package org.cbihi.mrinormalizer.infrastructure.dicom;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import org.cbihi.mrinormalizer.domain.error.DicomProcessingError;
import org.cbihi.mrinormalizer.domain.error.DicomProcessingException;
import org.cbihi.mrinormalizer.domain.model.DicomInstance;
import org.cbihi.mrinormalizer.domain.model.ImmutableVoxelData;
import org.cbihi.mrinormalizer.domain.model.InputSource;
import org.cbihi.mrinormalizer.domain.model.PixelEncoding;
import org.cbihi.mrinormalizer.domain.model.PixelValueType;
import org.cbihi.mrinormalizer.domain.model.RescaleTransform;
import org.cbihi.mrinormalizer.domain.model.ScalarType;
import org.cbihi.mrinormalizer.domain.model.SliceGeometry;
import org.cbihi.mrinormalizer.domain.port.DicomInstanceReader;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.io.DicomInputStream;

/** dcm4che adapter for the deliberately narrow M6 conventional-MR profile. */
public final class Dcm4cheInstanceReader implements DicomInstanceReader {

    @Override
    public DicomInstance read(InputSource input) {
        Path path = checkedPath(input);
        boolean part10 = hasPart10Preamble(path);
        try (DicomInputStream stream = new DicomInputStream(Files.newInputStream(path))) {
            Attributes fileMeta = stream.readFileMetaInformation();
            Attributes dataset = stream.readDatasetUntilPixelData();
            String transferSyntax = stream.getTransferSyntax();
            if (dataset.getString(Tag.SOPClassUID) == null && !part10) {
                throw new DicomProcessingException(DicomProcessingError.NOT_DICOM, "Input is not a DICOM object");
            }
            validateScope(dataset, fileMeta, transferSyntax);
            return toDomain(dataset, transferSyntax, path);
        } catch (DicomProcessingException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new DicomProcessingException(part10 ? DicomProcessingError.DICOM_PARSE_FAILED
                    : DicomProcessingError.NOT_DICOM, "DICOM input cannot be parsed", exception);
        }
    }

    private DicomInstance toDomain(Attributes dataset, String transferSyntax, Path path) {
        int rows = requiredInt(dataset, Tag.Rows);
        int columns = requiredInt(dataset, Tag.Columns);
        double[] spacing = requiredDoubles(dataset, Tag.PixelSpacing, 2, DicomProcessingError.MISSING_REQUIRED_METADATA);
        double[] orientation = requiredDoubles(dataset, Tag.ImageOrientationPatient, 6, DicomProcessingError.INVALID_ORIENTATION);
        double[] position = requiredDoubles(dataset, Tag.ImagePositionPatient, 3, DicomProcessingError.INVALID_POSITION);
        double[] columnIndexDirection = vector(orientation, 0);
        double[] rowIndexDirection = vector(orientation, 3);
        double[] normal = normalizedCross(columnIndexDirection, rowIndexDirection);
        SliceGeometry geometry = new SliceGeometry(position, columnIndexDirection, rowIndexDirection, normal,
                dot(position, normal));
        PixelEncoding encoding = pixelEncoding(dataset);
        RescaleTransform rescale = rescaleTransform(dataset);
        ImmutableVoxelData pixels = decodePixels(path, transferSyntax, rows, columns, encoding);
        return new DicomInstance(requiredText(dataset, Tag.SOPInstanceUID), requiredText(dataset, Tag.StudyInstanceUID),
                requiredText(dataset, Tag.SeriesInstanceUID), requiredText(dataset, Tag.Modality),
                requiredText(dataset, Tag.SOPClassUID), transferSyntax, rows, columns, spacing[0], spacing[1],
                geometry, encoding, rescale, pixels,
                dataset.getString(Tag.FrameOfReferenceUID), dataset.getString(Tag.AnatomicalOrientationType),
                optionalSpacing(dataset, Tag.SpacingBetweenSlices), optionalSpacing(dataset, Tag.SliceThickness));
    }

    private Double optionalSpacing(Attributes dataset, int tag) {
        if (!dataset.contains(tag)) {
            return null;
        }
        // NaN represents present-but-invalid metadata, never absence. Policy belongs to the service.
        try {
            return dataset.getDouble(tag, Double.NaN);
        } catch (NumberFormatException exception) {
            return Double.NaN;
        }
    }

    private void validateScope(Attributes dataset, Attributes fileMeta, String transferSyntax) {
        String sopClass = dataset.getString(Tag.SOPClassUID);
        if (!UID.MRImageStorage.equals(sopClass) || !"MR".equals(dataset.getString(Tag.Modality))
                || dataset.getInt(Tag.NumberOfFrames, 1) != 1) {
            throw new DicomProcessingException(DicomProcessingError.UNSUPPORTED_OBJECT_TYPE,
                    "Only conventional single-frame MR images are supported");
        }
        if (!isSupportedTransferSyntax(transferSyntax)) {
            throw new DicomProcessingException(DicomProcessingError.UNSUPPORTED_TRANSFER_SYNTAX,
                    "DICOM transfer syntax is outside the M6 profile");
        }
        if (fileMeta != null) {
            String metaSopClass = fileMeta.getString(Tag.MediaStorageSOPClassUID);
            String metaSopInstance = fileMeta.getString(Tag.MediaStorageSOPInstanceUID);
            if ((metaSopClass != null && !metaSopClass.equals(sopClass))
                    || (metaSopInstance != null && !metaSopInstance.equals(dataset.getString(Tag.SOPInstanceUID)))) {
                throw new DicomProcessingException(DicomProcessingError.DICOM_PARSE_FAILED,
                        "File Meta Information is inconsistent with the dataset");
            }
        }
    }

    private boolean isSupportedTransferSyntax(String value) {
        return UID.ImplicitVRLittleEndian.equals(value) || UID.ExplicitVRLittleEndian.equals(value)
                || UID.ExplicitVRBigEndian.equals(value);
    }

    private PixelEncoding pixelEncoding(Attributes dataset) {
        int representation = requiredInt(dataset, Tag.PixelRepresentation);
        PixelValueType valueType = switch (representation) {
            case 0 -> PixelValueType.UNSIGNED_INTEGER;
            case 1 -> PixelValueType.SIGNED_TWOS_COMPLEMENT;
            default -> throw new DicomProcessingException(DicomProcessingError.UNSUPPORTED_PIXEL_REPRESENTATION,
                    "Pixel Representation is unsupported");
        };
        try {
            return new PixelEncoding(requiredInt(dataset, Tag.BitsAllocated), requiredInt(dataset, Tag.BitsStored),
                    requiredInt(dataset, Tag.HighBit), valueType, requiredInt(dataset, Tag.SamplesPerPixel),
                    requiredText(dataset, Tag.PhotometricInterpretation));
        } catch (IllegalArgumentException exception) {
            throw new DicomProcessingException(DicomProcessingError.UNSUPPORTED_PIXEL_REPRESENTATION,
                    "DICOM pixel encoding is outside the M6 profile", exception);
        }
    }

    private RescaleTransform rescaleTransform(Attributes dataset) {
        boolean hasSlope = dataset.containsValue(Tag.RescaleSlope);
        boolean hasIntercept = dataset.containsValue(Tag.RescaleIntercept);
        if (hasSlope != hasIntercept) {
            throw new DicomProcessingException(DicomProcessingError.MISSING_REQUIRED_METADATA,
                    "Rescale slope and intercept must be supplied together");
        }
        if (!hasSlope) {
            return RescaleTransform.identityNotDeclared();
        }
        try {
            return new RescaleTransform(true, dataset.getDouble(Tag.RescaleSlope, Double.NaN),
                    dataset.getDouble(Tag.RescaleIntercept, Double.NaN));
        } catch (IllegalArgumentException exception) {
            throw new DicomProcessingException(DicomProcessingError.MISSING_REQUIRED_METADATA,
                    "Rescale metadata is invalid", exception);
        }
    }

    private ImmutableVoxelData decodePixels(Path path, String transferSyntax, int rows, int columns,
                                            PixelEncoding encoding) {
        try (DicomInputStream stream = new DicomInputStream(Files.newInputStream(path))) {
            Attributes dataset = stream.readDataset();
            byte[] bytes = dataset.getBytes(Tag.PixelData);
            if (bytes == null) {
                throw new DicomProcessingException(DicomProcessingError.PIXEL_DATA_UNAVAILABLE,
                        "Pixel Data is unavailable");
            }
            long samples = Math.multiplyExact((long) rows, columns);
            int bytesPerSample = encoding.bitsAllocated() / Byte.SIZE;
            long expectedLength = samples * bytesPerSample;
            boolean padded = expectedLength % 2 == 1 && bytes.length == expectedLength + 1;
            if (expectedLength > Integer.MAX_VALUE || (!padded && bytes.length != expectedLength)) {
                throw new DicomProcessingException(DicomProcessingError.PIXEL_DATA_UNAVAILABLE,
                        "Pixel Data length does not match the encoded image");
            }
            ByteOrder order = UID.ExplicitVRBigEndian.equals(transferSyntax)
                    ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
            ByteBuffer buffer = ByteBuffer.wrap(bytes).order(order);
            long[] values = new long[(int) samples];
            for (int index = 0; index < values.length; index++) {
                long cell = encoding.bitsAllocated() == 8 ? Byte.toUnsignedInt(buffer.get())
                        : Short.toUnsignedInt(buffer.getShort());
                values[index] = decodeStored(cell, encoding);
            }
            return new ImmutableVoxelData(columns, rows, 1, scalarType(encoding), values);
        } catch (DicomProcessingException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new DicomProcessingException(DicomProcessingError.PIXEL_DATA_UNAVAILABLE,
                    "Pixel Data cannot be decoded", exception);
        }
    }

    private ScalarType scalarType(PixelEncoding encoding) {
        if (encoding.bitsAllocated() == 8) {
            return encoding.valueType() == PixelValueType.UNSIGNED_INTEGER ? ScalarType.UINT8 : ScalarType.INT8;
        }
        if (encoding.bitsAllocated() == 16) {
            return encoding.valueType() == PixelValueType.UNSIGNED_INTEGER ? ScalarType.UINT16 : ScalarType.INT16;
        }
        throw new DicomProcessingException(DicomProcessingError.UNSUPPORTED_PIXEL_REPRESENTATION,
                "DICOM pixel encoding cannot be represented by the generic scalar model");
    }

    private long decodeStored(long cell, PixelEncoding encoding) {
        long mask = (1L << encoding.bitsStored()) - 1L;
        long value = cell & mask;
        if (encoding.valueType() == PixelValueType.SIGNED_TWOS_COMPLEMENT) {
            long signBit = 1L << (encoding.bitsStored() - 1);
            if ((value & signBit) != 0) {
                value |= ~mask;
            }
        }
        return value;
    }

    private Path checkedPath(InputSource input) {
        if (input == null || input.reference() == null) {
            throw new DicomProcessingException(DicomProcessingError.INPUT_NOT_FOUND, "Input reference is missing");
        }
        try {
            Path path = Path.of(input.reference());
            if (!Files.exists(path)) {
                throw new DicomProcessingException(DicomProcessingError.INPUT_NOT_FOUND, "Input does not exist");
            }
            if (Files.isDirectory(path) || !Files.isReadable(path)) {
                throw new DicomProcessingException(DicomProcessingError.INPUT_NOT_READABLE, "Input is not readable");
            }
            if (Files.size(path) == 0) {
                throw new DicomProcessingException(DicomProcessingError.EMPTY_INPUT, "Input is empty");
            }
            return path;
        } catch (DicomProcessingException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new DicomProcessingException(DicomProcessingError.INPUT_NOT_READABLE,
                    "Input cannot be inspected", exception);
        }
    }

    private boolean hasPart10Preamble(Path path) {
        try (var input = Files.newInputStream(path)) {
            byte[] prefix = input.readNBytes(132);
            return prefix.length == 132 && prefix[128] == 'D' && prefix[129] == 'I'
                    && prefix[130] == 'C' && prefix[131] == 'M';
        } catch (IOException exception) {
            return false;
        }
    }

    private int requiredInt(Attributes attributes, int tag) {
        if (!attributes.containsValue(tag)) {
            throw new DicomProcessingException(DicomProcessingError.MISSING_REQUIRED_METADATA,
                    "Required DICOM integer metadata is missing");
        }
        return attributes.getInt(tag, 0);
    }

    private String requiredText(Attributes attributes, int tag) {
        String value = attributes.getString(tag);
        if (value == null || value.isBlank()) {
            throw new DicomProcessingException(DicomProcessingError.MISSING_REQUIRED_METADATA,
                    "Required DICOM text metadata is missing");
        }
        return value;
    }

    private double[] requiredDoubles(Attributes attributes, int tag, int count, DicomProcessingError error) {
        double[] values = attributes.getDoubles(tag);
        if (values == null || values.length != count) {
            throw new DicomProcessingException(DicomProcessingError.MISSING_REQUIRED_METADATA,
                    "Required DICOM decimal metadata is missing");
        }
        for (double value : values) {
            if (!Double.isFinite(value)) {
                throw new DicomProcessingException(error, "DICOM spatial metadata is not finite");
            }
        }
        return values;
    }

    private double[] vector(double[] values, int offset) {
        return new double[] {values[offset], values[offset + 1], values[offset + 2]};
    }

    private double[] normalizedCross(double[] first, double[] second) {
        double[] cross = new double[] {first[1] * second[2] - first[2] * second[1],
                first[2] * second[0] - first[0] * second[2], first[0] * second[1] - first[1] * second[0]};
        double length = Math.sqrt(dot(cross, cross));
        if (!Double.isFinite(length) || length == 0.0d) {
            throw new DicomProcessingException(DicomProcessingError.INVALID_ORIENTATION,
                    "Image Orientation Patient has no slice normal");
        }
        return new double[] {cross[0] / length, cross[1] / length, cross[2] / length};
    }

    private double dot(double[] first, double[] second) {
        return first[0] * second[0] + first[1] * second[1] + first[2] * second[2];
    }
}
