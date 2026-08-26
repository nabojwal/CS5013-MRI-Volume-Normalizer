# Architecture Decision Records

## ADR-001 - Java 21

Decision:
Use Java 21 LTS.

Reason:
Stable LTS baseline and project compatibility.

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
