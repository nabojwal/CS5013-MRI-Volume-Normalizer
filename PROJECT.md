# CS5013 MRI Volume Normalizer

## Purpose

Desktop Java application for importing, validating, normalizing, and converting MRI volume data.

## Primary Goal

Provide a robust workflow for:

```text
DICOM / NIfTI input
        -> format detection
        -> validation
        -> volume loading
        -> normalization
        -> conversion
        -> output validation
        -> provenance reporting
```

## Technology

- Java 21 LTS
- Maven
- Swing
- FlatLaf
- dcm4che
- JUnit 5

## Architectural Principles

- Separation of UI and processing
- Modular services
- Test-driven development
- Deterministic processing
- Explicit validation
- Provenance tracking
- No dependence on filename extensions for format identification

## Out of Scope

- Cloud deployment
- Patient-data hosting
- Deep-learning-based segmentation
- PACS integration unless explicitly added to scope
- Clinical diagnosis
