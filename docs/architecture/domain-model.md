# Domain Model

M4 establishes these format-neutral concepts:

- `InputSource`: an external input reference.
- `OutputTarget`: an external output reference.
- `ImagingFormat`: a format identity value.
- `Volume`: a conceptual volume contract.

`Volume` intentionally does not define voxel storage, numeric precision, memory layout, axis order, or coordinate convention. Spatial coordinate and orientation preservation are mandatory architectural requirements, but the MRI convention remains unresolved.

No DICOM or NIfTI behavior is implemented in M4.
