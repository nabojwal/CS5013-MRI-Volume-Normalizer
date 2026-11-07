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

## How to Use the Application (Windows PowerShell)

These instructions launch the **Swing/FlatLaf desktop GUI from a fresh Git clone**.
They are written for a Windows workstation with a graphical desktop; the
project's documented filesystem qualification is primarily **local NTFS**.
This is a bounded, non-clinical research prototype, not a universal medical
image converter.

### 1. Prerequisites

Install [Git](https://git-scm.com/), a **Java 21 JDK**, and
[Apache Maven](https://maven.apache.org/). Open **Windows PowerShell** and
check that Java and Maven are accessible:

```powershell
java -version
mvn.cmd -version
```

The Java version must be **21**. Maven must also use a Java 21 JDK.
The first build needs access to Maven Central and the configured dcm4che
artifact repository to resolve dependencies.

### 2. Clone and open the project

```powershell
git clone https://github.com/nabojwal/CS5013-MRI-Volume-Normalizer.git
cd CS5013-MRI-Volume-Normalizer
```

If you have already cloned the project, open a PowerShell terminal in its
root directory (the folder containing `pom.xml`) instead.

### 3. Build and create the runtime classpath

Run each command **separately**, from the project root:

```powershell
mvn.cmd -q -DskipTests package
mvn.cmd -q dependency:build-classpath "-Dmdep.outputFile=cp.txt"
$cp = (Get-Content -Raw .\cp.txt).Trim()
```

The quoted `"-Dmdep.outputFile=cp.txt"` is intentional: unquoted Maven
properties can be misparsed by some PowerShell/Maven command-line setups.
On success, the Maven dependency goal writes `cp.txt` in the project root.
`-DskipTests` skips running tests; it is **not** evidence of a passing
regression suite. To run the tests separately, use `mvn.cmd clean test`
with the environment prerequisites described in [Verification](#8-verification).

### 4. Launch the desktop GUI

Enter this as **one complete PowerShell line**:

```powershell
java -cp "target/classes;$cp" org.cbihi.mrinormalizer.presentation.gui.GuiI1App
```

Do not append a backtick, curly quote or unmatched quotation mark. A window
titled **MRI Volume Normalizer** should appear with
**DICOM → NIfTI** and **NIfTI → DICOM** tabs. Keep the terminal open while
the application is running.

### 5. Convert an MRI dataset

1. Choose **DICOM → NIfTI** to inspect a folder containing conventional MR
   DICOM instances. Review detected series, select eligible inputs (up to
   **16** for a batch), and choose an **existing, separate output directory**.
2. Or choose **NIfTI → DICOM** and browse to one supported `.nii`/`.nii.gz`
   file or a folder of such files. Select the input(s), choose an existing
   output directory, and explicitly acknowledge the **non-clinical evaluation
   metadata assumptions** before conversion. The generated DICOM metadata
   does **not** recover unknown original scanner/acquisition details.
3. Click **Convert**, monitor the activity/status area, and inspect the
   resulting files and reports. Only a backend-confirmed **VERIFIED**
   outcome means verified completion; **PARTIAL**, **REJECTED**, **FAILED**,
   **INTERRUPTED** or **RECOVERY_REQUIRED** must not be presented as success.

Use a distinct input and output location. Prefer a **local NTFS directory**
for the Windows evaluation workflow; do not assume arbitrary network, cloud,
USB or unsupported filesystem destinations will work. 

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

## 3. Current Development Status

The following describes the **implemented source on merged `main`**
(`b032a51`) as of 2026-11-07. An implemented feature is not automatically
a formally accepted workstream; the foundation tracker governs acceptance
obligations separately.

| Workstream | Present implementation | Evidence and acceptance boundary |
| --- | --- | --- |
| M6 — DICOM reconstruction | **COMPLETE** for the supported conventional MR profile | Earlier accepted M6 checkpoint and geometry/pixel verification |
| M7 — DICOM → NIfTI | **COMPLETE** for the supported conversion profile | Historical M7 acceptance with independent NiBabel interoperability checks |
| **M8 — Controlled NIfTI → DICOM** | **IMPLEMENTED**: independent 3D integer NIfTI-1 to derived MR DICOM conversion | Native Windows evaluation and integration tests exist; separate formal M8 acceptance is not established by PR #1 |
| **M10 — Desktop GUI integration** | **IMPLEMENTED / INTEGRATED**: working Swing/FlatLaf DICOM ↔ NIfTI GUI | `GuiI1App`, `GuiI1Window`, `GuiI1Execution` and GUI tests are in merged `main`; no blanket separate milestone acceptance implied |
| F1–F7 foundation | **ACCEPTED / CLOSED** within their recorded scope | Historical frozen acceptance evidence remains authoritative |
| F8 — Reopened verification/provenance | **IMPLEMENTED / QUALIFIED** in its documented predecessor scope | Formal acceptance remains pending in the canonical tracker |
| F9 — Selected-series forward orchestration | **IMPLEMENTED / WINDOWS-QUALIFIED SUBMISSION PATH** | Formal F9 acceptance remains pending |
| FBO and EBO batch workflows | **IMPLEMENTED / INTEGRATED** | EBO-1 R2 is accepted within its bounded evaluation scope; other workflows retain their own limits |
| F10 / F11 foundation workstreams | **No formal closure recorded** | These are *not* the original project's M10 GUI milestone |
| Global Pre-M8 gate | **NOT FORMALLY CLOSED** | Integration does not replace the historical acceptance checklist |

**Current reported integrated regression:** 961 Windows tests passed,
0 failures, 0 errors and 0 skips at the tested `454f8b5` source tree,
which matches merged `main`. It was a user-executed, elevated Windows run
with provisioned short-path fixtures, not GitHub CI or general cross-platform
qualification; see [§8](#8-verification).

The earlier **129 passing tests** belong solely to the
**2026-10-03 M7 acceptance checkpoint**. The original M1–M7 checkpoint
table and the historical M8/M10 `NOT STARTED` entries are retained in
[MILESTONES.md](MILESTONES.md), where their date and scope are explicit.
They are not current project status.

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

## 12. Outstanding Qualification and Acceptance Evidence

**Core conversion implementation and the M10 desktop GUI are present.**
The items below concern formal sign-off, broader validation or extension
beyond the implemented submission profile—not instructions to start M8 or
GUI development again.

- **Formal acceptance:** Reconcile the remaining F8/F9/M8 obligations,
  separate F10/F11 foundation milestones and the global Pre-M8 gate
  against the [canonical tracker](docs/planning/PRE-M8-FOUNDATION-TRACKER.md).
  These are not automatically closed by source integration or the
  reported 961-test Windows regression.
- **Storage and platform qualification:** Establish additional evidence
  before claiming unrestricted removable-media, exFAT/FAT32, cloud/network,
  long-path, ordinary-user Windows or other OS support. The existing
  qualified local NTFS implementation is not a universal guarantee.
- **Scientific/round-trip evaluation:** The two conversion directions
  work independently under bounded profiles. Comprehensive bidirectional
  round-trip equivalence and broader real-MRI interoperability require
  their own documented acceptance evidence before such claims are made.
  
No patient datasets, DICOM studies, generated imaging outputs, secrets or
runtime logs belong in Git. Tests construct synthetic temporary fixtures.
