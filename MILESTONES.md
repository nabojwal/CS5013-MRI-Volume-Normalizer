# Project Milestones

Last updated: 2026-10-01

## CURRENT STATE

Current milestone: M6 - DICOM Processing

Last verified build:

```text
mvn clean test
```

Last verified result:

```text
48 tests passed, 0 failures, 0 errors, 0 skipped
BUILD SUCCESS
```

Last completed Git checkpoint:

`fc1ad46` - fix: harden voxel allocation validation

M5.1 corrective checkpoint: `1989589`.

Current M6 status: IN PROGRESS.

- Reconstruction baseline: `3c3d979`.
- Common-normal and whole-grid residual hardening: `32b4bc1`.
- Voxel allocation overflow hardening: `fc1ad46`.
- Format-neutral volume and AR-1 remain pending; passing current tests is not M6 acceptance.

The 48-test result is fresh working-tree verification at `fc1ad46` plus documentation
changes, not a new checkpoint. See [test results](docs/testing/TEST-RESULTS.md).
Architecture Revision 1.0 supersedes older roadmap scope: M7 is DICOM-to-NIfTI,
M8 is controlled reverse conversion; intensity normalization is optional.

## M1 - Development Environment

Status: COMPLETE
Completed: 2026-08-26

Acceptance criteria:

- [x] Java 21 installed
- [x] Maven 3.9.16 installed
- [x] Maven project created
- [x] JUnit 5 working
- [x] `mvn clean test` passes
- [x] Git repository initialized

Git checkpoint: `63fff6d`

## M2 - dcm4che Integration

Status: COMPLETE
Completed: 2026-08-26

Acceptance criteria:

- [x] dcm4che-core integrated
- [x] Maven dependency resolves
- [x] DICOM Attributes API tested
- [x] JUnit smoke test passes
- [x] `mvn clean test` passes

Git checkpoint: `8e1580b`

## M3 - FlatLaf Integration

Status: COMPLETE
Completed: 2026-08-26

Acceptance criteria:

- [x] FlatLaf dependency added
- [x] FlatLaf setup tested
- [x] `mvn clean test` passes
- [x] No unnecessary UI architecture introduced

Git checkpoint: `cf9013d`

## M4 - Core Architecture

Status: COMPLETE
Completed: 2026-08-26

Acceptance criteria:

- [x] Package architecture defined
- [x] Domain model defined
- [x] Service boundaries defined
- [x] UI separated from processing
- [x] Architecture tests pass
- [x] Design documented

Git checkpoint: `7e50a9b`

## M5 - Format Detection

Status: COMPLETE
Completed: 2026-08-26

Acceptance criteria:

- [x] DICOM detection
- [x] NIfTI detection
- [x] `.nii.gz` detection
- [x] Wrong-extension detection
- [x] Missing-extension detection
- [x] Corrupt-file handling
- [x] Unknown-format handling
- [x] JUnit test suite

Git checkpoint: `88da522`

M5.1 hardening checkpoint: `1989589`

## M6 - DICOM Processing

Status: IN PROGRESS

The reconstruction baseline and two scoped correctness fixes are complete and
checkpointed. Overall acceptance follows approved architecture section 24;
`NativeVolume` is still DICOM-shaped and item 19 (format-neutral `ImageVolume`)
is unmet. AR-1 and additional coverage/policy work remain deferred.
See [outstanding gaps](docs/testing/TEST-RESULTS.md#outstanding-test-and-acceptance-gaps).

## M7 - DICOM-to-NIfTI (Architecture 1.0)

Status: NOT STARTED

## M8 - Controlled NIfTI-to-DICOM (Architecture 1.0)

Status: NOT STARTED

## M9 - Validation

Status: NOT STARTED

## M10 - GUI Integration

Status: NOT STARTED

## M11 - Stakeholder Demo

Status: NOT STARTED

## M12 - Final Hardening

Status: NOT STARTED
