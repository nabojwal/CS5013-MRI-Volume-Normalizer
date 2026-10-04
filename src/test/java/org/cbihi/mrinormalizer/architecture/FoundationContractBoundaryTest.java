package org.cbihi.mrinormalizer.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.cbihi.mrinormalizer.application.dataset.model.AssessmentReason;
import org.cbihi.mrinormalizer.application.dataset.model.ConversionReadiness;
import org.cbihi.mrinormalizer.application.dataset.model.FormatAssessment;
import org.cbihi.mrinormalizer.application.dataset.model.FormatVariant;
import org.cbihi.mrinormalizer.application.dataset.model.SupportStatus;
import org.cbihi.mrinormalizer.application.dataset.model.ValidityStatus;
import org.cbihi.mrinormalizer.domain.model.DetectionDiagnostic;
import org.cbihi.mrinormalizer.domain.model.DetectionOutcome;
import org.cbihi.mrinormalizer.domain.model.DetectionResult;
import org.cbihi.mrinormalizer.domain.model.ImagingFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/** Reflective F0 public-boundary checks; exhaustive factory behavior remains in the accepted S2 tests. */
class FoundationContractBoundaryTest {

    private static final List<Class<?>> MODEL_TYPES = List.of(FormatAssessment.class, FormatVariant.class,
            ValidityStatus.class, SupportStatus.class, ConversionReadiness.class, AssessmentReason.class);

    // B01: inspect actual declarations, including generic arguments and declared exceptions.
    @Test
    void publicBoundaryReferencesOnlyApprovedValueTypes() {
        for (var model : MODEL_TYPES) {
            assertEquals(0, model.getTypeParameters().length, model.getName());
            assertApprovedType(model.getGenericSuperclass(), false);
            for (var type : model.getGenericInterfaces()) {
                assertApprovedType(type, false);
            }
            if (model.isRecord()) {
                for (var component : model.getRecordComponents()) {
                    assertApprovedType(component.getGenericType(), false);
                }
            }
            for (var field : model.getDeclaredFields()) {
                if (Modifier.isPublic(field.getModifiers())) {
                    assertTrue(field.isEnumConstant(), "Only enum constants may be public fields");
                    assertApprovedType(field.getGenericType(), false);
                }
            }
            for (var constructor : model.getConstructors()) {
                assertEquals(0, constructor.getTypeParameters().length);
                for (var type : constructor.getGenericParameterTypes()) {
                    assertApprovedType(type, false);
                }
                assertEquals(0, constructor.getGenericExceptionTypes().length,
                        "Value constructors must not expose exception payload contracts");
            }
            for (var method : model.getDeclaredMethods()) {
                if (Modifier.isPublic(method.getModifiers())) {
                    assertEquals(0, method.getTypeParameters().length);
                    boolean generatedLanguageMethod = isGeneratedLanguageMethod(model, method);
                    assertApprovedType(method.getGenericReturnType(), generatedLanguageMethod);
                    for (var type : method.getGenericParameterTypes()) {
                        assertApprovedType(type, generatedLanguageMethod);
                    }
                    assertEquals(0, method.getGenericExceptionTypes().length,
                            "Value methods must not expose exception payload contracts");
                }
            }
        }
    }

    // B02: exact approved enum members, without introducing an alternative readiness representation.
    @Test
    void readinessAndReasonMembersRemainExactlyApproved() {
        assertEquals(Set.of("REQUIRES_VALIDATION", "BLOCKED", "READY"),
                enumNames(ConversionReadiness.class));
        assertEquals(Set.of("FORMAT_NOT_RECOGNIZED", "DETECTION_NOT_COMPLETED", "DETECTION_INCONCLUSIVE",
                "INPUT_RECOGNIZED_AS_CORRUPT", "VALIDATION_NOT_PERFORMED", "UNSUPPORTED_FORMAT_VARIANT",
                "UNSUPPORTED_PROFILE", "VALIDATION_FAILED"), enumNames(AssessmentReason.class));
    }

    // B02: component names, raw and generic types, and the sole canonical public constructor.
    @Test
    void assessmentRecordAndCanonicalConstructorRemainFrozen() throws ReflectiveOperationException {
        assertTrue(FormatAssessment.class.isRecord());
        var components = FormatAssessment.class.getRecordComponents();
        assertEquals(List.of("initialDetection", "format", "variant", "validity", "support", "readiness",
                "reasons"), Arrays.stream(components).map(component -> component.getName()).toList());
        var types = new Class<?>[] {DetectionResult.class, ImagingFormat.class, FormatVariant.class,
                ValidityStatus.class, SupportStatus.class, ConversionReadiness.class, List.class};
        assertEquals(Arrays.asList(types), Arrays.stream(components).map(component -> component.getType()).toList());
        for (int index = 0; index < components.length - 1; index++) {
            assertEquals(types[index], components[index].getGenericType());
        }
        assertReasonListType(components[6].getGenericType());
        var constructor = FormatAssessment.class.getConstructor(types);
        assertEquals(List.of(constructor), Arrays.asList(FormatAssessment.class.getConstructors()));
        assertEquals(Modifier.PUBLIC, constructor.getModifiers());
        assertTrue(!constructor.isVarArgs());
        assertReasonListType(constructor.getGenericParameterTypes()[6]);
    }

    // B02: exact declarations prevent setters, alternate factories, upgrade/bypass and processing APIs.
    @Test
    void publicDeclarationsRemainOnlyApprovedValueApis() throws ReflectiveOperationException {
        for (var model : MODEL_TYPES) {
            assertEquals("org.cbihi.mrinormalizer.application.dataset.model", model.getPackageName());
            assertTrue(Modifier.isPublic(model.getModifiers()) && Modifier.isFinal(model.getModifiers()));
            Set<Method> expected = new HashSet<>();
            if (model == FormatAssessment.class) {
                for (var component : model.getRecordComponents()) {
                    var accessor = component.getAccessor();
                    assertEquals(Modifier.PUBLIC, accessor.getModifiers());
                    assertEquals(component.getGenericType(), accessor.getGenericReturnType());
                    expected.add(accessor);
                }
                expected.add(model.getDeclaredMethod("equals", Object.class));
                expected.add(model.getDeclaredMethod("hashCode"));
                expected.add(model.getDeclaredMethod("toString"));
                var factory = model.getDeclaredMethod("fromDetection", DetectionResult.class);
                assertEquals(Modifier.PUBLIC | Modifier.STATIC, factory.getModifiers());
                assertEquals(FormatAssessment.class, factory.getGenericReturnType());
                assertEquals(List.of(DetectionResult.class), Arrays.asList(factory.getGenericParameterTypes()));
                assertTrue(!factory.isVarArgs());
                expected.add(factory);
                assertEquals(0, model.getFields().length);
            } else {
                assertTrue(model.isEnum());
                assertEquals(0, model.getConstructors().length);
                var values = model.getDeclaredMethod("values");
                var valueOf = model.getDeclaredMethod("valueOf", String.class);
                assertEquals(Modifier.PUBLIC | Modifier.STATIC, values.getModifiers());
                assertEquals(Modifier.PUBLIC | Modifier.STATIC, valueOf.getModifiers());
                assertTrue(values.getReturnType().isArray());
                assertEquals(model, values.getReturnType().getComponentType());
                assertEquals(model, valueOf.getReturnType());
                expected.add(values);
                expected.add(valueOf);
                assertEquals(model.getEnumConstants().length, model.getFields().length);
            }
            Set<Method> actual = new HashSet<>();
            for (var method : model.getDeclaredMethods()) {
                if (Modifier.isPublic(method.getModifiers())) {
                    actual.add(method);
                }
            }
            assertEquals(expected, actual, model.getName());
        }
    }

    // B01/B02: fixed contract messages, no path/free-text input channel, and no propagated cause payload.
    @Test
    void constructorAndFactoryFailuresUseFixedNonIdentifyingMessages() {
        assertFixedFailure("Detection must be non-null", () -> FormatAssessment.fromDetection(null));
        assertFixedFailure("Raw detection components must be non-null",
                () -> FormatAssessment.fromDetection(new DetectionResult(null, DetectionDiagnostic.NONE, false)));
        assertFixedFailure("Assessment components must be non-null", () -> new FormatAssessment(null,
                ImagingFormat.DICOM, FormatVariant.DICOM_UNSPECIFIED, ValidityStatus.NOT_ASSESSED,
                SupportStatus.NOT_ASSESSED, ConversionReadiness.REQUIRES_VALIDATION,
                List.of(AssessmentReason.VALIDATION_NOT_PERFORMED)));
        for (boolean mismatch : new boolean[] {false, true}) {
            var malformed = new DetectionResult(DetectionOutcome.UNKNOWN, DetectionDiagnostic.INVALID_DICOM,
                    mismatch);
            assertFixedFailure("Raw detection outcome and diagnostic are inconsistent",
                    () -> FormatAssessment.fromDetection(malformed));
            assertFixedFailure("Raw detection outcome and diagnostic are inconsistent",
                    () -> new FormatAssessment(malformed, ImagingFormat.UNKNOWN, FormatVariant.UNDETERMINED,
                            ValidityStatus.NOT_ASSESSED, SupportStatus.NOT_ASSESSED, ConversionReadiness.BLOCKED,
                            List.of(AssessmentReason.DETECTION_NOT_COMPLETED)));
        }
        var recognized = DetectionResult.identified(DetectionOutcome.DICOM);
        assertFixedFailure("Reasons must be non-null and unique", () -> new FormatAssessment(recognized,
                ImagingFormat.DICOM, FormatVariant.DICOM_UNSPECIFIED, ValidityStatus.NOT_ASSESSED,
                SupportStatus.NOT_ASSESSED, ConversionReadiness.REQUIRES_VALIDATION,
                List.of(AssessmentReason.VALIDATION_NOT_PERFORMED, AssessmentReason.VALIDATION_NOT_PERFORMED)));
        assertFixedFailure("Recognized family cannot be rewritten", () -> new FormatAssessment(recognized,
                ImagingFormat.NIFTI, FormatVariant.NIFTI_1_SINGLE_FILE, ValidityStatus.NOT_ASSESSED,
                SupportStatus.NOT_ASSESSED, ConversionReadiness.REQUIRES_VALIDATION,
                List.of(AssessmentReason.VALIDATION_NOT_PERFORMED)));
        assertFixedFailure("Ready assessment requires valid supported evidence and empty reasons",
                () -> new FormatAssessment(recognized, ImagingFormat.DICOM, FormatVariant.DICOM_UNSPECIFIED,
                        ValidityStatus.NOT_ASSESSED, SupportStatus.NOT_ASSESSED, ConversionReadiness.READY, List.of()));
        assertFixedFailure("Reason does not apply to assessment state", () -> new FormatAssessment(recognized,
                ImagingFormat.DICOM, FormatVariant.DICOM_UNSPECIFIED, ValidityStatus.NOT_ASSESSED,
                SupportStatus.NOT_ASSESSED, ConversionReadiness.REQUIRES_VALIDATION,
                List.of(AssessmentReason.VALIDATION_NOT_PERFORMED, AssessmentReason.UNSUPPORTED_PROFILE)));
    }

    private static boolean isGeneratedLanguageMethod(Class<?> model, Method method) {
        return model == FormatAssessment.class
                && (method.getName().equals("toString") && method.getParameterCount() == 0
                        || method.getName().equals("equals")
                                && Arrays.equals(method.getParameterTypes(), new Class<?>[] {Object.class}))
                || model.isEnum() && method.getName().equals("valueOf")
                        && Arrays.equals(method.getParameterTypes(), new Class<?>[] {String.class});
    }

    private static void assertApprovedType(Type type, boolean generatedLanguageType) {
        if (type instanceof Class<?> value) {
            if (value.isArray()) {
                assertTrue(MODEL_TYPES.contains(value.getComponentType()) && value.getComponentType().isEnum(),
                        "Only generated enum values arrays are approved: " + value.getTypeName());
            } else {
                assertTrue(MODEL_TYPES.contains(value) || value == DetectionResult.class || value == ImagingFormat.class
                        || value == List.class || value == Record.class || value == Enum.class
                        || value == int.class || value == boolean.class
                        || generatedLanguageType && (value == String.class || value == Object.class),
                        "Unapproved boundary type: " + value.getTypeName());
            }
        } else if (type instanceof ParameterizedType parameterized) {
            assertTrue(parameterized.getRawType() == List.class || parameterized.getRawType() == Enum.class);
            assertNull(parameterized.getOwnerType());
            for (var argument : parameterized.getActualTypeArguments()) {
                assertApprovedType(argument, false);
            }
        } else {
            fail("Unapproved generic boundary type: " + type.getTypeName());
        }
    }

    private static void assertReasonListType(Type type) {
        assertTrue(type instanceof ParameterizedType);
        var parameterized = (ParameterizedType) type;
        assertEquals(List.class, parameterized.getRawType());
        assertEquals(List.of(AssessmentReason.class), Arrays.asList(parameterized.getActualTypeArguments()));
        assertNull(parameterized.getOwnerType());
    }

    private static Set<String> enumNames(Class<? extends Enum<?>> type) {
        Set<String> names = new HashSet<>();
        for (var member : type.getEnumConstants()) {
            names.add(member.name());
        }
        return names;
    }

    private static void assertFixedFailure(String message, Executable operation) {
        var exception = assertThrowsExactly(IllegalArgumentException.class, operation);
        assertEquals(message, exception.getMessage());
        assertNull(exception.getCause());
        assertEquals(0, exception.getSuppressed().length);
    }
}
