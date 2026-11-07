package org.cbihi.mrinormalizer;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.cbihi.mrinormalizer.application.dataset.nifti.*;
import org.cbihi.mrinormalizer.application.port.out.NiftiVolumeReader;
import org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath;
import org.cbihi.mrinormalizer.domain.model.*;
import org.junit.jupiter.api.Test;

class NiftiReadModelTest {
    private final RelativePath source=new RelativePath(RelativePath.Root.SOURCE,"synthetic");
    private ImageVolume volume() {return new ImageVolume(new VolumeGeometry(1,1,1,1,1,1,new double[3],new double[]{1,0,0},new double[]{0,1,0},new double[]{0,0,1},CoordinateSystem.NIFTI_RAS),new ImmutableVoxelData(1,1,1,ScalarType.UINT8,new long[]{0}),IntensityTransform.identityNotDeclared());}
    @Test void T01_explicitLimits() {
        long[] good={1000,1000,1,352,1_048_592,10};
        for(int i=0;i<6;i++)for(long bad:new long[]{0,-1,Long.MIN_VALUE}) {long[] a=good.clone();a[i]=bad;assertThrows(IllegalArgumentException.class,()->new NiftiReadLimits(a[0],a[1],a[2],a[3],a[4],a[5]));}
        assertThrows(IllegalArgumentException.class,()->new NiftiReadLimits(1,1,1,351,1,10));
        assertThrows(IllegalArgumentException.class,()->new NiftiReadLimits(1,1,1,352,1,9));
        assertThrows(IllegalArgumentException.class,()->new NiftiReadLimits(1,1,Integer.MAX_VALUE-7L,352,1,10));
        assertDoesNotThrow(()->new NiftiReadLimits(Long.MAX_VALUE,Long.MAX_VALUE,Integer.MAX_VALUE-8L,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE));
    }
    @Test void T02_resultStructuralContract() {
        var failures=new ArrayList<>(List.of(NiftiReadFailure.CLOSE_FAILED,NiftiReadFailure.READ_FAILED,NiftiReadFailure.READ_FAILED));
        var r=new NiftiReadResult(source,Optional.empty(),failures,List.of(NiftiReadFinding.WEAK_SOURCE_IDENTITY,NiftiReadFinding.INVALID_SFORM_IGNORED,NiftiReadFinding.WEAK_SOURCE_IDENTITY));failures.clear();
        assertEquals(List.of(NiftiReadFailure.READ_FAILED,NiftiReadFailure.CLOSE_FAILED),r.failures());
        assertEquals(List.of(NiftiReadFinding.INVALID_SFORM_IGNORED,NiftiReadFinding.WEAK_SOURCE_IDENTITY),r.findings());
        assertThrows(UnsupportedOperationException.class,()->r.failures().clear());assertThrows(UnsupportedOperationException.class,()->r.findings().clear());
        var v=Optional.of(volume());
        assertThrows(IllegalArgumentException.class,()->new NiftiReadResult(source,Optional.empty(),List.of(),List.of()));
        assertThrows(IllegalArgumentException.class,()->new NiftiReadResult(source,v,List.of(NiftiReadFailure.READ_FAILED),List.of()));
        assertThrows(IllegalArgumentException.class,()->new NiftiReadResult(source,v,List.of(),List.of()));
        assertThrows(IllegalArgumentException.class,()->new NiftiReadResult(source,v,List.of(),List.of(NiftiReadFinding.SFORM_SELECTED,NiftiReadFinding.QFORM_SELECTED)));
        assertDoesNotThrow(()->new NiftiReadResult(source,v,List.of(),List.of(NiftiReadFinding.SFORM_SELECTED)));
        assertThrows(IllegalArgumentException.class,()->new NiftiReadResult(null,v,List.of(),List.of(NiftiReadFinding.SFORM_SELECTED)));
        assertThrows(IllegalArgumentException.class,()->new NiftiReadResult(source,null,List.of(),List.of()));
        assertThrows(IllegalArgumentException.class,()->new NiftiReadResult(source,v,null,List.of()));
        assertThrows(IllegalArgumentException.class,()->new NiftiReadResult(source,v,List.of(),null));
        assertThrows(IllegalArgumentException.class,()->new NiftiReadResult(source,Optional.empty(),Arrays.asList((NiftiReadFailure)null),List.of()));
        assertThrows(IllegalArgumentException.class,()->new NiftiReadResult(source,Optional.empty(),List.of(NiftiReadFailure.READ_FAILED),Arrays.asList((NiftiReadFinding)null)));
    }
    @Test void T03_frozenApi() throws Exception {
        assertArrayEquals(new String[]{"maxEncodedBytes","maxDecodedBytes","maxVoxels","maxPrefixBytes","maxWorkingBytes","maxGzipHeaderBytes"},Arrays.stream(NiftiReadLimits.class.getRecordComponents()).map(c->c.getName()).toArray(String[]::new));
        assertArrayEquals(new String[]{"source","volume","failures","findings"},Arrays.stream(NiftiReadResult.class.getRecordComponents()).map(c->c.getName()).toArray(String[]::new));
        assertEquals(1,NiftiVolumeReader.class.getDeclaredMethods().length);assertEquals(NiftiReadResult.class,NiftiVolumeReader.class.getMethod("read",RelativePath.class).getReturnType());
        assertEquals("INPUT_UNAVAILABLE,PATH_OUTSIDE_ROOT,SYMLINK_DISALLOWED,SPECIAL_FILE_UNSUPPORTED,CONTAINMENT_UNPROVEN,SOURCE_CHANGED,UNSUPPORTED_VARIANT,UNSUPPORTED_DIMENSIONALITY,UNSUPPORTED_DATATYPE,UNSUPPORTED_INTENT,INVALID_HEADER,INVALID_SCALING,UNSUPPORTED_SPATIAL_UNITS,GEOMETRY_UNAVAILABLE,TRANSFORM_DISAGREEMENT,INVALID_EXTENSION,TRUNCATED_INPUT,TRAILING_DATA,GZIP_INTEGRITY_FAILED,RESOURCE_LIMIT,READ_FAILED,INTERRUPTED,CLOSE_FAILED",String.join(",",Arrays.stream(NiftiReadFailure.values()).map(Enum::name).toList()));
        assertEquals("SFORM_SELECTED,QFORM_SELECTED,INVALID_SFORM_IGNORED,INVALID_QFORM_IGNORED,QFAC_ZERO_AS_POSITIVE,QUATERNION_ROUNDING_NORMALIZED,EXTENSIONS_NOT_INTERPRETED,WEAK_SOURCE_IDENTITY",String.join(",",Arrays.stream(NiftiReadFinding.values()).map(Enum::name).toList()));
    }
}
