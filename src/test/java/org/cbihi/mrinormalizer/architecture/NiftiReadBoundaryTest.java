package org.cbihi.mrinormalizer.architecture;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import org.cbihi.mrinormalizer.application.dataset.nifti.*;
import org.cbihi.mrinormalizer.application.port.out.NiftiVolumeReader;
import org.cbihi.mrinormalizer.infrastructure.nifti.Nifti1VolumeReader;
import org.junit.jupiter.api.Test;

class NiftiReadBoundaryTest {
    @Test void T44_compiledDependenciesAndApi() throws Exception {
        assertEquals(1,Nifti1VolumeReader.class.getConstructors().length);
        assertArrayEquals(new Class<?>[]{Path.class,NiftiReadLimits.class},Nifti1VolumeReader.class.getConstructors()[0].getParameterTypes());
        for(String name:List.of("Nifti1Header","StrictGzipInputStream")) {var c=Class.forName("org.cbihi.mrinormalizer.infrastructure.nifti."+name);assertFalse(Modifier.isPublic(c.getModifiers()));assertTrue(Modifier.isFinal(c.getModifiers()));}
        for(Class<?> c:List.of(NiftiReadLimits.class,NiftiReadResult.class,NiftiReadFailure.class,NiftiReadFinding.class,NiftiVolumeReader.class)) {
            for(var f:c.getDeclaredFields()) {assertFalse(f.getType().getName().startsWith("java.nio.file"));assertFalse(f.getType().getName().startsWith("java.io"));}
            compiled(c,true);
        }
        compiled(Nifti1VolumeReader.class,false);for(var c:Nifti1VolumeReader.class.getDeclaredClasses())compiled(c,false);
        compiled(Class.forName("org.cbihi.mrinormalizer.infrastructure.nifti.Nifti1Header"),false);
        compiled(Class.forName("org.cbihi.mrinormalizer.infrastructure.nifti.StrictGzipInputStream"),false);
    }
    static void compiled(Class<?> c,boolean application) throws Exception {
        try(var in=new DataInputStream(c.getResourceAsStream("/"+c.getName().replace('.','/')+".class"))) {
            assertEquals(0xcafebabe,in.readInt());in.readUnsignedShort();in.readUnsignedShort();int count=in.readUnsignedShort();
            for(int i=1;i<count;i++) {int tag=in.readUnsignedByte();switch(tag) {
                case 1 -> {String s=in.readUTF();for(String banned:List.of("javax/swing","org/dcm4che","application/service/","application/provenance/copy/","ProcessBuilder","java/lang/Runtime","setPosixFilePermissions","createTempFile","newOutputStream","MessageDigest","Nifti1VolumeWriter"))assertFalse(s.contains(banned),c.getName()+":"+s);
                    if(application)assertFalse(s.contains("infrastructure/"),s);}
                case 3,4,9,10,11,12,17,18 -> in.skipNBytes(4);
                case 5,6 -> {in.skipNBytes(8);i++;}
                case 7,8,16,19,20 -> in.skipNBytes(2);
                case 15 -> in.skipNBytes(3);
                default -> fail("Unknown class tag");
            }}
        }
    }
    @Test void T42_independentFixtureOracle() throws Exception {
        String test=Files.readString(Path.of("src/test/java/org/cbihi/mrinormalizer/infrastructure/nifti/Nifti1VolumeReaderTest.java"));
        assertFalse(test.contains("Nifti1Header"));assertFalse(test.contains("Nifti1VolumeWriter"));assertTrue(test.contains("putInt(0,348)"));
        String geometry=Files.readString(Path.of("src/test/java/org/cbihi/mrinormalizer/infrastructure/nifti/NiftiReadGeometryTest.java"));assertFalse(geometry.contains("Nifti1Header"));assertFalse(geometry.contains("NiftiQuaternion"));assertTrue(geometry.contains("8,21,27"));
    }
}
