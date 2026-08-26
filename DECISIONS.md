# Architecture Decision Records

## ADR-001 - Java 21

Decision:
Use Java 21 LTS.

Reason:
Stable LTS baseline and project compatibility.

Status:
Accepted

---

## ADR-008 - Bounded Detection Budget

Decision:
Format detection performs bounded inspection. DICOM inputs larger than 1 MiB return `UNKNOWN` with `INPUT_TOO_LARGE`.

Reason:
Detection must avoid traversing large DICOM objects or series. This is a resource budget, not a statement that large DICOM input is invalid or unsupported; full parsing is deferred to M6 workflows.

Status:
Accepted

---

## ADR-006 - Content-Based Format Detection

Decision:
Detect supported formats from bounded content inspection; treat filename extensions as hints and report mismatches separately.

Reason:
Extensions can be missing or incorrect, while format recognition must be based on file structure.

Status:
Accepted

---

## ADR-007 - Standard Library NIfTI Detection

Decision:
Use Java standard-library byte and gzip APIs for M5 NIfTI header recognition rather than adding a NIfTI dependency.

Reason:
M5 requires header recognition only. A complete NIfTI reader is outside scope and belongs to M7.

Status:
Accepted

---

## ADR-004 - Layered Architecture

Decision:
Use the dependency direction `presentation -> application -> domain`, with infrastructure depending on domain contracts.

Reason:
Separates user interaction, use-case orchestration, domain concepts, and external adapters so processing remains testable and independent of UI frameworks.

Status:
Accepted

---

## ADR-005 - Spatial Coordinate Preservation

Decision:
Every future volume-processing workflow must preserve spatial coordinate and orientation information explicitly.

Reason:
Spatial meaning is essential to MRI volume correctness and must not be lost during loading, normalization, or conversion.

Status:
Accepted requirement; coordinate convention unresolved

---

## ADR-002 - dcm4che

Decision:
Use dcm4che for DICOM handling.

Reason:
Provides established DICOM information-object and file-handling capabilities rather than implementing DICOM parsing ourselves.

Status:
Accepted

---

## ADR-003 - Swing + FlatLaf

Decision:
Use Swing with FlatLaf for the desktop UI.

Reason:
Matches the project requirements while providing a modern cross-platform Swing appearance.

Status:
Accepted
