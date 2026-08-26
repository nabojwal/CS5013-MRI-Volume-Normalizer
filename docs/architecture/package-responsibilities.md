# Package Responsibilities

## `domain`

Owns format-neutral concepts, invariants, and contracts. It must not depend on application, infrastructure, presentation, Swing, FlatLaf, dcm4che, or filesystem implementation classes.

## `application`

Owns synchronous use-case contracts, requests, results, and application-boundary provenance. It coordinates domain contracts without implementing file-format processing.

## `infrastructure`

Reserved for adapters to dcm4che, future NIfTI support, filesystem access, and provenance persistence. No concrete processing adapters are introduced in M4.

## `presentation`

Owns presentation-facing contracts. Future Swing and FlatLaf code belongs here and communicates with application contracts rather than infrastructure implementations.
