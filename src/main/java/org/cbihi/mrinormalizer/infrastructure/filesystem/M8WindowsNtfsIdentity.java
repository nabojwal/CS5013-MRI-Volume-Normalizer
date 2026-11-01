package org.cbihi.mrinormalizer.infrastructure.filesystem;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.WString;
import com.sun.jna.win32.StdCallLibrary;
import java.io.IOException;
import java.nio.file.Path;

/** Windows-only, no-follow identity observation through the documented Win32 file ID.
 * Does not attempt identity from timestamps, byte contents or path equality. */
final class M8WindowsNtfsIdentity {
    private M8WindowsNtfsIdentity() {}
    private static final int FILE_READ_ATTRIBUTES=0x80;
    private static final int FILE_SHARE_READ=1, FILE_SHARE_WRITE=2, FILE_SHARE_DELETE=4;
    private static final int OPEN_EXISTING=3;
    private static final int FILE_FLAG_OPEN_REPARSE_POINT=0x00200000;
    private static final int FILE_FLAG_BACKUP_SEMANTICS=0x02000000;
    private interface Kernel32 extends StdCallLibrary {
        Kernel32 INSTANCE=Native.load("kernel32",Kernel32.class);
        Pointer CreateFileW(WString name,int access,int share,Pointer security,int creation,
                            int flags,Pointer template);
        boolean GetFileInformationByHandle(Pointer handle,FileInformation info);
        boolean CloseHandle(Pointer handle);
        int GetLastError();
    }
    @Structure.FieldOrder({"low","high"})
    public static class FileTime extends Structure {
        public int low,high;
    }
    @Structure.FieldOrder({"attributes","creation","access","write","volume",
        "sizeHigh","sizeLow","linkCount","indexHigh","indexLow"})
    public static class FileInformation extends Structure {
        public int attributes;
        public FileTime creation,access,write;
        public int volume,sizeHigh,sizeLow,linkCount,indexHigh,indexLow;
    }
    record Id(int volume,int indexHigh,int indexLow) {}
    static Id read(Path path) throws IOException {
        Kernel32 k=Kernel32.INSTANCE;
        Pointer handle=k.CreateFileW(new WString(path.toAbsolutePath().toString()),
            FILE_READ_ATTRIBUTES, FILE_SHARE_READ|FILE_SHARE_WRITE|FILE_SHARE_DELETE,
            null,OPEN_EXISTING,FILE_FLAG_OPEN_REPARSE_POINT|FILE_FLAG_BACKUP_SEMANTICS,null);
        if(handle==null || Pointer.nativeValue(handle)==-1L)
            throw new IOException("NTFS identity handle unavailable (Win32 error "+k.GetLastError()+")");
        try {
            FileInformation info=new FileInformation();
            if(!k.GetFileInformationByHandle(handle,info))
                throw new IOException("NTFS file identity unavailable (Win32 error "+k.GetLastError()+")");
            // The provider policy already restricts to local NTFS, not ReFS/network paths.
            return new Id(info.volume,info.indexHigh,info.indexLow);
        } finally {
            if(!k.CloseHandle(handle))
                throw new IOException("NTFS identity handle release failed (Win32 error "+k.GetLastError()+")");
        }
    }
}
