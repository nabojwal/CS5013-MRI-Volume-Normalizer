package org.cbihi.mrinormalizer.infrastructure.filesystem;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.UserPrincipal;
import com.sun.nio.file.ExtendedOpenOption;
import java.util.Map;
import java.util.HashMap;
import java.util.Comparator;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.cbihi.mrinormalizer.application.dataset.nifti.NiftiReadResult;
import org.cbihi.mrinormalizer.application.m8.*;
import org.cbihi.mrinormalizer.application.port.out.M8Workspace;
import org.cbihi.mrinormalizer.application.provenance.manifest.ContentDigest;
import org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath;
import org.cbihi.mrinormalizer.infrastructure.nifti.Nifti1VolumeReader;
import org.cbihi.mrinormalizer.infrastructure.provenance.M8JournalCodec;

/** One-shot local-filesystem M8 session: ownership-bound staged series, journal, no-replace links. */
public final class NioM8Workspace implements M8Workspace {
    private final Path sourceRoot, outputRoot;
    private final M8Limits limits;
    private final Clock clock;
    private boolean used;
    private final Faults faults;

    public NioM8Workspace(Path sourceRoot,Path outputRoot,M8Limits limits,Clock clock){
        this(sourceRoot,outputRoot,limits,clock,(phase,sourceZ)->{});
    }
    /** Package-private fault injection: only actual activity boundaries are instrumented. */
    interface Faults { void at(String phase,int sourceZ) throws IOException; }
    NioM8Workspace(Path sourceRoot,Path outputRoot,M8Limits limits,Clock clock,Faults faults){
        this.sourceRoot=Objects.requireNonNull(sourceRoot);this.outputRoot=Objects.requireNonNull(outputRoot);
        this.limits=Objects.requireNonNull(limits);this.clock=Objects.requireNonNull(clock);
        this.faults=Objects.requireNonNull(faults);
    }
    private void inject(String phase,int sourceZ) throws IOException {faults.at(phase,sourceZ);}
    @Override public synchronized Session open(M8Request request){
        if(used)throw M8Fault.fail(M8Fault.Phase.REQUEST,M8Fault.Code.INVALID_REQUEST);
        used=true;
        if(request==null)throw M8Fault.fail(M8Fault.Phase.REQUEST,M8Fault.Code.INVALID_REQUEST);
        Path srcRoot=canonicalRoot(sourceRoot),outRoot=canonicalRoot(outputRoot);
        requireLocal(outRoot);
        requireLocal(srcRoot);
        Path src=resolveSource(srcRoot,request.source());
        return new S(request,srcRoot,outRoot,src);
    }
    private static Path canonicalRoot(Path p){
        try{
            Path r=p.toRealPath();
            if(!Files.isDirectory(r,LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(r))
                throw M8Fault.fail(M8Fault.Phase.REQUEST,M8Fault.Code.CONTAINMENT_UNPROVEN);
            return r;
        }catch(IOException|SecurityException e){throw M8Fault.fail(M8Fault.Phase.REQUEST,M8Fault.Code.CONTAINMENT_UNPROVEN);}
    }
    private static void requireLocal(Path out){
        if(!out.getFileSystem().equals(FileSystems.getDefault()))throw M8Fault.fail(M8Fault.Phase.REQUEST,M8Fault.Code.PROVIDER_UNSUPPORTED);
        try{
            String path=out.toString(), store=Files.getFileStore(out).type().toLowerCase(java.util.Locale.ROOT);
            boolean windows=System.getProperty("os.name","").toLowerCase(java.util.Locale.ROOT).contains("win");
            if((windows&&(!store.contains("ntfs")||path.startsWith("\\\\")))
                    ||(!windows&&!store.equals("ext4")))
                throw M8Fault.fail(M8Fault.Phase.REQUEST,M8Fault.Code.PROVIDER_UNSUPPORTED);
        }catch(IOException e){throw M8Fault.fail(M8Fault.Phase.REQUEST,M8Fault.Code.PROVIDER_UNSUPPORTED);}
    }
    private static Path resolveSource(Path root,RelativePath logical){
        if(logical.root()!=RelativePath.Root.SOURCE)throw M8Fault.fail(M8Fault.Phase.REQUEST,M8Fault.Code.INVALID_REQUEST);
        Path p=root;
        for(String segment:logical.path().split("/")){
            p=p.resolve(segment);
            try{var a=Files.readAttributes(p,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                if(a.isSymbolicLink()||(!a.isDirectory()&&!a.isRegularFile()))throw M8Fault.fail(M8Fault.Phase.SOURCE,M8Fault.Code.CONTAINMENT_UNPROVEN);
            }catch(IOException e){throw M8Fault.fail(M8Fault.Phase.SOURCE,M8Fault.Code.READ_FAILED);}
        }
        if(!p.startsWith(root)||!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))
            throw M8Fault.fail(M8Fault.Phase.SOURCE,M8Fault.Code.CONTAINMENT_UNPROVEN);
        return p;
    }
    /** Verify an observed entry is the same no-follow object, never delete by name alone. */
    private static BasicFileAttributes observe(Path p) throws IOException {
        return Files.readAttributes(p,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
    }
    private static boolean sameEntry(BasicFileAttributes first,BasicFileAttributes now){
        return first.fileKey()!=null && first.fileKey().equals(now.fileKey())
                && first.isDirectory()==now.isDirectory() && first.isRegularFile()==now.isRegularFile()
                && !now.isSymbolicLink();
    }
    private static void ensurePrivateDirectory(Path p) throws IOException {
        if(System.getProperty("os.name","").toLowerCase(java.util.Locale.ROOT).contains("win")){
            AclFileAttributeView view=Files.getFileAttributeView(p,AclFileAttributeView.class,LinkOption.NOFOLLOW_LINKS);
            if(view==null)throw new IOException("ACL observation unsupported");
            UserPrincipal owner=Files.getOwner(p,LinkOption.NOFOLLOW_LINKS);
            String ownerName=owner.getName();
            for(var entry:view.getAcl()){
                if(entry.type()!=java.nio.file.attribute.AclEntryType.ALLOW)continue;
                String principal=entry.principal().getName();
                if(principal.equalsIgnoreCase(ownerName)
                    ||principal.equalsIgnoreCase("BUILTIN\\Administrators")
                    ||principal.equalsIgnoreCase("NT AUTHORITY\\SYSTEM"))continue;
                for(var permission:entry.permissions()){
                    if(permission==AclEntryPermission.WRITE_DATA || permission==AclEntryPermission.APPEND_DATA
                        ||permission==AclEntryPermission.DELETE || permission==AclEntryPermission.DELETE_CHILD
                        ||permission==AclEntryPermission.WRITE_ACL || permission==AclEntryPermission.WRITE_OWNER)
                        throw new IOException("Private directory ACL too broad");
                }
            }
        }else{
            PosixFileAttributes a=Files.readAttributes(p,PosixFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
            if(a.permissions().contains(java.nio.file.attribute.PosixFilePermission.GROUP_WRITE)
                ||a.permissions().contains(java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE)
                ||a.permissions().contains(java.nio.file.attribute.PosixFilePermission.GROUP_READ)
                ||a.permissions().contains(java.nio.file.attribute.PosixFilePermission.OTHERS_READ)
                ||a.permissions().contains(java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE)
                ||a.permissions().contains(java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE))
                throw new IOException("Private directory is not owner-only");
        }
    }
    private static FileChannel lockedRead(Path p) throws IOException{
        if(System.getProperty("os.name","").toLowerCase(java.util.Locale.ROOT).contains("win")){
            return FileChannel.open(p,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS,
                    ExtendedOpenOption.NOSHARE_WRITE,ExtendedOpenOption.NOSHARE_DELETE);
        }
        return FileChannel.open(p,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS);
    }
    /** Carries the actual failed close activity, including when try-with-resources
     * suppresses a close exception onto an earlier read/write failure. */
    private static final class SnapshotCloseFailure extends IOException {
        final M8Fault.Phase phase;
        final M8Fault.Code code;
        SnapshotCloseFailure(M8Fault.Phase phase,M8Fault.Code code,IOException cause){
            super("Snapshot resource release failed",cause);
            this.phase=phase;this.code=code;
        }
    }
    /** Owns one real channel. The hook is exercised AFTER channel.close(), not
     * instead of it, so a primary I/O fault can genuinely suppress close faults. */
    private final class SnapshotClose implements AutoCloseable {
        private final FileChannel channel;
        private final String hook;
        private final M8Fault.Phase phase;
        private final M8Fault.Code code;
        private final List<M8Fault> closeFacts;
        SnapshotClose(FileChannel channel,String hook,M8Fault.Phase phase,M8Fault.Code code,
                List<M8Fault> closeFacts){
            this.channel=channel;this.hook=hook;this.phase=phase;this.code=code;
            this.closeFacts=closeFacts;
        }
        @Override public void close() throws IOException {
            IOException failure=null;
            try {channel.close();} catch(IOException e){failure=e;}
            try {inject(hook,-1);} catch(IOException e){
                if(failure==null)failure=e;else failure.addSuppressed(e);
            }
            if(failure!=null){
                // Record independently: M8Fault.Failure disables Java's Throwable
                // suppressed-exception storage for privacy/safe public errors.
                closeFacts.add(new M8Fault(phase,code));
                throw new SnapshotCloseFailure(phase,code,failure);
            }
        }
    }
    private static boolean suppressedCloseFailure(Throwable failure){
        for(Throwable suppressed:failure.getSuppressed()){
            if(suppressed instanceof SnapshotCloseFailure || suppressedCloseFailure(suppressed))
                return true;
        }
        return false;
    }
    private static void appendSuppressedCloseFailures(Throwable failure,List<M8Fault> facts){
        for(Throwable suppressed:failure.getSuppressed()){
            if(suppressed instanceof SnapshotCloseFailure close)
                facts.add(new M8Fault(close.phase,close.code));
            else if(suppressed instanceof IOException)
                facts.add(new M8Fault(M8Fault.Phase.RELEASE,M8Fault.Code.RELEASE_FAILED));
            appendSuppressedCloseFailures(suppressed,facts);
        }
    }
    private static String hex(byte[] b){return java.util.HexFormat.of().formatHex(b);}
    private static MessageDigest sha(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
    private static ContentDigest hash(Path p,long max){
        try(InputStream input=Files.newInputStream(p,LinkOption.NOFOLLOW_LINKS)){
            MessageDigest digest=sha();byte[] buf=new byte[8192];long n=0;int r;
            while((r=input.read(buf))!=-1){n=Math.addExact(n,r);if(n>max)throw M8Fault.fail(M8Fault.Phase.SOURCE,M8Fault.Code.RESOURCE_LIMIT);digest.update(buf,0,r);}
            return new ContentDigest(n,hex(digest.digest()));
        }catch(IOException|ArithmeticException e){throw M8Fault.fail(M8Fault.Phase.SOURCE,M8Fault.Code.READ_FAILED);}
    }
    private static String digest(byte[] b){return hex(sha().digest(b));}
    private static boolean same(ContentDigest a,ContentDigest b){return a.equals(b);}
    private static void ensureRegular(Path p){
        if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(p))
            throw M8Fault.fail(M8Fault.Phase.VERIFY,M8Fault.Code.STAGE_CHANGED);
    }
    private final class S implements Session {
        private final M8Request request;
        private final Path srcRoot,outRoot,source,privateRoot,snapshot,finalRoot;
        private final M8JournalCodec codec=new M8JournalCodec(limits);
        private final List<M8Workspace.Checkpoint> checkpoints=new ArrayList<>();
        private final List<Receipt> receipts=new ArrayList<>();
        private final List<ContentDigest> stages=new ArrayList<>();
        private final Set<Path> owned=new HashSet<>();
        private final Map<Path,BasicFileAttributes> identities=new HashMap<>();
        private final Map<Path,FileChannel> openedStages=new HashMap<>();
        private final BasicFileAttributes sourceRootIdentity, outputRootIdentity, sourceIdentity;
        private final java.nio.file.FileStore outputStore;
        private final ContentDigest sourceDigest;
        private M8Plan plan;
        private Event event;
        private M8Result.Publication publication=M8Result.Publication.NONE;
        private NiftiReadResult read;
        private boolean closed,finalCreated,markerVerified;
        private byte[] expectedMarkerBytes;
        private long totalStageBytes;
        private int publicLinks;
        S(M8Request request,Path sourceRoot,Path outputRoot,Path source){
            this.request=request;this.srcRoot=sourceRoot;this.outRoot=outputRoot;this.source=source;
            privateRoot=outRoot.resolve(".pai-m8-"+request.jobId());
            finalRoot=outRoot.resolve("m8-"+request.jobId());snapshot=privateRoot.resolve("source.nii");
            try{
                sourceRootIdentity=observe(srcRoot);outputRootIdentity=observe(outRoot);
                sourceIdentity=observe(source);outputStore=Files.getFileStore(outRoot);
                if(sourceIdentity.fileKey()==null || sourceRootIdentity.fileKey()==null
                    ||outputRootIdentity.fileKey()==null)
                    throw M8Fault.fail(M8Fault.Phase.REQUEST,M8Fault.Code.PROVIDER_UNSUPPORTED);
                if(System.getProperty("os.name","").toLowerCase(java.util.Locale.ROOT).contains("win")
                        && (privateRoot.toString().length()>240 || finalRoot.toString().length()>240
                        || source.toString().length()>240))throw new IOException("Path budget");
            }catch(IOException e){throw M8Fault.fail(M8Fault.Phase.REQUEST,M8Fault.Code.CONTAINMENT_UNPROVEN);}
            if(Files.exists(privateRoot,LinkOption.NOFOLLOW_LINKS)||Files.exists(finalRoot,LinkOption.NOFOLLOW_LINKS))
                throw M8Fault.fail(M8Fault.Phase.REQUEST,M8Fault.Code.OUTPUT_CONFLICT);
            String activity="preflight";
            List<M8Fault> closeFacts=new ArrayList<>();
            try{
                long usable=Files.getFileStore(outRoot).getUsableSpace();
                long inputSize=Files.size(source);
                long needed;
                try {needed=Math.addExact(inputSize,Math.addExact(limits.maxOutputBytes(),128L*(1<<20)));}
                catch(ArithmeticException e){throw M8Fault.fail(M8Fault.Phase.SOURCE,M8Fault.Code.RESOURCE_LIMIT);}
                if(inputSize>limits.nifti().maxEncodedBytes() || usable<needed)
                    throw M8Fault.fail(M8Fault.Phase.SOURCE,M8Fault.Code.RESOURCE_LIMIT);
                boolean win=System.getProperty("os.name","").toLowerCase(java.util.Locale.ROOT).contains("win");
                activity="create-private";
                inject("create-private",-1);
                if(win)Files.createDirectory(privateRoot);
                else Files.createDirectory(privateRoot,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
                own(privateRoot);
                ensurePrivateDirectory(privateRoot);
                revalidateRoots();
                activity="snapshot-open";
                inject("snapshot-open",-1);
                try(FileChannel in=lockedRead(source);
                    SnapshotClose sourceClose=new SnapshotClose(in,"snapshot-source-close",
                        M8Fault.Phase.RELEASE,M8Fault.Code.RELEASE_FAILED,closeFacts)){
                    // The original and output streams are separate I/O activities. In
                    // particular, an output OPEN/WRITE must never be reported as a
                    // failed source READ, and a source RE-READ must not be a WRITE.
                    activity="snapshot-destination-open";
                    inject("snapshot-destination-open",-1);
                    try(FileChannel dest=FileChannel.open(snapshot,StandardOpenOption.WRITE,StandardOpenOption.CREATE_NEW);
                        SnapshotClose destinationClose=new SnapshotClose(dest,"snapshot-destination-close",
                            M8Fault.Phase.WRITE,M8Fault.Code.WRITE_FAILED,closeFacts)){
                        own(snapshot);
                        MessageDigest digest=sha();ByteBuffer buffer=ByteBuffer.allocate(8192);long copied=0;
                        while(true){
                            activity="snapshot-source-read";
                            inject("snapshot-source-read",-1);
                            int n=in.read(buffer);
                            if(n==-1)break;
                            if(n==0)continue;
                            buffer.flip();
                            copied=Math.addExact(copied,buffer.remaining());
                            if(copied>limits.nifti().maxEncodedBytes())
                                throw M8Fault.fail(M8Fault.Phase.SOURCE,M8Fault.Code.RESOURCE_LIMIT);
                            byte[] tmp=new byte[buffer.remaining()];buffer.get(tmp);digest.update(tmp);
                            ByteBuffer bytes=ByteBuffer.wrap(tmp);
                            while(bytes.hasRemaining()){
                                activity="snapshot-destination-write";
                                inject("snapshot-write",-1);
                                dest.write(bytes);
                            }
                            buffer.clear();
                        }
                        activity="snapshot-force";
                        inject("snapshot-force",-1);
                        dest.force(true);
                        sourceDigest=new ContentDigest(copied,hex(digest.digest()));
                        activity="snapshot-source-reread";
                        inject("snapshot-source-reread",-1);
                        in.position(0);
                        MessageDigest original=sha();ByteBuffer b=ByteBuffer.allocate(8192);long count=0;
                        while(true){
                            activity="snapshot-source-reread";
                            inject("snapshot-source-reread",-1);
                            int n=in.read(b);
                            if(n==-1)break;
                            if(n==0)continue;
                            b.flip();count=Math.addExact(count,b.remaining());original.update(b);b.clear();
                        }
                        if(count!=copied||!hex(original.digest()).equals(sourceDigest.sha256()))
                            throw M8Fault.fail(M8Fault.Phase.SOURCE,M8Fault.Code.SOURCE_CHANGED);
                        activity="snapshot-destination-close";
                        // The resource guard now performs the actual close and
                        // records its failure independently of earlier I/O faults.
                    }
                    activity="snapshot-source-close";
                    // Closing the retained source handle is a RELEASE activity;
                    // it is recorded even if an earlier copy operation failed.
                }
                activity="snapshot-verification";
                revalidateRoots();
                if(!same(sourceDigest,hash(snapshot,limits.nifti().maxEncodedBytes())))
                    throw M8Fault.fail(M8Fault.Phase.SOURCE,M8Fault.Code.SOURCE_CHANGED);
            }catch(IOException|RuntimeException e){
                boolean unexpected=!(e instanceof IOException)&&!(e instanceof M8Fault.Failure);
                M8Fault.Phase phase=activity.equals("preflight")
                    || (activity.startsWith("snapshot-source") && !activity.equals("snapshot-source-close")) || activity.equals("snapshot-open")
                    ? M8Fault.Phase.SOURCE
                    : activity.equals("snapshot-verification") ? M8Fault.Phase.VERIFY
                    : activity.equals("snapshot-source-close") ? M8Fault.Phase.RELEASE
                    : M8Fault.Phase.WRITE;
                M8Fault.Code code=e instanceof AccessDeniedException?M8Fault.Code.ACCESS_DENIED:
                    unexpected?M8Fault.Code.INTERNAL_ERROR:
                    (phase==M8Fault.Phase.WRITE?M8Fault.Code.WRITE_FAILED:
                    phase==M8Fault.Phase.RELEASE?M8Fault.Code.RELEASE_FAILED:M8Fault.Code.READ_FAILED);
                M8Fault primary=e instanceof M8Fault.Failure f?f.faults().getFirst():
                    e instanceof SnapshotCloseFailure close?new M8Fault(close.phase,close.code):
                    new M8Fault(phase,code);
                List<M8Fault> collected=new ArrayList<>();
                if(e instanceof M8Fault.Failure f)collected.addAll(f.faults());
                else collected.add(primary);
                // The captured facts are authoritative even for an exception
                // type with suppression disabled. Keep only one fact per pair.
                List<M8Fault> suppressedFacts=new ArrayList<>();
                appendSuppressedCloseFailures(e,suppressedFacts);
                for(M8Fault fact:closeFacts)if(!collected.contains(fact))collected.add(fact);
                for(M8Fault fact:suppressedFacts)if(!collected.contains(fact))collected.add(fact);
                boolean releaseUncertain=e instanceof SnapshotCloseFailure &&
                    ((SnapshotCloseFailure)e).phase==M8Fault.Phase.RELEASE;
                for(M8Fault secondary:collected.subList(1,collected.size())){
                    if(secondary.phase()==M8Fault.Phase.RELEASE)releaseUncertain=true;
                }
                // Either channel can fail during automatic close. Even a WRITE
                // close suppressed by a SOURCE fault makes release certainty false.
                boolean secondaryClose=!closeFacts.isEmpty() &&
                    (!(e instanceof SnapshotCloseFailure)||closeFacts.size()>1);
                boolean uncertainty=unexpected || phase==M8Fault.Phase.RELEASE
                    ||releaseUncertain||secondaryClose||suppressedCloseFailure(e)
                    || (e instanceof M8Fault.Failure f && f.effectsUncertain());
                try{
                    for(Path q:List.of(snapshot,privateRoot)){
                        if(Files.exists(q,LinkOption.NOFOLLOW_LINKS)){
                            if(!owned.contains(q) || !sameEntry(identities.get(q),observe(q)))
                                throw new IOException("Ownership unproven");
                            Files.delete(q);
                        }
                    }
                }catch(IOException|RuntimeException cleanup){
                    collected.add(new M8Fault(M8Fault.Phase.CLEANUP,M8Fault.Code.CLEANUP_FAILED));
                    throw new M8Fault.Failure(collected,true);
                }
                throw new M8Fault.Failure(collected,uncertainty);
            }
        }
        private void own(Path p)throws IOException{
            BasicFileAttributes a=observe(p);
            if(a.fileKey()==null||a.isSymbolicLink())throw new IOException("Identity unavailable");
            owned.add(p);identities.put(p,a);
        }
        private void revalidateRoots()throws IOException{
            if(!sameEntry(sourceRootIdentity,observe(srcRoot))
                    ||!sameEntry(outputRootIdentity,observe(outRoot))
                    ||!sameEntry(sourceIdentity,observe(source))
                    ||!Files.getFileStore(outRoot).equals(outputStore))
                throw new IOException("Root/source identity changed");
        }
        private void checkOwned(Path p)throws IOException{
            if(!owned.contains(p)||!sameEntry(identities.get(p),observe(p)))
                throw new IOException("Unproven replacement");
        }
        private void active(){if(closed)throw M8Fault.fail(M8Fault.Phase.REQUEST,M8Fault.Code.INVALID_REQUEST);}
        @Override public ContentDigest sourceDigest(){active();return sourceDigest;}
        @Override public NiftiReadResult readSnapshot(){
            active();if(read!=null)throw M8Fault.fail(M8Fault.Phase.SOURCE,M8Fault.Code.INVALID_REQUEST);
            try{
                Nifti1VolumeReader reader=new Nifti1VolumeReader(privateRoot,limits.nifti());
                read=reader.read(new RelativePath(RelativePath.Root.SOURCE,"source.nii"));
                return read;
            }catch(RuntimeException e){if(e instanceof M8Fault.Failure)throw e;
                throw M8Fault.fail(M8Fault.Phase.SOURCE,M8Fault.Code.READ_FAILED);}
        }
        @Override public void verifySnapshotUnchanged(){
            active();if(!same(sourceDigest,hash(snapshot,limits.nifti().maxEncodedBytes())))
                throw M8Fault.fail(M8Fault.Phase.SOURCE,M8Fault.Code.SOURCE_CHANGED);
        }
        @Override public OutputStream createStage(int z){
            active();if(event!=Event.INTENT||plan==null||z!=stages.size())throw M8Fault.fail(M8Fault.Phase.WRITE,M8Fault.Code.INVALID_REQUEST);
            Path file=stage(z);
            try{
                revalidateRoots();inject("stage-create",z);
                FileChannel channel=FileChannel.open(file,StandardOpenOption.WRITE,StandardOpenOption.CREATE_NEW);
                own(file);openedStages.put(file,channel);
                final long maximum=Math.min(limits.maxOutputBytes(),
                        Math.addExact(Math.multiplyExact(2L,(long)plan.width()*plan.height()),16384L));
                return new OutputStream(){boolean done;long written;
                    @Override public void write(int b)throws IOException{write(new byte[]{(byte)b},0,1);}
                    @Override public void write(byte[] b,int off,int len)throws IOException{
                        if(done)throw new IOException("Closed stream");
                        if(off<0||len<0||off>b.length-len)throw new IndexOutOfBoundsException();
                        if(len>maximum-written || len>limits.maxOutputBytes()-totalStageBytes-written)
                            throw new IOException("Stage size limit");
                        inject("stage-write",z);
                        ByteBuffer buf=ByteBuffer.wrap(b,off,len);while(buf.hasRemaining())channel.write(buf);
                        written+=len;
                    }
                    @Override public void flush()throws IOException{channel.force(false);}
                    @Override public void close()throws IOException{if(!done){done=true;try{
                        inject("stage-close",z);channel.force(true);
                    }finally{channel.close();openedStages.remove(file);}}}
                };
            }catch(IOException e){throw M8Fault.fail(M8Fault.Phase.WRITE,M8Fault.Code.WRITE_FAILED);}
        }
        @Override public InputStream reopenStage(int z){
            active();Path p=stage(z);ensureRegular(p);
            try{checkOwned(p);inject("stage-reopen",z);return Files.newInputStream(p,LinkOption.NOFOLLOW_LINKS);}
            catch(IOException e){throw M8Fault.fail(M8Fault.Phase.VERIFY,M8Fault.Code.READ_FAILED);}
        }
        @Override public ContentDigest digestClosedStage(int z){
            active();Path p=stage(z);ensureRegular(p);
            try{checkOwned(p);inject("stage-digest",z);}catch(IOException e){throw M8Fault.fail(M8Fault.Phase.VERIFY,M8Fault.Code.STAGE_CHANGED);}
            ContentDigest d=hash(p,limits.maxOutputBytes());
            if(z==stages.size()){
                totalStageBytes=Math.addExact(totalStageBytes,d.sizeBytes());
                if(totalStageBytes>limits.maxOutputBytes())throw M8Fault.fail(M8Fault.Phase.WRITE,M8Fault.Code.RESOURCE_LIMIT);
                stages.add(d);
            }else if(z>=stages.size()||!same(stages.get(z),d))throw M8Fault.fail(M8Fault.Phase.VERIFY,M8Fault.Code.STAGE_CHANGED);
            return d;
        }
        private Path stage(int z){return privateRoot.resolve(String.format(java.util.Locale.ROOT,"image-%06d.dcm",z+1));}
        private Path cp(long sequence){return privateRoot.resolve(String.format(java.util.Locale.ROOT,"checkpoint-%06d.json",sequence));}
        @Override public synchronized Receipt acknowledge(Event next,M8Plan intended,List<SliceEvidence> evidence,List<M8Fault> faults){
            active();Objects.requireNonNull(intended);Objects.requireNonNull(next);
            if(!request.jobId().equals(intended.jobId())||!legal(event,next)||checkpoints.size()>=8)
                throw M8Fault.fail(M8Fault.Phase.JOURNAL,M8Fault.Code.ACK_UNCERTAIN);
            if(plan==null)plan=intended;
            else if(!Arrays.equals(codec.encode(checkpointForComparison(plan)),codec.encode(checkpointForComparison(intended))))
                throw M8Fault.fail(M8Fault.Phase.JOURNAL,M8Fault.Code.ACK_UNCERTAIN);
            List<SliceEvidence> facts=List.copyOf(evidence);
            if(next==Event.STAGED_VERIFIED){
                if(facts.size()!=plan.depth()||stages.size()!=plan.depth())throw M8Fault.fail(M8Fault.Phase.JOURNAL,M8Fault.Code.ACK_UNCERTAIN);
                for(int i=0;i<plan.depth();i++){
                    SliceEvidence x=facts.get(i);if(x.sourceZ()!=i ||!x.filename().equals(plan.slices().get(i).filename())
                            ||!x.sopUid().equals(plan.slices().get(i).sopUid())||!x.digest().equals(stages.get(i))
                            ||x.check().comparedVoxels()!=(long)plan.width()*plan.height()||x.check().comparedCorners()!=4)
                        throw M8Fault.fail(M8Fault.Phase.JOURNAL,M8Fault.Code.ACK_UNCERTAIN);
                }
            }
            if(next==Event.PUBLISHING||next==Event.PUBLISHED||next==Event.COMPLETED){
                if(checkpoints.size()<2||!facts.equals(checkpoints.get(1).slices()))
                    throw M8Fault.fail(M8Fault.Phase.JOURNAL,M8Fault.Code.ACK_UNCERTAIN);
                if(next==Event.PUBLISHED && publication!=M8Result.Publication.COMPLETE_BYTES_VERIFIED)
                    throw M8Fault.fail(M8Fault.Phase.JOURNAL,M8Fault.Code.ACK_UNCERTAIN);
            }
            long seq=checkpoints.size()+1L;
            String prev=receipts.isEmpty()?"0".repeat(64):receipts.getLast().sha256();
            Checkpoint record=new Checkpoint(seq,prev,next,Instant.now(clock),plan,facts,List.copyOf(faults));
            byte[] data=codec.encode(record);
            if(Math.addExact(journalBytes(),data.length)>limits.maxJournalBytes())throw M8Fault.fail(M8Fault.Phase.JOURNAL,M8Fault.Code.RESOURCE_LIMIT);
            Path path=cp(seq);
            try{
                revalidateRoots();inject("checkpoint-create",(int)seq);
                try(FileChannel channel=FileChannel.open(path,StandardOpenOption.WRITE,StandardOpenOption.CREATE_NEW)){
                    own(path);ByteBuffer b=ByteBuffer.wrap(data);while(b.hasRemaining())channel.write(b);
                    inject("checkpoint-force",(int)seq);channel.force(true);
                }
                inject("checkpoint-readback",(int)seq);
            }catch(IOException e){throw M8Fault.fail(M8Fault.Phase.JOURNAL,M8Fault.Code.ACK_UNCERTAIN);}
            Receipt r=new Receipt(request.jobId(),seq,digest(data),next);
            checkpoints.add(record);receipts.add(r);
            replay(r);event=next;return r;
        }
        private Checkpoint checkpointForComparison(M8Plan p){
            return new Checkpoint(0,"0".repeat(64),Event.INTENT,Instant.EPOCH,p,List.of(),List.of());
        }
        private long journalBytes(){long total=0;for(int i=1;i<=checkpoints.size();i++){
            try{total+=Files.size(cp(i));}catch(IOException e){throw M8Fault.fail(M8Fault.Phase.JOURNAL,M8Fault.Code.ACK_UNCERTAIN);}
        }return total;}
        private boolean legal(Event current,Event next){
            return switch(next){
                case INTENT -> current==null;
                case STAGED_VERIFIED -> current==Event.INTENT;
                case PUBLISHING -> current==Event.STAGED_VERIFIED;
                case PUBLISHED -> current==Event.PUBLISHING;
                case COMPLETED -> current==Event.PUBLISHED;
                case FAILED -> current==Event.INTENT||current==Event.STAGED_VERIFIED;
                case RECOVERY_REQUIRED -> current!=Event.RECOVERY_REQUIRED;
            };
        }
        /** Reconstruct the complete journal from disk, not the in-memory receipt list. */
        @Override public Receipt replay(Receipt expected){
            active();
            try{
                checkOwned(privateRoot);
                List<Path> disk;
                try(var entries=Files.list(privateRoot)){
                    disk=entries.filter(p->p.getFileName().toString().startsWith("checkpoint-"))
                            .sorted().toList();
                }
                if(disk.size()!=checkpoints.size()||disk.isEmpty()||disk.size()>8)
                    throw new IOException("Journal member count changed");
                String prev="0".repeat(64);Receipt last=null;
                Event preceding=null;M8Plan diskPlan=null;
                long total=0;
                List<SliceEvidence> stagedInventory=null;
                for(int i=1;i<=disk.size();i++){
                    Path path=disk.get(i-1);
                    if(!path.equals(cp(i)))throw new IOException("Journal sequence gap/trailing checkpoint");
                    checkOwned(path);
                    long size=Files.size(path);
                    if(size<=0||size>limits.maxCheckpointBytes()
                        ||size>limits.maxJournalBytes()-total)throw new IOException("Journal size bound");
                    total+=size;
                    byte[] bytes=new byte[Math.toIntExact(size)];
                    try(InputStream input=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)){
                        int off=0;
                        while(off<bytes.length){int n=input.read(bytes,off,bytes.length-off);
                            if(n<0)throw new IOException("Truncated journal");off+=n;}
                        if(input.read()!=-1)throw new IOException("Expanded journal");
                    }
                    Checkpoint c=codec.decode(bytes);
                    if(c.sequence()!=i||!c.previousSha256().equals(prev)||!c.plan().jobId().equals(request.jobId())
                        ||!legal(preceding,c.event()))throw new IOException("Journal transition invalid");
                    byte[] canonicalPlan=codec.encode(checkpointForComparison(c.plan()));
                    if(diskPlan==null)diskPlan=c.plan();
                    else if(!Arrays.equals(canonicalPlan,codec.encode(checkpointForComparison(diskPlan))))
                        throw new IOException("Journal plan changed");
                    if(c.event()==Event.STAGED_VERIFIED)stagedInventory=c.slices();
                    if((c.event()==Event.PUBLISHING||c.event()==Event.PUBLISHED||c.event()==Event.COMPLETED)
                        && (stagedInventory==null||!stagedInventory.equals(c.slices())))
                        throw new IOException("Journal inventory changed");
                    if(i<=checkpoints.size() && !Arrays.equals(codec.encode(checkpoints.get(i-1)),bytes))
                        throw new IOException("Journal entry changed");
                    prev=digest(bytes);preceding=c.event();
                    last=new Receipt(request.jobId(),i,prev,c.event());
                }
                if(last==null||!last.equals(expected)||plan==null
                    ||!Arrays.equals(codec.encode(checkpointForComparison(plan)),
                        codec.encode(checkpointForComparison(diskPlan))))
                    throw new IOException("Journal head differs");
                return last;
            }catch(IOException|RuntimeException e){
                throw new M8Fault.Failure(List.of(new M8Fault(M8Fault.Phase.JOURNAL,
                        M8Fault.Code.ACK_UNCERTAIN)),true);
            }
        }
        @Override public void publish(Receipt publishingReceipt,List<SliceEvidence> evidence){
            active();if(event!=Event.PUBLISHING||!replay(publishingReceipt).equals(publishingReceipt)
                    ||!evidence.equals(checkpoints.get(1).slices()))
                throw M8Fault.fail(M8Fault.Phase.PUBLISH,M8Fault.Code.PUBLICATION_UNCERTAIN);
            if(finalCreated)throw M8Fault.fail(M8Fault.Phase.PUBLISH,M8Fault.Code.OUTPUT_CONFLICT);
            try{
                revalidateRoots();inject("final-create",-1);
                Files.createDirectory(finalRoot);finalCreated=true;publication=M8Result.Publication.PARTIAL_OR_UNKNOWN;
                own(finalRoot);
                for(int z=0;z<plan.depth();z++){
                    Path from=stage(z),to=finalRoot.resolve(plan.slices().get(z).filename());
                    ensureRegular(from);
                    if(!same(stages.get(z),hash(from,limits.maxOutputBytes())))throw M8Fault.fail(M8Fault.Phase.PUBLISH,M8Fault.Code.STAGE_CHANGED);
                    checkOwned(from);revalidateRoots();inject("link",z);
                    Files.createLink(to,from);publicLinks++;
                    own(to);
                    if(!sameEntry(identities.get(from),observe(to)))
                        throw M8Fault.fail(M8Fault.Phase.PUBLISH,M8Fault.Code.FINAL_CHANGED);
                }
                verifyPublished(evidence);
            }catch(IOException|RuntimeException e){
                if(e instanceof M8Fault.Failure f)throw f;
                throw M8Fault.fail(M8Fault.Phase.PUBLISH,M8Fault.Code.PUBLICATION_UNCERTAIN);
            }
        }
        @Override public List<SliceEvidence> verifyPublished(List<SliceEvidence> expected){
            active();if(!finalCreated||expected.size()!=plan.depth())throw M8Fault.fail(M8Fault.Phase.PUBLISH,M8Fault.Code.FINAL_CHANGED);
            try{
                checkOwned(finalRoot);revalidateRoots();inject("final-readback",-1);
                Set<String> actual=new HashSet<>();
                try(var walk=Files.list(finalRoot)){walk.forEach(p->actual.add(p.getFileName().toString()));}
                Set<String> wanted=new HashSet<>();for(var slice:plan.slices())wanted.add(slice.filename());
                if(markerVerified)wanted.add("PAI-M8-COMPLETE.json");
                if(!actual.equals(wanted))throw M8Fault.fail(M8Fault.Phase.PUBLISH,M8Fault.Code.FINAL_CHANGED);
                // Marker and terminal journal remain part of J AND M AND I even at the
                // *last* read-back boundary; names alone are never completion evidence.
                if(markerVerified){
                    readMarkerExact(finalRoot.resolve("PAI-M8-COMPLETE.json"),expectedMarkerBytes);
                    Receipt terminal=receipts.getLast();
                    if(terminal.event()!=Event.COMPLETED||!replay(terminal).equals(terminal))
                        throw new IOException("Terminal journal differs");
                }
                for(int z=0;z<plan.depth();z++){
                    Path p=finalRoot.resolve(plan.slices().get(z).filename());ensureRegular(p);
                    checkOwned(p);
                    if(!same(expected.get(z).digest(),hash(p,limits.maxOutputBytes())))
                        throw M8Fault.fail(M8Fault.Phase.PUBLISH,M8Fault.Code.FINAL_CHANGED);
                }
                publication=M8Result.Publication.COMPLETE_BYTES_VERIFIED;
                return List.copyOf(expected);
            }catch(IOException e){throw M8Fault.fail(M8Fault.Phase.PUBLISH,M8Fault.Code.READ_FAILED);}
        }
        @Override public void cleanupOwnedData(){
            active();
            try{
                revalidateRoots();inject("cleanup",-1);
                if(!openedStages.isEmpty())throw new IOException("Open stage handles");
                // Refuse orphaned/unknown entries rather than ignore an unowned partial write.
                try(var all=Files.list(privateRoot)){
                    for(Path entry:all.toList()){
                        if(entry.getFileName().toString().startsWith("image-") && !owned.contains(entry))
                            throw new IOException("Untracked stage entry");
                    }
                }
                // Created-but-unverified stage files are still job-owned and must be cleaned.
                // Refuse deletion if any observed entry identity changed; never chase paths.
                for(Path p:owned.stream().filter(q->q.getParent()!=null&&q.getParent().equals(privateRoot))
                        .filter(q->q.getFileName().toString().startsWith("image-")).sorted(Comparator.reverseOrder()).toList()){
                    if(Files.exists(p,LinkOption.NOFOLLOW_LINKS)){
                        checkOwned(p);
                        int z=Integer.parseInt(p.getFileName().toString().substring(6,12))-1;
                        if(z<stages.size() && !same(stages.get(z),hash(p,limits.maxOutputBytes())))
                            throw new IOException("Verified stage changed");
                        Files.delete(p);
                    }
                }
                if(Files.exists(snapshot,LinkOption.NOFOLLOW_LINKS)){
                    checkOwned(snapshot);
                    if(!same(sourceDigest,hash(snapshot,limits.nifti().maxEncodedBytes())))
                        throw new IOException("Source snapshot changed");
                    Files.delete(snapshot);
                }
                if(checkpoints.isEmpty() && Files.exists(privateRoot,LinkOption.NOFOLLOW_LINKS)){
                    checkOwned(privateRoot);
                    // Leave damaged/unrecognized entries intact rather than remove them by name.
                    try(var entries=Files.list(privateRoot)){
                        if(entries.findAny().isPresent())throw new IOException("Unknown remaining entries");
                    }
                    Files.delete(privateRoot);
                }
            }catch(IOException|RuntimeException e){
                throw new M8Fault.Failure(List.of(new M8Fault(M8Fault.Phase.CLEANUP,
                        M8Fault.Code.CLEANUP_FAILED)),true);
            }
        }
        @Override public void sealCompletion(Receipt completedReceipt){
            active();if(event!=Event.COMPLETED||!replay(completedReceipt).equals(completedReceipt))
                throw M8Fault.fail(M8Fault.Phase.PUBLISH,M8Fault.Code.ACK_UNCERTAIN);
            List<SliceEvidence> actual=checkpoints.getLast().slices();
            verifyPublished(actual);
            byte[] evidenceBytes=inventoryJson(actual);
            String payload="{\"schema\":\"PAI-M8-COMPLETE-1\",\"profile\":\"M8-MR-1\",\"jobId\":\""
                    +request.jobId()+"\",\"completedSequence\":"+completedReceipt.sequence()
                    +",\"completedSha256\":\""+completedReceipt.sha256()+"\",\"inventorySha256\":\""
                    +digest(evidenceBytes)+"\"}\n";
            byte[] data=payload.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            Path privateMarker=privateRoot.resolve("PAI-M8-COMPLETE.json");
            Path publicMarker=finalRoot.resolve("PAI-M8-COMPLETE.json");
            try{
                revalidateRoots();inject("marker-create",-1);
                try(FileChannel ch=FileChannel.open(privateMarker,StandardOpenOption.WRITE,StandardOpenOption.CREATE_NEW)){
                    own(privateMarker);ch.write(ByteBuffer.wrap(data));
                    inject("marker-force",-1);ch.force(true);
                }
            }catch(IOException e){throw M8Fault.fail(M8Fault.Phase.PUBLISH,M8Fault.Code.WRITE_FAILED);}
            try{
                readMarkerExact(privateMarker,data);
                checkOwned(privateMarker);inject("marker-link",-1);
                Files.createLink(publicMarker,privateMarker);own(publicMarker);
                inject("marker-readback",-1);
                if(!sameEntry(identities.get(privateMarker),observe(publicMarker)))
                    throw M8Fault.fail(M8Fault.Phase.PUBLISH,M8Fault.Code.FINAL_CHANGED);
                readMarkerExact(publicMarker,data);
                expectedMarkerBytes=data.clone();
                markerVerified=true;
                verifyPublished(actual);
                // Completion requires the journal head to remain exact after marker publication.
                replay(completedReceipt);
            }catch(IOException e){throw M8Fault.fail(M8Fault.Phase.PUBLISH,M8Fault.Code.PUBLICATION_UNCERTAIN);}
        }
        /** Read exact expected marker bytes with a pre-allocation size check. */
        private byte[] readMarkerExact(Path marker,byte[] expected) throws IOException {
            if(expected==null||expected.length>8192)throw new IOException("Invalid expected marker budget");
            checkOwned(marker);
            if(Files.size(marker)!=expected.length)throw new IOException("Marker size changed");
            byte[] actual=new byte[expected.length];
            try(InputStream input=Files.newInputStream(marker,LinkOption.NOFOLLOW_LINKS)){
                int pos=0;
                while(pos<actual.length){int got=input.read(actual,pos,actual.length-pos);
                    if(got<=0)throw new IOException("Incomplete marker");pos+=got;}
                if(input.read()!=-1)throw new IOException("Expanded marker");
            }
            if(!Arrays.equals(actual,expected))throw new IOException("Marker content changed");
            return actual;
        }
        private byte[] inventoryJson(List<SliceEvidence> slices){
            // Exact canonical SliceEvidence array serialization from PAI-M8-JOURNAL-1.
            // All quoted fields here have restricted producer-generated ASCII alphabets.
            StringBuilder b=new StringBuilder("[");
            for(SliceEvidence x:slices){
                if(b.length()>1)b.append(',');
                b.append("{\"sourceZ\":").append(x.sourceZ())
                 .append(",\"filename\":\"").append(x.filename()).append('"')
                 .append(",\"sopUid\":\"").append(x.sopUid()).append('"')
                 .append(",\"size\":").append(x.digest().sizeBytes())
                 .append(",\"sha256\":\"").append(x.digest().sha256()).append('"')
                 .append(",\"comparedVoxels\":").append(x.check().comparedVoxels())
                 .append(",\"comparedCorners\":").append(x.check().comparedCorners())
                 .append(",\"maxWorldError\":\"").append(Double.toHexString(x.check().maxWorldErrorMm())).append('"')
                 .append(",\"maxIntensityError\":\"").append(Double.toHexString(x.check().maxIntensityError())).append("\"}");
            }
            b.append("]\n");
            return b.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        @Override public M8Result.Publication publicationKnowledge(){return publication;}
        @Override public RelativePath outputDirectory(){return new RelativePath(RelativePath.Root.OUTPUT,finalRoot.getFileName().toString());}
        @Override public void close(){
            if(closed)return;
            try{
                inject("release",-1);
                if(!openedStages.isEmpty())throw new IOException("Unreleased stage handles");
                closed=true;
            }catch(IOException e){throw new M8Fault.Failure(List.of(new M8Fault(M8Fault.Phase.RELEASE,
                    M8Fault.Code.RELEASE_FAILED)),true);}
        }
    }
}
