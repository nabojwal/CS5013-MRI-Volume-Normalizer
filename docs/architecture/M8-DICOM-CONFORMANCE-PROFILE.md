# Project PAI — M8-MR-1 DICOM conformance profile (implementation candidate)

**Status:** candidate source artifact; not a conformance-tested release.  Baseline contract: `PAI-M8-D1-01` revision 2.  DICOM reference edition: **2026d**.

## Storage and SOP profile

One newly derived **MR Image Storage** Part 10 object (`1.2.840.10008.5.1.4.1.1.4`) per source Z slice, **Explicit VR Little Endian** (`1.2.840.10008.1.2.1`).  Native OW Pixel Data only; no multiframe, private elements, decompression, lossy encoding, interpolation, slice reversal, resampling, or remote PACS operations.  Pixels are always 16-bit stored, signed for INT8/INT16, unsigned for UINT8/UINT16.  Original 8-bit stored numerical values are widened exactly.

## Standard Extended MR SOP Class declaration (G3)

The producer adds standard Rescale Intercept `(0028,1052)`, Rescale Slope `(0028,1053)` and Rescale Type `(0028,1054)` (US meaning unspecified units) as **standard extensions**, not private tags.  Conventional MR Image IOD omits the Modality LUT module.  The attributes are required by Project PAI's narrower self-verification profile but external MR readers may ignore them.  Project PAI makes no universal viewer, clinical or scanner compatibility claim.  Negative slopes are supported if the precise typed, serialized scaling tests pass; no change to the stored raw voxels is used to disguise slope/intercept.

## Metadata source and policy

`M8Metadata` requires explicit MR-origin and BIPED-geometry confirmation, one genuine Scanning Sequence (`SE`, `IR`, `GR`, `EP`, `RM`) and one genuine Sequence Variant (`SK`, `MTC`, `SS`, `TRSS`, `SP`, `MP`, `OSP`, `NONE`).  `RM` and `NONE` do not stand for unknown.  Unknown mandatory Type 1 source metadata is refused rather than invented.  Patient Name/ID/Birth Date/Sex, Study Date/Time, Study ID, Accession, referring physician, Position Reference Indicator and unknown Type 2 acquisition entries are present but empty.  No private patient text, copied NIfTI description, guessed acquisition timing, fake patient identity or scanner identity is generated.

Generated attributes identify the software (`Manufacturer=Project PAI`, `Manufacturer Model Name=M8`), output as `DERIVED\\SECONDARY\\OTHER`, and new random UID identifiers (`2.25.<UUID-decimal>`) for Study, Series, Frame of Reference and each SOP instance.  The Frame of Reference describes only the newly derived series; it does not assert original scanner registration.  All instances share Study/Series/FoR identifiers; each SOP Instance identifier is unique.  Source intensity scaling and voxel values are **PRESERVED**, spatial mapping **INFERRED**, required acquisition selections **USER_SUPPLIED**, new UIDs/time **GENERATED**, output-format constants **DEFAULTED**, and absent patient/acquisition history **NON_RECOVERABLE**.

Geometry uses `D*voxelToWorldRAS` with `D=diag(-1,-1,1,1)`, first IOP triplet increasing column, second increasing row, Pixel Spacing `[row,column]`, and IPP at voxel `(0,0,z)`.  Shear, tilt and serialized slice-separation ambiguity are refused according to the binding `M8-GEO-1` and B02 predicates.

## Production and publication limitations

The M8-owned journal is `PAI-M8-JOURNAL-1`; the completion marker is `PAI-M8-COMPLETE-1`.  Multi-instance hard-link publication is *not* atomic.  A directory, public DICOM file or bare COMPLETED journal record is not success: require validated terminal journal, marker and complete matching inventory (`J AND M AND I`), plus confirmed resource release for execution SUCCESS.  No power-loss durability, hostile-writer isolation, automatic recovery, full anonymization or universal viewer conformance is claimed.

**Acceptance has not been established for this source candidate.** This file is not a DICOM PS3.2 implementation conformance statement approved for unrestricted interoperability.  The user must obtain the complete M8-01–M8-32 evidence and independent M8-P1 review before claiming M8 engineering qualification.
