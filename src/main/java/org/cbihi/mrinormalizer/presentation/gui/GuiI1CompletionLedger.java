package org.cbihi.mrinormalizer.presentation.gui;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** GUI-level replay guard. The conversion backends remain authoritative. */
public final class GuiI1CompletionLedger {
    private GuiI1CompletionLedger() {}
    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private static void update(MessageDigest d, String value) {
        byte[] b=value.getBytes(StandardCharsets.UTF_8);
        d.update(ByteBuffer.allocate(4).putInt(b.length).array()); d.update(b);
    }
    public static String fingerprint(GuiI1Scanner.ScanResult scan, List<Integer> selected, Path output) throws IOException {
        MessageDigest d=digest();
        update(d,"GUI-I1-E1-REPLAY-1"); update(d,scan.direction().name());
        update(d,output.toRealPath().toString());
        for (int index:selected) {
            var candidate=scan.candidates().get(index);
            if (candidate.seriesUid()!=null) update(d,candidate.seriesUid());
            for (String name:candidate.memberNames()) {
                Path p=scan.direction()==GuiI1Scanner.Direction.NIFTI_TO_DICOM
                    ? scan.kind()==GuiI1Scanner.InputKind.NIFTI_FILE ? scan.input() : scan.input().resolve(name)
                    : candidate.directory().resolve(name);
                if (!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(p))
                    throw new IOException("Input identity cannot be established");
                update(d,p.toRealPath().toString());
                MessageDigest file=digest();long size=0;
                try (InputStream in=Files.newInputStream(p,LinkOption.NOFOLLOW_LINKS)) {
                    byte[] buffer=new byte[65536];int n;
                    while ((n=in.read(buffer))!=-1) {file.update(buffer,0,n);size=Math.addExact(size,n);}
                }
                update(d,Long.toString(size));update(d,HexFormat.of().formatHex(file.digest()));
            }
        }
        return HexFormat.of().formatHex(d.digest());
    }
    private static Path root(Path output,boolean create) throws IOException {
        Path internal=output.resolve(".pai-internal");
        Path records=internal.resolve("gui-verified");
        for (Path path:List.of(internal,records)) {
            if (create && Files.notExists(path,LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(path);
            if (Files.exists(path,LinkOption.NOFOLLOW_LINKS) &&
                (Files.isSymbolicLink(path)||!Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS)))
                throw new IOException("Untrusted internal evidence root");
        }
        return records;
    }
    public static boolean exists(Path output,String id) throws IOException {
        Path record=root(output,false).resolve(id+".record");
        if (Files.notExists(record,LinkOption.NOFOLLOW_LINKS)) return false;
        if (!Files.isRegularFile(record,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(record))
            throw new IOException("Corrupt completion record");
        return Files.readString(record,StandardCharsets.US_ASCII).equals("VERIFIED "+id+"\n");
    }
    public static void mark(Path output,String id) throws IOException {
        Path dir=root(output,true), target=dir.resolve(id+".record");
        if (Files.exists(target,LinkOption.NOFOLLOW_LINKS)) {
            if (!exists(output,id)) throw new IOException("Completion history collision");
            return;
        }
        Path temp=Files.createTempFile(dir,".gui-", ".tmp");
        try {
            byte[] bytes=("VERIFIED "+id+"\n").getBytes(StandardCharsets.US_ASCII);
            try(FileChannel channel=FileChannel.open(temp,StandardOpenOption.WRITE)) {
                ByteBuffer b=ByteBuffer.wrap(bytes);while(b.hasRemaining())channel.write(b);channel.force(true);
            }
            Files.createLink(target,temp); // no replace
            if(!exists(output,id))throw new IOException("Verification record readback failed");
        } finally {Files.deleteIfExists(temp);}
    }
}
