# Real MRI DICOM-to-NIfTI integration smoke test — 2026-10-23

**Project:** CS5013 MRI Volume Normalizer (Project PAI)
**Disposition:** **PASS — bounded M6 → M7 → F7 real-data smoke test**
**Platform:** Windows 11, Java 21, Maven/JUnit 5; disposable local Git clone
**Tested repository commit:** `bc1613d0e95bf67014bae76102d4af2e0b89a926` (`feature/pre-m8-foundation`)
**Scope:** Supplementary integration evidence, not F8 contractual acceptance, F9 orchestration, or clinical validation.

## Purpose and evidence provenance

Evaluate the existing, published Project PAI DICOM reconstruction (M6), NIfTI-1 writing (M7), and NIfTI-1 reopening (F7) on an actual MRI DICOM dataset without altering accepted production code. The smoke-test JUnit class was introduced **only into a disposable clone**; it was not part of the published commit, frozen regression inventory, or canonical Pre-M8 tracker.

Evidence originates from the user's Windows Maven transcript dated 2026-10-23 and the separately supplied `RealDicomDatasetSmokeTest.java` source, which was reviewed for assertion scope. The independently prepared NIfTI reference predates the Java run and is not a Project PAI-generated result. Test outcomes reported below are **user-executed**, not independently rerun by this documentation update.

## Input screening and test selection

A private input archive contained **60 conventional single-frame MR DICOM instances** from **four distinct series**. Each used uncompressed Implicit VR Little Endian transfer syntax, unsigned pixels with 12 stored bits in 16 allocated bits. Series identifiers, personal metadata, DICOM files, and images are intentionally omitted.

| De-identified test label | Complete series images | Image matrix | Screening finding |
|---|---:|---|---|
| S01 | 9 | 256 × 256 | Inconsistent orientations across instances |
| S02 | 20 | 960 × 960 | Irregular slice-position gaps |
| S03 | 20 | 800 × 800 | Irregular slice-position gaps |
| S04 | 11 | 256 × 256 | Irregular slice-position gaps |

The frozen M6 geometry policy must **not** silently reconstruct any complete irregular series as a regular 3D volume. For the positive conversion test, **15 physically contiguous S02 slices** were explicitly selected as a derived test slab. This subset is **not** equivalent to successfully converting all 20 S02 instances or the complete 60-instance dataset. Automatic subset selection was not performed or authorized.

## Executed validation

The disposable clone used published source commit `bc1613d0e95bf67014bae76102d4af2e0b89a926`. The local JUnit smoke harness executed two tests:

1. `completeFourSeriesMustNotSilentlyCreateIrregularVolumes`: asserted exactly 60 input DICOMs partitioned into four series of 9/11/20/20 instances; expected controlled M6 failure with no returned volume for each whole series. The orientation-inconsistent group must report `INCOMPATIBLE_INSTANCE`; the other three groups must report `IRREGULAR_SPACING`.
2. `explicitContiguousFifteenSliceSlabConvertsAndReopens`: asserted the 15 selected files share one series identity; called the existing M6/M7 conversion service; verified a newly created uncompressed NIfTI output; compared the **SHA-256 digest of the complete raw NIfTI voxel payload** against an independently assembled DICOM-derived NIfTI reference; reopened the output through F7 and checked selected properties.

### Final successful Windows run

| Gate | Observed result |
|---|---|
| Maven command | `mvn.cmd -B -ntp -Dtest=RealDicomDatasetSmokeTest -DargLine=-Xmx3g -Dpai.real.root=<private-fixture-root> -Dpai.real.reference=<private-reference.nii.gz> test` |
| Maven exit status | **0** |
| JUnit | **2 run, 0 failures, 0 errors, 0 skipped** |
| Maven conclusion | **BUILD SUCCESS** |
| Reconstructed positive subset | **960 × 960 × 15**, UINT16 |
| Slice-center spacing asserted after F7 reopen | **6.0 mm** (absolute tolerance `1e-4` mm) |
| Uncompressed NIfTI file size | **27,648,352 bytes** (352-byte pre-data region + 27,648,000-byte voxel payload) |
| Voxel fidelity assertion | **PASS**, SHA-256 digest over all 27,648,000 stored voxel bytes equals independent reference payload digest |
| Intensity interpretation asserted after F7 reopen | Scaling declared; slope approximately **3.43467643467643**, tolerance `1e-5` |

**Artifact names retained in private evidence storage:** `PAI-real-dicom-maven-final.log`, `TEST-org.cbihi.mrinormalizer.RealDicomDatasetSmokeTest.xml`, `pai_s02_15.nii`, and `PAI_S02_contiguous15_INDEPENDENT_reference.nii.gz`. The produced NIfTI and source DICOM remain outside the public repository. SHA-256 for the final *Java-produced file* has not been supplied in the evidence and is not invented here.

### Execution history and non-defect interruptions

An initial invocation reached the JUnit test but was interrupted by Windows PowerShell treating a nonfatal SLF4J stderr warning as a terminating native-command error. A subsequent retry executed two tests but failed a **pre-existing-output/no-replacement fixture precondition**; the output from the earlier invocation was preserved. The final run used a **fresh output directory** and passed 2/2. These preliminary runs are not reported as passing validation or as demonstrated defects in M6/M7/F7. SLF4J's missing-provider warning persisted in the passing run but did not affect Maven exit status.

## What this evidence establishes — and what it does not

**Established:** A selected contiguous real MRI slab was reconstructed by Project PAI M6, written as a NIfTI-1 volume by M7, reopened successfully by F7, and passed the smoke harness's payload-digest, dimension, scalar-type, slice-spacing, and declared-scaling assertions. All four complete incompatible series were rejected without exposing successful volume results.

**Not established:** Full per-element affine/world-coordinate comparison for the *Java-produced* NIfTI; exhaustive scaling intercept checks; visual image QA; independent dcm2niix/NiBabel comparison of the Java output; general DICOM compatibility or conversion of an entire irregular original series; F8 staged verification/provenance acknowledgment/publication; F9 headless orchestration; GUI readiness; formal F8 acceptance; or clinical-grade validation.

The independent reference was generated using a separate Python DICOM-to-NIfTI construction and its own header/payload checks. The Java smoke test compares **voxel-payload SHA-256 digests**, rather than asserting each byte individually; all payload bytes contribute to the digest. Its reopened-geometry assertions are limited to the properties stated above.

## Reproducibility, confidentiality, and release boundary

The validation used a disposable clone rather than changing the tracked production sources. Exact private evidence directories, medical images, series UIDs, patient identifiers, original DICOM filenames, and source archives must **not** be committed or published. Maintain the raw evidence, original source data, independent reference, and final output in appropriately controlled storage; share only suitably redacted logs or method-level results.

**Release disposition:** This report may be published as **one documentation-only change** under `docs/testing/`. It does not change F8's recorded status: **IMPLEMENTED / PUBLISHED / CROSS-PLATFORM QUALIFIED — FORMAL ACCEPTANCE PENDING**. Any separately proposed real-data fixture in the repository requires explicit privacy review, source-scope authorization and a new verification cycle.
