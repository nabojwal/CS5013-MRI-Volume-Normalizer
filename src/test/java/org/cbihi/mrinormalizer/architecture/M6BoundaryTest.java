package org.cbihi.mrinormalizer.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.stream.Stream;

import org.cbihi.mrinormalizer.application.request.DicomSeriesRequest;
import org.cbihi.mrinormalizer.application.result.DicomProcessingResult;
import org.cbihi.mrinormalizer.domain.model.DicomInstance;
import org.cbihi.mrinormalizer.domain.model.ImageVolume;
import org.cbihi.mrinormalizer.domain.model.PixelEncoding;
import org.cbihi.mrinormalizer.domain.model.ScalarType;
import org.cbihi.mrinormalizer.domain.model.VoxelData;
import org.cbihi.mrinormalizer.domain.port.DicomInstanceReader;
import org.junit.jupiter.api.Test;

class M6BoundaryTest {
    @Test
    void m6DomainAndApplicationApisShouldNotExposeOuterFrameworkTypes() {
        Class<?>[] types = {DicomInstance.class, ImageVolume.class, PixelEncoding.class, ScalarType.class,
                VoxelData.class, DicomInstanceReader.class, DicomSeriesRequest.class, DicomProcessingResult.class};
        for (Class<?> type : types) {
            assertTrue(referencedTypes(type).allMatch(this::isAllowed), type.getName());
        }
    }

    @Test
    void genericVolumeBoundaryShouldNotExposeDicomPixelEncoding() {
        assertTrue(Stream.of(VoxelData.class.getDeclaredMethods())
                .noneMatch(method -> method.getReturnType() == PixelEncoding.class
                        || Stream.of(method.getParameterTypes()).anyMatch(type -> type == PixelEncoding.class)));
        assertTrue(referencedTypes(ImageVolume.class).noneMatch(type -> type == PixelEncoding.class));
    }

    private boolean isAllowed(Class<?> type) {
        String packageName = type.getPackageName();
        assertFalse(packageName.startsWith("org.dcm4che3"));
        assertFalse(packageName.startsWith("javax.swing"));
        assertFalse(packageName.startsWith("com.formdev"));
        assertFalse(packageName.startsWith("org.cbihi.mrinormalizer.infrastructure"));
        return !packageName.startsWith("java.io");
    }

    private Stream<Class<?>> referencedTypes(Class<?> type) {
        Stream<Class<?>> fields = Stream.of(type.getDeclaredFields()).map(Field::getType);
        Stream<Class<?>> methods = Stream.of(type.getDeclaredMethods())
                .flatMap(method -> Stream.concat(Stream.of(method.getReturnType()), Stream.of(method.getParameterTypes())));
        Stream<Class<?>> constructors = Stream.of(type.getDeclaredConstructors())
                .flatMap(constructor -> Stream.of(constructor.getParameterTypes()));
        return Stream.concat(Stream.concat(fields, methods), constructors).filter(value -> value != type);
    }
}
