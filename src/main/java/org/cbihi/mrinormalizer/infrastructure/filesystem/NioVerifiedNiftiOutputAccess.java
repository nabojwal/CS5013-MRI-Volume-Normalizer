package org.cbihi.mrinormalizer.infrastructure.filesystem;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.*;
import java.util.*;
import com.sun.nio.file.ExtendedOpenOption;
import org.cbihi.mrinormalizer.application.port.out.VerifiedNiftiOutputAccess;
import org.cbihi.mrinormalizer.application.port.out.ManifestStore;
import org.cbihi.mrinormalizer.application.provenance.manifest.*;
import org.cbihi.mrinormalizer.domain.model.*;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static org.cbihi.mrinormalizer.application.port.out.VerifiedNiftiOutputAccess.AccessFault.*;

/** Cooperative local-only private NIfTI stage with no-replace hard-link final publication. */
public final class NioVerifiedNiftiOutputAccess implements VerifiedNiftiOutputAccess {
    private final Path sourceRoot,outputRoot,privateRoot;
    private final ManifestStore store;
    private final long maxSourceFileBytes,maxTotalSourceBytes,maxStageBytes;
    private final boolean windows;
    private boolean validated,prepared,closed;
    private List<RelativePath> sourceBinding;
    private Owned session;
    private final Object identityLock=new Object();

    public NioVerifiedNiftiOutputAccess(Path sourceRoot,Path outputRoot,Path privateStageRoot,
            ManifestStore borrowedV2Store,long maxSourceFileBytes,long maxTotalSourceBytes,long maxStageBytes) {
        if(sourceRoot==null||outputRoot==null||privateStageRoot==null||borrowedV2Store==null
                ||maxSourceFileBytes<=0||maxTotalSourceBytes<=0||maxStageBytes<=0)
            throw new IllegalArgumentException("Invalid output-access configuration");
        this.store=borrowedV2Store;
        this.maxSourceFileBytes=maxSourceFileBytes;
        this.maxTotalSourceBytes=maxTotalSourceBytes;
        this.maxStageBytes=maxStageBytes;
        try {
            this.sourceRoot=sourceRoot.toRealPath();this.outputRoot=outputRoot.toRealPath();
            this.privateRoot=privateStageRoot.toAbsolutePath().normalize();
            if(!Files.isDirectory(this.sourceRoot,NOFOLLOW_LINKS)||!Files.isDirectory(this.outputRoot,NOFOLLOW_LINKS)
                    ||this.outputRoot.startsWith(this.sourceRoot)||this.sourceRoot.startsWith(this.outputRoot)
                    ||!this.privateRoot.startsWith(this.outputRoot)||this.privateRoot.equals(this.outputRoot))
                throw fault(SOURCE_CONTAINMENT_UNPROVEN);
            var fs=Files.getFileStore(this.outputRoot);
            String provider=this.outputRoot.getFileSystem().provider().getClass().getName();
            String kind=fs.type().toLowerCase(Locale.ROOT);
            this.windows=provider.equals("sun.nio.fs.WindowsFileSystemProvider") && kind.equals("ntfs");
            boolean linux=provider.equals("sun.nio.fs.LinuxFileSystemProvider")
                    && Set.of("ext2","ext3","ext4","xfs","btrfs","overlay").contains(kind);
            if(!windows&&!linux || !Files.getFileStore(this.privateRoot.getParent().toRealPath()).equals(fs))
                throw fault(PUBLICATION_UNAVAILABLE);
            if(windows && Files.getFileAttributeView(this.outputRoot,AclFileAttributeView.class,NOFOLLOW_LINKS)==null
                    ||linux && Files.getFileAttributeView(this.outputRoot,PosixFileAttributeView.class,NOFOLLOW_LINKS)==null)
                throw fault(PUBLICATION_UNAVAILABLE);
            // F7 retains the output-root directory's observed identity and mtime from construction.
            // A lazily created stage root could invalidate that previously constructed reader.
            // Require provisioned private root before construction of the F7 output-root reader.
            if(!Files.isDirectory(this.privateRoot,NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(this.privateRoot)
                    || !this.privateRoot.toRealPath().equals(this.privateRoot))
                throw fault(PUBLICATION_UNAVAILABLE);
            makePrivate(this.privateRoot);
        }catch (IOException|SecurityException ex){throw fault(SOURCE_CONTAINMENT_UNPROVEN);}
    }
    private static AccessFailure fault(AccessFault... code){return new AccessFailure(List.of(code));}
    private static BasicFileAttributes attributes(Path p) throws IOException {
        return Files.readAttributes(p,BasicFileAttributes.class,NOFOLLOW_LINKS);
    }
    private void directoryChain(Path root,Path file) throws IOException {
        if(!file.startsWith(root)||file.equals(root))throw fault(SOURCE_CONTAINMENT_UNPROVEN);
        Path p=root;
        if(!attributes(p).isDirectory()||attributes(p).isSymbolicLink())throw fault(SOURCE_CONTAINMENT_UNPROVEN);
        var rel=root.relativize(file);
        for(int i=0;i<rel.getNameCount()-1;i++) {
            p=p.resolve(rel.getName(i));
            var a=attributes(p);
            if(!a.isDirectory()||a.isSymbolicLink()||!p.toRealPath().equals(p))throw fault(SOURCE_CONTAINMENT_UNPROVEN);
        }
    }
    private boolean stable(BasicFileAttributes a,BasicFileAttributes b) {
        return a.isRegularFile()&&b.isRegularFile()&&!a.isSymbolicLink()&&!b.isSymbolicLink()
                && a.size()==b.size()&&a.lastModifiedTime().equals(b.lastModifiedTime())
                && a.creationTime().equals(b.creationTime())
                && (a.fileKey()==null||b.fileKey()==null||a.fileKey().equals(b.fileKey()));
    }
    private ContentDigest hash(Path p,long limit,FileChannel channel) throws IOException {
        MessageDigest h;
        try{h=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw fault(STAGE_HASH_FAILED);}
        var buf=ByteBuffer.allocate(8192);long total=0;channel.position(0);
        while(true){
            if(Thread.currentThread().isInterrupted())throw fault(INTERRUPTED);
            buf.clear();int n=channel.read(buf);
            if(n<0)break;
            if(n==0)throw new IOException("no progress");
            if(n>limit-total)throw fault(RESOURCE_LIMIT);
            total+=n;buf.flip();h.update(buf);
        }
        return new ContentDigest(total,HexFormat.of().formatHex(h.digest()));
    }
    private FileChannel pin(Path p) throws IOException {
        var opts=new HashSet<OpenOption>(Set.of(StandardOpenOption.READ,NOFOLLOW_LINKS));
        if(windows) opts.add(ExtendedOpenOption.NOSHARE_DELETE);
        return FileChannel.open(p,opts);
    }
    @Override public SourceBinding validateSelectedSources(List<InputSource> inputs,List<SourceFileRecord> planned) {
        synchronized(identityLock){
            if(closed||validated||prepared)throw fault(PUBLICATION_UNAVAILABLE);
            validated=true;
            if(inputs==null||planned==null||inputs.size()!=planned.size()||inputs.isEmpty())throw fault(SOURCE_PATH_INVALID);
            var byPath=new HashMap<RelativePath,SourceFileRecord>();
            for(var x:planned) {
                if(x==null||x.source().root()!=RelativePath.Root.SOURCE||x.digest().isEmpty()||!x.failures().isEmpty()
                        ||byPath.putIfAbsent(x.source(),x)!=null)throw fault(SOURCE_PATH_INVALID);
            }
            var matched=new ArrayList<RelativePath>();long total=0;
            for(var supplied:inputs){
                if(supplied==null||supplied.reference()==null)throw fault(SOURCE_PATH_INVALID);
                Path path;
                try {path=Path.of(supplied.reference()).toAbsolutePath().normalize();
                    directoryChain(sourceRoot,path);
                }catch(InvalidPathException e){throw fault(SOURCE_PATH_INVALID);}
                catch(IOException|SecurityException e){throw fault(SOURCE_CONTAINMENT_UNPROVEN);}
                RelativePath logical;
                try{logical=new RelativePath(RelativePath.Root.SOURCE,sourceRoot.relativize(path).toString().replace('\\','/'));}
                catch(IllegalArgumentException e){throw fault(SOURCE_PATH_INVALID);}
                var expected=byPath.get(logical);
                if(expected==null||matched.contains(logical))throw fault(SOURCE_PATH_INVALID);
                try {
                    var before=attributes(path);
                    if(!before.isRegularFile()||before.isSymbolicLink())throw fault(SOURCE_CONTAINMENT_UNPROVEN);
                    if(before.size()>maxSourceFileBytes || before.size()>maxTotalSourceBytes-total)throw fault(RESOURCE_LIMIT);
                    ContentDigest actual;
                    try(var pinned=pin(path)){actual=hash(path,maxSourceFileBytes,pinned);}
                    var after=attributes(path);
                    if(!stable(before,after))throw fault(SOURCE_CHANGED);
                    if(!actual.equals(expected.digest().orElseThrow()))throw fault(SOURCE_DIGEST_MISMATCH);
                    total+=actual.sizeBytes(); matched.add(logical);
                }catch(IOException|SecurityException ex){throw fault(SOURCE_CHANGED);}
            }
            if(!new HashSet<>(matched).equals(byPath.keySet()))throw fault(SOURCE_PATH_INVALID);
            matched.sort(Comparator.comparing(RelativePath::path));sourceBinding=List.copyOf(matched);
            return new SourceBinding(sourceBinding,matched.size());
        }
    }
    @Override public PreparedOutput prepare(String operationId,RelativePath destination,ManifestReceipt intent){
        synchronized(identityLock){
            if(closed||!validated||prepared||sourceBinding==null)throw fault(PUBLICATION_UNAVAILABLE);
            prepared=true;
            if(operationId==null||!operationId.matches("[0-9a-f]{64}")||destination==null
                    ||destination.root()!=RelativePath.Root.OUTPUT||intent==null)throw fault(PUBLICATION_UNAVAILABLE);
            var replayed=store.replay(Optional.of(intent));
            if(replayed.isEmpty()||!intent.equals(replayed.orElseThrow().receipt())
                    ||replayed.orElseThrow().plan().schemaVersion()!=2
                    ||replayed.orElseThrow().state()!=ManifestState.JobState.RUNNING)throw fault(PUBLICATION_UNAVAILABLE);
            var state=replayed.orElseThrow();
            var op=state.plan().operations().stream().filter(o->o.operationId().equals(operationId)).findFirst().orElseThrow(()->fault(PUBLICATION_UNAVAILABLE));
            var observed=state.operations().stream().filter(o->o.operationId().equals(operationId)).findFirst().orElseThrow(()->fault(PUBLICATION_UNAVAILABLE)).observation();
            if(op.kind()!=ManifestOperation.Kind.CONVERT_DICOM_TO_NIFTI||!op.destination().equals(destination)
                    ||observed.state()!=CheckpointRecord.State.IN_PROGRESS
                    ||observed.matchedExpectedSourceCount()!=sourceBinding.size()||!op.sources().equals(sourceBinding))
                throw fault(PUBLICATION_UNAVAILABLE);
            Path fin=outputRoot.resolve(destination.path()).normalize();
            String suffix=destination.path().toLowerCase(Locale.ROOT);
            if(!fin.startsWith(outputRoot)||fin.startsWith(privateRoot)||!(suffix.endsWith(".nii")||suffix.endsWith(".nii.gz")))
                throw fault(SOURCE_CONTAINMENT_UNPROVEN);
            try{
                directoryChain(outputRoot,fin);
                if(Files.exists(fin,NOFOLLOW_LINKS))throw fault(OUTPUT_CONFLICT);
                if(!Files.isDirectory(privateRoot,NOFOLLOW_LINKS)
                        || Files.isSymbolicLink(privateRoot))throw fault(PUBLICATION_UNAVAILABLE);
                var stageParent=privateRoot.resolve(UUID.randomUUID().toString());
                Files.createDirectory(stageParent);
                makePrivate(stageParent);
                String ext=suffix.endsWith(".nii.gz")?".nii.gz":".nii";
                Path stage=stageParent.resolve(".stage-"+UUID.randomUUID()+ext);
                session=new Owned(UUID.randomUUID(),operationId,destination,fin,stage,stageParent);
                return session;
            }catch(IOException|SecurityException ex){throw fault(STAGE_CREATE_FAILED);}
        }
    }
    private void makePrivate(Path p) throws IOException {
        if(windows){
            var view=Files.getFileAttributeView(p,AclFileAttributeView.class,NOFOLLOW_LINKS);
            if(view==null)throw fault(PUBLICATION_UNAVAILABLE);
            var owner=Files.getOwner(p,NOFOLLOW_LINKS);
            var allow=AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(owner)
                    .setPermissions(AclEntryPermission.values()).build();
            view.setAcl(List.of(allow));
            if(view.getAcl().stream().anyMatch(x->x.type()==AclEntryType.ALLOW&&!x.principal().equals(owner)))
                throw fault(PUBLICATION_UNAVAILABLE);
        }else{
            Files.setPosixFilePermissions(p,PosixFilePermissions.fromString("rwx------"));
            if(!Files.getPosixFilePermissions(p,NOFOLLOW_LINKS).equals(PosixFilePermissions.fromString("rwx------")))
                throw fault(PUBLICATION_UNAVAILABLE);
        }
    }
    @Override public void close(){
        synchronized(identityLock){
            if(closed) return;
            closed=true;
            if(session!=null)session.close();
        }
    }
    private final class Owned implements PreparedOutput {
        private final UUID id;private final String operationId;private final RelativePath destination;
        private final Path fin,stage,parent;
        private FileChannel pinned;
        private BasicFileAttributes initial;
        private ContentDigest d1,d2,d3,d4;
        private int digests;
        private boolean poisoned,linkAttempted,published,unknown,discarded,closed,stageRemoved;
        private AccessFailure sticky;
        Owned(UUID id,String operationId,RelativePath destination,Path fin,Path stage,Path parent){
            this.id=id;this.operationId=operationId;this.destination=destination;
            this.fin=fin;this.stage=stage;this.parent=parent;
        }
        @Override public UUID sessionId(){return id;}
        @Override public OutputTarget stageTarget(){return new OutputTarget(stage.toString());}
        @Override public RelativePath stageReadReference(){
            return new RelativePath(RelativePath.Root.SOURCE,outputRoot.relativize(stage).toString().replace('\\','/'));
        }
        private void requireActive(){
            if(poisoned||closed||discarded||linkAttempted){poisoned=true;throw fault(PUBLICATION_UNAVAILABLE);}
        }
        private ContentDigest observe() throws IOException {
            var before=attributes(stage);
            if(!before.isRegularFile()||before.isSymbolicLink())throw fault(STAGE_CHANGED);
            if(initial!=null&&!stable(initial,before))throw fault(STAGE_CHANGED);
            if(pinned==null) {pinned=pin(stage);initial=before;}
            var digest=hash(stage,maxStageBytes,pinned);
            if(!stable(before,attributes(stage))||!stable(initial,attributes(stage)))throw fault(STAGE_CHANGED);
            return digest;
        }
        @Override public ContentDigest digestClosedStage(){
            synchronized(identityLock){
                requireActive();
                if(digests>=2){poisoned=true;throw fault(PUBLICATION_UNAVAILABLE);}
                try {
                    if(digests==0 && !Files.exists(stage,NOFOLLOW_LINKS))
                        throw fault(STAGE_CLOSE_FAILED);
                    var digest=observe();
                    if(digests==0)d1=digest;
                    else {d2=digest;if(!d1.equals(d2)){poisoned=true;throw fault(STAGE_CHANGED);}}
                    digests++;return digest;
                }catch(AccessFailure e){poisoned=true;throw e;}
                catch(IOException|SecurityException e){poisoned=true;throw fault(STAGE_HASH_FAILED);}
            }
        }
        @Override public PublicationResult publishVerified(ManifestReceipt ack){
            synchronized(identityLock){
                requireActive();
                if(digests!=2||d1==null||!d1.equals(d2)||pinned==null||ack==null){poisoned=true;throw fault(PUBLICATION_UNAVAILABLE);}
                var replayed=store.replay(Optional.of(ack));
                if(replayed.isEmpty()||!replayed.orElseThrow().receipt().equals(ack)
                        ||replayed.orElseThrow().plan().schemaVersion()!=2)throw fault(PUBLICATION_UNAVAILABLE);
                var state=replayed.orElseThrow();
                var observed=state.operations().stream().filter(o->o.operationId().equals(operationId)).findFirst()
                        .orElseThrow(()->fault(PUBLICATION_UNAVAILABLE)).observation();
                var proof=observed.postWriteVerification().orElseThrow(()->fault(PUBLICATION_UNAVAILABLE));
                if(observed.state()!=CheckpointRecord.State.STAGED_VERIFIED||!proof.sessionId().equals(id)
                        ||!proof.stagedDigest().equals(d1)||!"F8-NIFTI1-F32-1".equals(proof.policyId())
                        ||!observed.postWriteFaults().isEmpty())throw fault(PUBLICATION_UNAVAILABLE);
                try {
                    d3=observe();
                    if(!d3.equals(d1)) {poisoned=true;throw fault(STAGE_CHANGED);}
                    if(Files.exists(fin,NOFOLLOW_LINKS)){
                        poisoned=true;return new PublicationResult(Publication.NOT_PUBLISHED,Optional.empty(),List.of(OUTPUT_CONFLICT));
                    }
                    linkAttempted=true;
                    try{Files.createLink(fin,stage);}
                    catch(FileAlreadyExistsException e){
                        linkAttempted=false;poisoned=true;
                        return new PublicationResult(Publication.NOT_PUBLISHED,Optional.empty(),List.of(OUTPUT_CONFLICT));
                    }
                    if(!Files.isSameFile(fin,stage) || !attributes(fin).isRegularFile()
                            || !stable(initial,attributes(stage)))throw fault(PUBLICATION_UNKNOWN);
                    try(var finalPin=pin(fin)){d4=hash(fin,maxStageBytes,finalPin);}
                    if(!d4.equals(d1)) throw fault(FINAL_DIGEST_MISMATCH);
                    published=true;
                    return new PublicationResult(Publication.PUBLISHED_BYTES_CHECKED,Optional.of(d4),List.of());
                }catch(AccessFailure e){
                    poisoned=true;
                    if(linkAttempted){unknown=true;return new PublicationResult(Publication.UNKNOWN,Optional.empty(),e.faults());}
                    throw e;
                }catch(IOException|SecurityException e){
                    poisoned=true;
                    if(linkAttempted){unknown=true;return new PublicationResult(Publication.UNKNOWN,Optional.empty(),List.of(PUBLICATION_UNKNOWN));}
                    throw fault(PUBLICATION_UNAVAILABLE);
                }
            }
        }
        @Override public void discardUnpublished(){
            synchronized(identityLock){
                if(discarded)return;
                if(published||unknown||linkAttempted)throw fault(PUBLICATION_UNAVAILABLE);
                try{
                    if(pinned!=null){pinned.close();pinned=null;}
                    if(Files.exists(stage,NOFOLLOW_LINKS)){
                        if(initial!=null&&!stable(initial,attributes(stage)))throw fault(STAGE_CHANGED);
                        Files.delete(stage);
                    }
                    Files.deleteIfExists(parent);
                    discarded=true;stageRemoved=true;
                }catch(IOException|SecurityException ex){poisoned=true;throw fault(CLEANUP_FAILED);}
            }
        }
        @Override public void close(){
            synchronized(identityLock){
                if(closed){if(sticky!=null)throw sticky;return;}
                closed=true;var errors=EnumSet.noneOf(AccessFault.class);
                boolean finalAck=false;
                if(published){
                    try{
                        var current=store.replay(Optional.empty());
                        if(current.isPresent()) {
                            var obs=current.orElseThrow().operations().stream().filter(o->o.operationId().equals(operationId)).findFirst().orElseThrow().observation();
                            finalAck=obs.state()==CheckpointRecord.State.VERIFIED_PUBLISHED
                                    && obs.postWriteVerification().isPresent()
                                    && obs.postWriteVerification().orElseThrow().sessionId().equals(id);
                        }
                    }catch(RuntimeException ex){errors.add(RELEASE_FAILED);}
                }
                if(pinned!=null)try{pinned.close();pinned=null;}catch(IOException ex){errors.add(RELEASE_FAILED);}
                if(!unknown && (discarded||!linkAttempted||published&&finalAck)){
                    try{
                        if(Files.exists(stage,NOFOLLOW_LINKS)){
                            if(initial!=null&&!stable(initial,attributes(stage)))throw fault(STAGE_CHANGED);
                            Files.delete(stage);
                        }
                        Files.deleteIfExists(parent);
                        stageRemoved=true;
                    }catch(IOException|SecurityException|AccessFailure ex){errors.add(CLEANUP_FAILED);}
                }
                if(!errors.isEmpty()){sticky=new AccessFailure(List.copyOf(errors));throw sticky;}
            }
        }
    }
}
