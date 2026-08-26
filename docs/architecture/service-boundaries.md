# Service Boundaries

`ProcessingApplication` is the synchronous application boundary. It accepts a `ProcessingRequest` and returns a `ProcessingResult`.

The contract does not implement format detection, metadata reading, series grouping, volume construction, normalization, conversion, or output validation. Those capabilities belong to later milestones and must be introduced behind explicit contracts when their requirements are defined.

Presentation code will invoke application contracts. It will not call dcm4che, NIfTI adapters, filesystem APIs, or processing code directly.
