# M6-DICOM-PROCESSING v1.0.0

**Prompt ID:** M6-DICOM-PROCESSING
**Version:** 1.0.0
**Status:** ACTIVE
**Agent:** Qwen 30B MoE
**Created:** 2026-08-27
**Git Baseline:** b1fb3d1
**Supersedes:** None

## M6 Implementation Contract

### Explicit File List
- All DICOM processing must be contained within infrastructure.dicom package
- dcm4che is only used in infrastructure.dicom
- No other DICOM libraries should be introduced

### Mandatory Selected SeriesInstanceUID
- Only process the specified SeriesInstanceUID
- Ignore all other series in input files

### Conventional Single-Frame MR
- Process only single-frame MR images
- Reject multi-frame DICOM instances

### Native DICOM Patient Coordinates
- Preserve native DICOM patient coordinates
- No LPS/RAS coordinate conversion
- Use ImageOrientationPatient and ImagePositionPatient for geometric calculations

### Geometric Slice Ordering
- Order slices based on projected positions
- Calculate slice normal using ImageOrientationPatient
- Ensure proper geometric slice ordering

### Required DICOM Tags
- ImageOrientationPatient
- ImagePositionPatient
- Bits Allocated
- Bits Stored
- High Bit
- Pixel Representation
- Samples Per Pixel
- Photometric Interpretation
- Rescale Slope/Intercept (preserved explicitly)

### Pixel Data Handling
- Raw voxel values as long integers
- Explicit pixel encoding semantics
- No normalization
- No resampling
- No interpolation

### Transfer Syntax Scope
- Only process approved transfer syntaxes
- Validate transfer syntax during processing

### Error Categories
- Define explicit error categories for validation failures
- Distinguish between data errors and processing errors

### Privacy-Safe Provenance
- Track provenance without exposing patient information
- Maintain reproducibility of processing steps
- Preserve processing metadata safely

### Synthetic DICOM Fixtures
- Create synthetic DICOM fixtures for testing
- Include adversarial test cases

### Adversarial Test Requirements
- Include edge case testing
- Test with malformed data
- Verify robustness against invalid inputs

### Implementation Sequence
1. Validate input DICOM files
2. Extract required metadata
3. Perform geometric calculations
4. Process pixel data according to encoding specifications
5. Validate geometry and pixel data
6. Generate output with provenance information

### Verification Requirements
- All tests must pass before milestone completion
- Verify correct geometric slice ordering
- Confirm pixel data handling accuracy
- Validate error categorization
- Test with synthetic DICOM fixtures

### Git Checkpoint Requirements
- Create Git checkpoint after each major implementation phase
- Ensure all changes are properly tested
- Document implementation decisions in DEVELOPMENT_LOG.md
