package org.cbihi.mrinormalizer.infrastructure.filesystem;

import com.sun.jna.*;
import com.sun.jna.ptr.*;
import com.sun.jna.win32.StdCallLibrary;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;

/** D1 admission only. F1/F8 still perform private-creation, lease and publication probes.
 * No drive-letter whitelist, ACL repair, network or cloud durability claim. */
public final class DestinationQualification {
    private DestinationQualification() { }
    private static final Set<String> UNIX = Set.of("ext2", "ext3", "ext4", "xfs", "btrfs", "overlay", "apfs", "hfs", "ufs", "zfs");
    static int windowsDriveType(Path path) {
        if (path == null || path.getRoot() == null || !path.getFileSystem().provider().getClass().getName().equals("sun.nio.fs.WindowsFileSystemProvider")
                || path.toString().startsWith("\\\\")) return 0;
        try { return Kernel.INSTANCE.GetDriveTypeW(new WString(path.getRoot().toString())); }
        catch (RuntimeException | LinkageError unavailable) { return 0; }
    }
    public static JsonManifestStore openForwardManifest(Path source, Path output, UUID id, JsonManifestStore.InitializationTrace trace) {
        return new JsonManifestStore(source, output, output.resolve(".pai-f9-manifest"), id, trace);
    }
    public static Path requireLocal(Path selected) throws IOException {
        if (selected == null || selected.getFileSystem() != FileSystems.getDefault())
            throw new IOException("DESTINATION_PROVIDER_UNSUPPORTED");
        Path path = selected.toAbsolutePath().normalize();
        String provider = path.getFileSystem().provider().getClass().getName();
        boolean windows = provider.equals("sun.nio.fs.WindowsFileSystemProvider");
        Path cursor = path.getRoot();
        if (cursor == null) throw new IOException("DESTINATION_ROOT_UNAVAILABLE");
        if (windows) {
            try {
                int type = windowsDriveType(path);
                if (type != 2 && type != 3) throw new IOException("DESTINATION_LOCAL_NTFS_VOLUME_REQUIRED");
                char[] device = new char[4096];
                String drive = cursor.toString().substring(0,2);
                if (Kernel.INSTANCE.QueryDosDeviceW(new WString(drive), device, device.length) == 0
                        || Native.toString(device).startsWith("\\??\\"))
                    throw new IOException("DESTINATION_DRIVE_ALIAS_UNQUALIFIED");
            } catch (LinkageError unavailable) { throw new IOException("DESTINATION_NATIVE_QUALIFICATION_UNAVAILABLE", unavailable); }
        }
        for (Path part : path) {
            cursor = cursor.resolve(part);
            BasicFileAttributes a = Files.readAttributes(cursor, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!a.isDirectory() || a.isSymbolicLink() || a.isOther() || !cursor.toRealPath().equals(cursor))
                throw new IOException("DESTINATION_UNSTABLE_OR_LINKED");
            if (windows) {
                int flags = Kernel.INSTANCE.GetFileAttributesW(new WString(cursor.toString()));
                if (flags == -1 || (flags & 0x400) != 0)
                    throw new IOException("DESTINATION_REPARSE_POINT_UNQUALIFIED");
            }
        }
        String type = Files.getFileStore(path).type().toLowerCase(Locale.ROOT);
        if (windows ? !type.equals("ntfs") : !Set.of("sun.nio.fs.LinuxFileSystemProvider",
                "sun.nio.fs.MacOSXFileSystemProvider", "sun.nio.fs.BsdFileSystemProvider").contains(provider) || !UNIX.contains(type))
            throw new IOException("DESTINATION_PROVIDER_UNSUPPORTED");
        return path;
    }

    /** Default owner of newly created objects, distinct from the existing parent owner.
     * Read-only token query; never changes privileges, ownership or an existing ACL. */
    public static UserPrincipal privateOwner(Path output) throws IOException {
        if (!output.getFileSystem().provider().getClass().getName().equals("sun.nio.fs.WindowsFileSystemProvider"))
            return Files.getOwner(output, LinkOption.NOFOLLOW_LINKS);
        try {
            PointerByReference token = new PointerByReference();
            if (!Advapi.INSTANCE.OpenThreadToken(Kernel.INSTANCE.GetCurrentThread(), 8, true, token)) {
                if (Native.getLastError() != 1008 || !Advapi.INSTANCE.OpenProcessToken(Kernel.INSTANCE.GetCurrentProcess(), 8, token))
                    throw new IOException("DESTINATION_OWNER_TOKEN_UNAVAILABLE");
            }
            try {
                IntByReference length = new IntByReference();
                Advapi.INSTANCE.GetTokenInformation(token.getValue(), 4, null, 0, length); // TokenOwner
                if (length.getValue() < Native.POINTER_SIZE || length.getValue() > 65536)
                    throw new IOException("DESTINATION_OWNER_TOKEN_INVALID");
                try (Memory data = new Memory(length.getValue())) {
                    if (!Advapi.INSTANCE.GetTokenInformation(token.getValue(), 4, data, (int)data.size(), length))
                        throw new IOException("DESTINATION_OWNER_TOKEN_UNAVAILABLE");
                    Pointer sid = data.getPointer(0);
                    char[] name = new char[1024], domain = new char[1024];
                    IntByReference n = new IntByReference(name.length), d = new IntByReference(domain.length), use = new IntByReference();
                    if (!Advapi.INSTANCE.LookupAccountSidW(null, sid, name, n, domain, d, use))
                        throw new IOException("DESTINATION_OWNER_LOOKUP_UNAVAILABLE");
                    String account = Native.toString(domain);
                    if (!account.isEmpty()) account += "\\";
                    account += Native.toString(name);
                    return output.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(account);
                }
            } finally {
                if (!Kernel.INSTANCE.CloseHandle(token.getValue())) throw new IOException("DESTINATION_OWNER_TOKEN_RELEASE_FAILED");
            }
        } catch (LinkageError failure) { throw new IOException("DESTINATION_OWNER_NATIVE_UNAVAILABLE", failure); }
    }
    public static Object entryIdentity(Path path) throws IOException {
        if (path.getFileSystem().provider().getClass().getName().equals("sun.nio.fs.WindowsFileSystemProvider"))
            return M8WindowsNtfsIdentity.read(path);
        Object key = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).fileKey();
        if (key == null) throw new IOException("ENTRY_IDENTITY_UNAVAILABLE");
        return key;
    }
    public static FileAttribute<?>[] privateAttributes(Path parent, boolean directory) throws IOException {
        if (Files.getFileAttributeView(parent, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS) != null)
            return new FileAttribute<?>[]{PosixFilePermissions.asFileAttribute(
                    PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------"))};
        var builder = AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(privateOwner(parent))
                .setPermissions(EnumSet.allOf(AclEntryPermission.class));
        if (directory) builder.setFlags(AclEntryFlag.FILE_INHERIT, AclEntryFlag.DIRECTORY_INHERIT);
        List<AclEntry> acl = List.of(builder.build());
        return new FileAttribute<?>[]{new FileAttribute<List<AclEntry>>() {
            public String name() { return "acl:acl"; }
            public List<AclEntry> value() { return acl; }
        }};
    }

    public static void verifyPrivateDirectory(Path path) throws IOException {
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)
                || !path.toRealPath().equals(path) || !Files.getOwner(path, LinkOption.NOFOLLOW_LINKS).equals(privateOwner(path)))
            throw new IOException("PRIVATE_DIRECTORY_OWNER_OR_IDENTITY_UNQUALIFIED");
        var posix = Files.getFileAttributeView(path, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (posix != null) {
            if (!posix.readAttributes().permissions().equals(PosixFilePermissions.fromString("rwx------")))
                throw new IOException("PRIVATE_DIRECTORY_ACCESS_UNQUALIFIED");
        } else {
            var owner = privateOwner(path);
            var view = Files.getFileAttributeView(path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
            if (view == null) throw new IOException("PRIVATE_DIRECTORY_ACCESS_UNQUALIFIED");
            boolean full = false;
            for (var ace : view.getAcl()) {
                if (ace.type() == AclEntryType.ALLOW) {
                    if (!ace.principal().equals(owner)) throw new IOException("PRIVATE_DIRECTORY_ACCESS_UNQUALIFIED");
                    if (!ace.flags().contains(AclEntryFlag.INHERIT_ONLY) && ace.permissions().containsAll(EnumSet.allOf(AclEntryPermission.class))) full = true;
                }
                if (ace.type() == AclEntryType.DENY && ace.principal().equals(owner)) throw new IOException("PRIVATE_DIRECTORY_ACCESS_UNQUALIFIED");
            }
            if (!full) throw new IOException("PRIVATE_DIRECTORY_ACCESS_UNQUALIFIED");
        }
    }
    /** Probe Java's actual create/force/link/no-replace path capability before job artifacts.
     * Exclusive random private probe files only; identity checked before cleanup. */
    public static void requirePathCapacity(Path root, int additionalCharacters) throws IOException {
        if (!root.getFileSystem().provider().getClass().getName().equals("sun.nio.fs.WindowsFileSystemProvider")) return;
        requireLocal(root);
        int length = Math.max(64, additionalCharacters - 1);
        if (length > 240) throw new IOException("DESTINATION_PATH_BUDGET_UNSUPPORTED");
        String unique = ".pai-probe-" + UUID.randomUUID();
        Path first = root.resolve(unique + "a".repeat(length - unique.length()));
        Path second = root.resolve(unique + "b".repeat(length - unique.length()));
        Object identity = null; boolean linked = false;
        IOException failure = null;
        try {
            try (var c = java.nio.channels.FileChannel.open(first,
                    Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, com.sun.nio.file.ExtendedOpenOption.NOSHARE_DELETE),
                    privateAttributes(root, false))) {
                identity = entryIdentity(first);
                var buffer = java.nio.ByteBuffer.wrap(new byte[]{80,65,73});
                while (buffer.hasRemaining()) c.write(buffer); c.force(true);
                Files.createLink(second, first); linked = true;
                byte[] observed;
                try (var in = Files.newInputStream(second, LinkOption.NOFOLLOW_LINKS)) { observed = in.readNBytes(4); }
                if (!Files.isSameFile(first, second) || !identity.equals(entryIdentity(second))
                        || !Arrays.equals(observed, new byte[]{80,65,73}))
                    throw new IOException("DESTINATION_PUBLICATION_PROBE_FAILED");
                try { Files.createLink(second, first); throw new IOException("DESTINATION_REPLACEMENT_NOT_REFUSED"); }
                catch (FileAlreadyExistsException expected) { }
            }
        } catch (IOException | UnsupportedOperationException error) {
            failure = new IOException("DESTINATION_PATH_OR_PUBLICATION_UNAVAILABLE", error);
        } finally {
            for (Path p : linked ? List.of(second, first) : List.of(first)) {
                if (identity != null) try {
                    if (!identity.equals(entryIdentity(p))) throw new IOException("DESTINATION_PROBE_IDENTITY_CHANGED");
                    Files.delete(p);
                } catch (IOException cleanup) {
                    if (failure == null) failure = new IOException("DESTINATION_PROBE_CLEANUP_UNCERTAIN", cleanup);
                    else failure.addSuppressed(cleanup);
                }
            }
        }
        if (failure != null) throw failure;
    }
    private interface Kernel extends StdCallLibrary {
        Kernel INSTANCE = Native.load("kernel32", Kernel.class);
        int GetDriveTypeW(WString root);
        int QueryDosDeviceW(WString drive, char[] target, int size);
        int GetFileAttributesW(WString path);
        Pointer GetCurrentProcess();
        Pointer GetCurrentThread();
        boolean CloseHandle(Pointer handle);
    }
    private interface Advapi extends StdCallLibrary {
        Advapi INSTANCE = Native.load("advapi32", Advapi.class);
        boolean OpenProcessToken(Pointer process, int access, PointerByReference token);
        boolean OpenThreadToken(Pointer thread, int access, boolean self, PointerByReference token);
        boolean GetTokenInformation(Pointer token, int kind, Pointer data, int size, IntByReference length);
        boolean LookupAccountSidW(WString system, Pointer sid, char[] name, IntByReference nameSize,
                                  char[] domain, IntByReference domainSize, IntByReference use);
    }
}
