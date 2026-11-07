# CS5013 MRI Volume Normalizer

## 1. Overview

A Java 21 research-data utility for IIT Madras CS5013: Programming with AI.

The implemented engine detects DICOM/NIfTI inputs, reconstructs an explicitly
selected supported conventional MR DICOM series into a format-neutral
`ImageVolume`, and converts that volume to NIfTI-1 `.nii` or `.nii.gz` while
preserving the supported stored voxel values and spatial semantics.

Dataset discovery/organization, headless selected-series conversion, bounded
NIfTI-to-derived-DICOM reconstruction and sequential batch orchestration are
implemented in separate, constrained workstreams. A Swing/FlatLaf desktop GUI
now integrates the supported forward and reverse workflows. This is a
non-clinical research and course-submission prototype, not a clinical or PACS product.

### Current integrated submission checkpoint — 2026-11-07

Pull request [#1](https://github.com/nabojwal/CS5013-MRI-Volume-Normalizer/pull/1)
merged `feature/gui-i1-windows` at tested source commit
[`454f8b58275583b0137d03a88d025c13523013eb`](https://github.com/nabojwal/CS5013-MRI-Volume-Normalizer/commit/454f8b58275583b0137d03a88d025c13523013eb)
into `main` as merge commit
[`b032a51cd369b428f201c6b0dd8d23a49f195eec`](https://github.com/nabojwal/CS5013-MRI-Volume-Normalizer/commit/b032a51cd369b428f201c6b0dd8d23a49f195eec).
Both commits have Git tree `223a9c140de14870e8defffbd242a4771bf55df8`:
the merged source files match the tested feature checkpoint. The integration
includes the preceding 65 commits, not only the final GUI correction.

Implemented, bounded capabilities:

- **DICOM → NIfTI:** explicit selection of supported conventional single-frame
  MR DICOM series. One series uses F9; multiple eligible prepared series use
  sequential FBO orchestration. Backend compatibility and final verification
  remain authoritative; GUI discovery is a metadata-only preview.
- **NIfTI → derived MR DICOM:** independent M8 conversion for the supported
  3D integer NIfTI-1 profile (`INT8`, `UINT8`, `INT16`, `UINT16`) and
  supported geometry/units. EBO handles one input or a bounded top-level folder
  sequentially. The GUI requires acknowledgment of non-clinical evaluation
  metadata assumptions (`MR/BIPED`, `SE`, `NONE`); these values are **not**
  recovered acquisition facts.
- **Desktop GUI:** `GuiI1App` and `GuiI1Window` implement source/output
  browsing, candidate inspection, direction selection, conversion dispatch and
  final outcome display. `GuiI1Execution` delegates conversion authority
  to F9, FBO and EBO; a GUI `VERIFIED` result depends on a verified backend
  outcome and completion-record acknowledgment.

**Recorded verification (not executed by this documentation change):**
PR #1 records a **user-provided native Windows clean regression of 961 tests,
0 failures, 0 errors and 0 skips**, plus a passing targeted mixed-series GUI
conversion test. The full run used an **elevated** Windows session, provisioned
filesystem fixtures, `java.io.tmpdir=C:\PT`, and
`d1.test.destination=C:\PD`. Local evidence was retained outside Git.
This is workstation evidence, **not a GitHub-hosted CI run** and not ordinary-user
or cross-platform full-suite qualification.

**Scope and qualification limits:** DICOM support is not universal across SOP
Classes/transfer syntaxes; generalized 4D/floating-point NIfTI, round-trip
fidelity, clinical interoperability and recovery of original acquisition
metadata are not claimed. Windows destination admission is conditional on
filesystem identity, local NTFS qualification, path capability and backend
private-creation/publication checks; arbitrary external media, network/cloud
folders and long paths are not universally qualified. The documented D1
destination-reliability investigation after PR #1 is **not** part of this merged
source checkpoint.

**Acceptance distinction:** F0–F7 retain their recorded bounded acceptance.
F8 and F9 formal acceptance remain pending; M8 has an implemented, separately
qualified Windows evaluation path without blanket formal acceptance. EBO-1 R2
is accepted for its bounded evaluation contract. The GUI is an integrated
submission prototype **without inferring F10/F11 or global Pre-M8 closure**.
The earlier 2026-11-02 EBO checkpoint (`b7739fd`, 888/888) and the 2026-10-03
M7 checkpoint (129 tests) remain historical evidence, not the latest integrated
state. See [milestones](MILESTONES.md),
[foundation tracker](docs/planning/PRE-M8-FOUNDATION-TRACKER.md), and
[test results](docs/testing/TEST-RESULTS.md).

## 2. Core Principles

- detect formats from content rather than trusting extensions;
- reconstruct DICOM slices using validated physical geometry;
- preserve raw stored voxel values;
- preserve rescale slope/intercept as explicit intensity metadata;
- keep DICOM geometry in patient LPS until the conversion boundary;
- perform explicit DICOM LPS to NIfTI RAS affine conversion;
- do not resample, interpolate, flip or reorder voxel arrays in the supported
  DICOM-to-NIfTI path;
- fail explicitly on unsupported data or unsafe output conditions.

Intensity normalization is optional and is not part of the required conversion
MVP.

## 3. Historical M7 Development Status (2026-10-03)

This table preserves the M7-era snapshot reviewed on `feature/m7-dicom-to-nifti`.
The M8/GUI `NOT STARTED` entries below describe that **date only**; consult
§1 and [MILESTONES.md](MILESTONES.md) for the current integrated state.

| Milestone | Description | Status / checkpoint |
|---|---|---|
| M1 | Java/Maven/JUnit baseline | COMPLETE — `63fff6d` |
| M2 | dcm4che integration | COMPLETE — `8e1580b` |
| M3 | FlatLaf foundation | COMPLETE — `cf9013d` |
| M4 | Initial architecture | COMPLETE — `7e50a9b` |
| M5 | Content-based detection | COMPLETE — `88da522` |
| M5.1 | Detection hardening | COMPLETE — `1989589` |
| M6 | DICOM reconstruction | COMPLETE — protected checkpoint `cb7769e` |
| M7-P01 | Format-neutral `ImageVolume` | COMPLETE — `1dd31fc` |
| M7-P02 | DICOM LPS to NIfTI RAS affine | COMPLETE — `8fb2831` |
| M7-P03 | NIfTI-1 writer | COMPLETE — `6924dd5` |
| M7-P04 | End-to-end DICOM to NIfTI | COMPLETE — `a495699` |
| M7-P05A | Safe output and structured failures | COMPLETE — `2bf1927` |
| M7-P05B | Independent NIfTI interoperability validation | VERIFIED |
| ADR-014 | Structured validation report and future JSON manifest | ACCEPTED — `b6bab40` |
| M7-P05C | Conversion validation report | COMPLETE — `0976788` |
| M7 | DICOM-to-NIfTI acceptance | COMPLETE — 2026-10-03 |
| M8 | Controlled NIfTI-to-DICOM | NOT STARTED |
| M10 | GUI integration | NOT STARTED |

At this historical M7 checkpoint, the Java regression baseline contained
129 passing tests with zero failures, errors or skips.

## 4. Supported DICOM Profile

`DefaultDicomSeriesService` accepts explicit file inputs and an explicitly
selected `SeriesInstanceUID`.

Supported initial profile:

- conventional single-frame MR Image Storage;
- Modality `MR`;
- Implicit VR Little Endian;
- Explicit VR Little Endian;
- Explicit VR Big Endian;
- MONOCHROME2;
- one sample per pixel;
- 8-bit or 16-bit allocated integer samples;
- signed and unsigned representations;
- validated BitsStored / HighBit;
- explicit Image Orientation Patient / Image Position Patient geometry;
- validated Pixel Spacing;
- validated FrameOfReferenceUID;
- BIPED patient-coordinate profile;
- validated regular slice spacing.

Physical slice position controls ordering. Filename order and `InstanceNumber`
do not control reconstruction.

Single-slice reconstruction requires valid `SpacingBetweenSlices` or falls back
to valid `SliceThickness`.

## 5. Generic Volume and Spatial Model

`ImageVolume` is the authoritative in-memory image representation.

The generic scalar profile currently supports:

- `UINT8`;
- `INT8`;
- `UINT16`;
- `INT16`.

The established voxel-index convention is:

- x = column index;
- y = row index;
- z = slice index.

DICOM reconstruction remains in patient LPS coordinates.

For NIfTI output:

`A_RAS = diag(-1, -1, 1, 1) * A_LPS`

This changes the world-coordinate convention only. It does not reorder the
stored voxel array.

## 6. NIfTI-1 Output Profile

The project-owned writer supports:

- `.nii`;
- `.nii.gz`;
- NIfTI-1 single-file `n+1` format;
- little-endian header and payload;
- UINT8, INT8, UINT16 and INT16;
- millimetre spatial units;
- explicit intensity scaling metadata;
- sform as the authoritative full affine;
- qform when the affine is representable by the NIfTI quaternion model;
- `qform_code = 0` when exact quaternion representation is not supported;
- no silent overwrite of existing outputs;
- cleanup of newly created partial output after serialization failure.

The writer uses no third-party NIfTI runtime dependency.

## 7. DICOM-to-NIfTI Workflow

The following is the original **M7 application-level path**, not the complete
subsequent F9/FBO/GUI publication pipeline:

The implemented M7 application path is:

```text
explicit DICOM inputs
        |
        v
Dcm4cheInstanceReader
        |
        v
validated DICOM series
        |
        v
ImageVolume in DICOM patient LPS
        |
        v
NiftiAffineMapper
        |
        v
voxel-to-world RAS affine
        |
        v
Nifti1VolumeWriter
        |
        +--> .nii / .nii.gz
        |
        v
writer returns successfully
        |
        v
ConversionValidationReport
```

Output failures are represented separately from DICOM reconstruction errors.

The `ConversionValidationReport` is created only after the NIfTI writer
completes successfully. It records factual conversion invariants and does not
claim that the written output was reopened, reparsed or independently validated
at runtime.

### 7.1 Integrated GUI and authoritative backend workflows

The desktop entry point is
`org.cbihi.mrinormalizer.presentation.gui.GuiI1App` (Java 21 with the
project runtime dependencies). The two tabs are **DICOM → NIfTI** and
**NIfTI → DICOM**. The GUI scans candidate inputs, allows the supported
selection, validates a selected existing output directory, and calls
`GuiI1Execution`.

`GuiI1Execution` routes one DICOM series through `F9WindowsComposition`,
a multi-series DICOM batch through `FboCommand`, and supported NIfTI
single-file/directory input through `EboCommand`. The underlying F9/FBO/EBO
services decide conversion validity and publication; metadata-only preview is
not a proof of executability. Results distinguish `VERIFIED`, `PARTIAL`,
`REJECTED`, `FAILED`, `RECOVERY_REQUIRED` and `INTERRUPTED`.
`RECOVERY_REQUIRED` must not be described as successful output.

For the qualified F9 path, verified publication is coupled to the backend
reopen/compare/provenance workflow, which is distinct from the earlier M7
writer-return `ConversionValidationReport`. A separate GUI completion ledger
prevents identical input/destination replay after acknowledgment. Do not
delete internal evidence, assume a directory is safely reusable, or treat
successful byte writing alone as a verified conversion.

## 8. Verification

Primary verification:

```shell
mvn clean test
```

Latest **reported integrated-source** Windows regression (PR #1,
`454f8b5`; workstation evidence, with the elevated/short-path qualifications
listed in §1):

```text
961 tests passed
0 failures
0 errors
0 skipped
BUILD SUCCESS
```

The previous M7-specific regression was **129/129** at the 2026-10-03
historical checkpoint. This documentation update does **not** rerun Maven
or establish GitHub CI success.

Useful focused commands:

```shell
mvn "-Dtest=DicomSeriesServiceTest" test
mvn "-Dtest=Nifti1VolumeWriterTest,Nifti1VolumeWriterHardeningTest" test
mvn "-Dtest=DicomToNiftiServiceTest,DicomToNiftiServiceHardeningTest" test
mvn "-Dtest=ConversionValidationReportTest" test
mvn "-Dtest=ConversionValidationReportTest,DicomToNiftiServiceTest,DicomToNiftiServiceHardeningTest" test
git diff --check
```

The P05C report-focused suite passes 6 tests, and the combined
report/conversion suite passes 14 tests.

Independent M7 interoperability validation was performed with NiBabel 5.4.2
against Java-generated `.nii` and `.nii.gz` files.

The external reader confirmed:

- shape `(2, 2, 2)`;
- datatype `uint16`;
- voxel spacing `(0.5, 0.75, 2.0)`;
- exact raw voxel-array preservation;
- qform code `1`;
- sform code `1`;
- qform/sform affine geometry within absolute tolerance `1e-5`;
- declared slope `2.5` and intercept `-100`;
- gzip-wrapped NIfTI interoperability;
- expected DICOM-LPS to NIfTI-RAS world mapping.

NiBabel is an external acceptance-validation tool only. It is not a project
runtime dependency.

## 9. Provenance

Successful DICOM reconstruction records a canonical SHA-256 fingerprint derived
from selected SOP Instance UIDs and selected source content, independent of path
and input ordering.

The DICOM-to-NIfTI use case carries reconstruction provenance forward. NIfTI
output failures are represented as failed overall conversion outcomes.

Successful conversion additionally returns an application-level
`ConversionValidationReport` containing the output target, dimensions, scalar
type, voxel count, spacing, NIfTI-RAS affine, intensity transform and the
lossless-path conversion invariants.

For the supported M7 path, stored voxel values are preserved, resampling and
interpolation are false, and voxel ordering is unchanged.

M7 does not persist an additional provenance/validation sidecar. ADR-014
records the M7-era design decision. Later F1 persistence implements versioned
canonical JSON plan/checkpoint schemas, and F9 uses restricted persisted
provenance and reopened-output verification before verified publication.
M8 uses its separately scoped output journal and completion predicate.
These later mechanisms do not retroactively change M7's standalone report
semantics.

## 10. Architecture

The authoritative design contract is:

[Architecture Revision 1.0](docs/architecture/ARCHITECTURE-REVISION-1.0.md)

Current implementation decisions are recorded in:

[Architecture Decision Records](DECISIONS.md)

## 11. Repository Documentation

- [Architecture contract](docs/architecture/ARCHITECTURE-REVISION-1.0.md)
- [Milestones](MILESTONES.md)
- [Architecture decisions](DECISIONS.md)
- [Development log](DEVELOPMENT_LOG.md)
- [AI prompt log and traceability](PROMPTS.md)
- [Test strategy](docs/testing/TEST-STRATEGY.md)
- [Verified test results](docs/testing/TEST-RESULTS.md)

## 12. Remaining Work

- Reconcile the outstanding F8/F9/M8 formal acceptance obligations and
  the global Pre-M8 gate from the canonical tracker; **do not infer closure**
  from the GUI integration or the 961-test regression.
- Extend and independently qualify destination reliability, path lengths,
  external/removable media, ordinary-user Windows execution and other
  operating systems before expanding support claims.
- Add separate, evidence-backed round-trip and scientific-interoperability
  evaluations where required; the two implemented directions are independent.
- Complete stakeholder demonstration and submission documentation without
  claiming normalization, universal format support, original acquisition
  metadata recovery or clinical validation.



No patient datasets, DICOM studies, generated imaging outputs, secrets or
runtime logs belong in Git. Tests construct synthetic temporary fixtures.
