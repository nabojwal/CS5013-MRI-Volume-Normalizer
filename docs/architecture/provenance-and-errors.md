# Provenance And Errors

## Provenance

Provenance is owned at the application boundary. `ProcessingResult` carries a `ProvenanceRecord` describing the operation outcome. Future infrastructure may provide technical facts such as fingerprints and software metadata, but the UI does not construct authoritative provenance.

Patient-identifying data must not be written to logs or provenance records by default.

## Errors

Infrastructure exceptions will later be translated into stable application or domain error categories. The application boundary will expose structured outcomes suitable for presentation, while technical causes remain available for controlled diagnostics.

Swing event handlers must not own parsing, processing, provenance construction, or error translation.
