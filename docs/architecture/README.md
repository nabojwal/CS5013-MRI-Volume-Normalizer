# M4 Architecture

M4 establishes package boundaries and contracts without implementing format detection, DICOM processing, NIfTI processing, normalization, conversion, or GUI screens.

The dependency direction is:

```text
presentation -> application -> domain
infrastructure -> domain
```

The domain is independent of Swing, FlatLaf, dcm4che, and filesystem implementations.
