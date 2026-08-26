# M5 Format Detection

Format detection is a synchronous application operation. `FormatDetectionService` accepts an `InputSource` and returns the domain `DetectionResult`.

Detection outcomes are `DICOM`, `NIFTI`, `NIFTI_GZ`, `UNKNOWN`, and `CORRUPT`. Content inspection takes precedence over filename extensions; extensions are recorded only as mismatch hints.

`CORRUPT` means recognizable supported-format structure exists but structural validation fails. Random or unsupported bytes remain `UNKNOWN` with a stable diagnostic. Missing paths, directories, unreadable inputs, and zero-byte inputs also remain `UNKNOWN` with diagnostics.

DICOM recognition uses dcm4che-core without metadata extraction for application use, series grouping, slice ordering, volume construction, or conversion. NIfTI recognition uses bounded Java standard-library header inspection for NIfTI-1, NIfTI-2, and gzip-wrapped single-file NIfTI. It does not load voxels, reconstruct affine transforms, or resolve image pairs.

No Swing or GUI behavior belongs to M5.

## Detection Budget

Detection uses bounded inspection. DICOM input larger than 1 MiB is returned as `UNKNOWN` with `INPUT_TOO_LARGE`; this is a detection-budget policy, not a claim that the DICOM object is unsupported or corrupt. Full parsing of large DICOM objects and series is deferred to M6 and later workflows.