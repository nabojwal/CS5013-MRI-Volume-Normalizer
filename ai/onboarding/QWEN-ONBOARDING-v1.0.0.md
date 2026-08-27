# QWEN-ONBOARDING v1.0.0

**Prompt ID:** QWEN-ONBOARDING
**Version:** 1.0.0
**Status:** ACTIVE
**Agent:** Qwen 30B MoE
**Created:** 2026-08-27
**Git Baseline:** b1fb3d1
**Supersedes:** None

## Project Identity

This is the CS5013 MRI Volume Normalizer project, a Java-based medical imaging application designed to process DICOM MRI volumes. The system must handle medical imaging data safely while maintaining reproducibility and provenance.

## Technology Stack

- Java 21 LTS
- Maven 3.9.16
- JUnit 5
- dcm4che for DICOM handling
- FlatLaf for UI theming
- Swing for GUI components

## Existing Milestone History

The project is currently in the M6 milestone, which focuses on DICOM processing capabilities.

## Architecture

The system follows a layered architecture with clear separation between:
- Presentation layer (Swing UI)
- Business logic layer 
- Infrastructure layer (DICOM handling, file I/O)

## Current M6 Scope

M6 focuses on implementing core DICOM processing capabilities including:
- DICOM series and instance processing
- Geometry calculation from ImageOrientationPatient and ImagePositionPatient
- Pixel data handling with explicit encoding semantics
- Transfer syntax validation
- Privacy-safe provenance tracking

## Approved M6 Decisions

- Use of dcm4che only in infrastructure.dicom package
- Native DICOM patient coordinates (no LPS/RAS conversion)
- Geometric slice ordering based on projected positions
- Preservation of raw voxel values as long integers
- Explicit handling of pixel encoding parameters (Bits Allocated, Bits Stored, etc.)
- No normalization, resampling, or interpolation
- Approved transfer syntax scope

## Dependency Policy

- Only add dependencies when there's a clear requirement
- Minimal dependency additions
- All dependencies must be validated and tested
- Do not silently change dependency versions

## Encoding Safety

- Use UTF-8 encoding for all text processing
- Validate external input rather than trusting filenames or extensions
- Ensure safe handling of medical imaging data

## Git Safety

- Never commit patient datasets, DICOM studies, NIfTI volumes, or other large medical-imaging data inside Git
- All code changes must be verified with mvn clean test
- Maintain provenance and reproducibility

## Testing Expectations

- Every functional module should have JUnit tests
- All tests must pass before declaring a milestone complete
- Test coverage should include both positive and adversarial cases
- Run mvn clean test after dependency or architecture changes

## Autonomous-Agent Rules

- Do not modify working functionality without tests
- Add one dependency at a time
- Keep UI code separate from medical-imaging processing logic
- Never put DICOM/NIfTI processing logic directly in Swing event handlers
- Prefer small, testable classes with single responsibilities
- Implement incremental changes
