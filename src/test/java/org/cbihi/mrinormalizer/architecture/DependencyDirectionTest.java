package org.cbihi.mrinormalizer.architecture;

import java.lang.reflect.Field;
import java.util.stream.Stream;

import org.cbihi.mrinormalizer.domain.model.InputSource;
import org.cbihi.mrinormalizer.domain.model.OutputTarget;
import org.cbihi.mrinormalizer.domain.model.Volume;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class DependencyDirectionTest {

    @Test
    void domainContractsShouldNotDependOnOuterLayersOrInfrastructure() {
        Class<?>[] domainTypes = {InputSource.class, OutputTarget.class, Volume.class};

        for (Class<?> domainType : domainTypes) {
            referencedTypes(domainType).forEach(referencedType -> {
                String packageName = referencedType.getPackageName();
                assertFalse(packageName.startsWith("org.cbihi.mrinormalizer.application"));
                assertFalse(packageName.startsWith("org.cbihi.mrinormalizer.infrastructure"));
                assertFalse(packageName.startsWith("org.cbihi.mrinormalizer.presentation"));
                assertFalse(packageName.startsWith("javax.swing"));
                assertFalse(packageName.startsWith("com.formdev"));
                assertFalse(packageName.startsWith("org.dcm4che3"));
            });
        }
    }

    @Test
    void domainContractsShouldDependOnlyOnDomainOrJavaTypes() {
        Class<?>[] domainTypes = {InputSource.class, OutputTarget.class, Volume.class};

        for (Class<?> domainType : domainTypes) {
            assertTrue(referencedTypes(domainType).allMatch(referencedType ->
                    referencedType.getPackageName().startsWith("java.")
                            || referencedType.getPackageName().startsWith("org.cbihi.mrinormalizer.domain")));
        }
    }

    private Stream<Class<?>> referencedTypes(Class<?> type) {
        Stream<Class<?>> fields = Stream.of(type.getDeclaredFields()).map(Field::getType);
        Stream<Class<?>> methods = Stream.of(type.getDeclaredMethods())
                .flatMap(method -> Stream.concat(
                        Stream.of(method.getReturnType()),
                        Stream.of(method.getParameterTypes())));
        Stream<Class<?>> constructors = Stream.of(type.getDeclaredConstructors())
                .flatMap(constructor -> Stream.of(constructor.getParameterTypes()));
        return Stream.concat(Stream.concat(fields, methods), constructors)
                .filter(referencedType -> referencedType != type);
    }
}
