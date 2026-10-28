# Project PAI — M8-D1 Implementation Contract

**Identifier:** PAI-M8-D1-01, revision 2, 28 October 2026
**Disposition:** B01/B02 corrected for focused M8-R1 recheck. NOT independent approval or implementation authorization.
**Repository:** `nabojwal/CS5013-MRI-Volume-Normalizer`
**Branch:** `feature/pre-m8-foundation`
**Audited immutable baseline:** `3daf9d829a7b24d826b96faf35d45b7860a986d9`
**Sequence:** M8-D1 → M8-R1 → M8-I1 → M8-P1.

This document is an external design deliverable. No repository file was edited and no executable project test was run for this audit. Requirements below are proposed for freezing at M8-R1; they are not claims of implemented or tested behavior. A later governance-only commit may change the implementation parent, but must leave the audited predecessor source tree unchanged.

### Revision 2 reconciliation

| Review finding | Correction | Existing obligations updated |
|---|---|---|
| M8-R1-B01 | §6.2 defines artifact completion as the conjunction of a valid terminal receipt, verified marker and matching complete public inventory; a bare COMPLETED replay cannot establish success. Explicit failure/crash and subsequent-inspection rules are included | M8-24, M8-26, M8-29 |
| M8-R1-B02 | §4 requires distinguishable, correctly signed adjacent serialized IPPs and a spacing-relative tolerance with no absolute floor, checked before writing and independently on parse-back | M8-08, M8-09, M8-22 |

The 29-ADD/0-MODIFY budget, 32-obligation structure, Java API signatures, baseline and predecessor freezes are unchanged. G1/G2/G3 remain unapproved. The sequence is M8-R1 approval → explicit G1/G2/G3 decisions → governance-only publication → separately authorized M8-I1. This revision does not itself establish that the independent reviewer has cleared either finding.

## 1. Decision and scope

Implement one independent, bounded **NIfTI-1 → derived DICOM MR series** use case. It loads a private snapshot through the existing F7 reader, validates representability, writes one instance per source slice, reopens every instance through a separate dcm4che verifier, and publishes a verified series into a new job-specific child of the chosen output directory.

The existing DICOM → NIfTI workflow is not called by M8 and remains frozen. No round-trip acceptance, GUI, batch directory discovery, PACS transmission, DICOMDIR generation, automatic recovery/resumption, resampling, interpolation, clipping, or restoration of original DICOM metadata is included.

The destination chooser selects a parent folder. M8 creates `m8-<job UUID>/` beneath it. It does not merge into an existing series directory. Input is exactly one selected file; a future GUI may resolve a folder selection to a file, but M8 does not choose among multiple inputs.

### Material design decisions requiring explicit approval

| Decision | Why approval is necessary | Proposed resolution |
|---|---|---|
| G1: M8 implementation-start exception | The canonical tracker still blocks M8 and global Pre-M8 acceptance | Approve the bounded exception in §12 after independent contract review; retain every outstanding predecessor obligation |
| G2: Separate reverse-conversion provenance/publication | F1 has no reverse operation kind; F8 is a forward, single-NIfTI publication protocol | Add M8-owned versioned provenance and multi-instance publication ports. Do not counterfeit a COPY/forward operation or modify F1/F8 |
| G3: MR scaling interoperability profile | Conventional MR IOD does not include the Modality LUT module; NIfTI scaling must not silently disappear | Explicitly use a **Standard Extended MR Image Storage** profile carrying standard rescale attributes, with its own conformance declaration and verification. Do not claim all MR viewers apply these extensions |

These are approval items with concrete proposed resolutions, not invitations to another broad design phase. If G2 or G3 is rejected, implementation cannot silently substitute a predecessor change or narrower conversion profile.

## 2. Repository-grounded audit

The following source and architecture material was inspected at the frozen SHA. Paths in this section are relative to the repository. Baseline links remain immutable.

| Audited component | Finding and M8 consequence |
|---|---|
| [Canonical tracker](https://github.com/nabojwal/CS5013-MRI-Volume-Normalizer/blob/3daf9d829a7b24d826b96faf35d45b7860a986d9/docs/planning/PRE-M8-FOUNDATION-TRACKER.md) | Records F0–F7 closure, F8 formal acceptance pending, and F9 Windows-qualified submission publication only. F10/F11/GUI/M8/global Pre-M8 remain blocked. M8 cannot infer permission from F9 publication |
| [Architecture Revision 1.0](https://github.com/nabojwal/CS5013-MRI-Volume-Normalizer/blob/3daf9d829a7b24d826b96faf35d45b7860a986d9/docs/architecture/ARCHITECTURE-REVISION-1.0.md), especially reverse workflow, layering and metadata policy | Supports controlled reverse conversion with generated metadata. Its separate round-trip plan is deferred by the current scope decision, not fulfilled |
| [DECISIONS.md](https://github.com/nabojwal/CS5013-MRI-Volume-Normalizer/blob/3daf9d829a7b24d826b96faf35d45b7860a986d9/DECISIONS.md), ADRs 001–005 and 009–014 | Java 21; dcm4che; inward dependency direction; generic volume model; explicit scalar, affine and intensity semantics; no overwrite; structured provenance |
| `application/port/out/NiftiVolumeReader.java`; `application/dataset/nifti/{NiftiReadLimits,NiftiReadResult,NiftiReadFinding}.java`; `infrastructure/nifti/{Nifti1VolumeReader,Nifti1Header}.java` | Reuse `read(RelativePath)` and root-bound `Nifti1VolumeReader(Path,NiftiReadLimits)`. Supports four integer types, endian variants, plain/gzip, exactly 3D and depth one. Enforces bounded decoding, extensions, geometry and scaling. Returns RAS/mm `ImageVolume`. F7 accepts nonsingular sforms that need not be orthogonal: M8 must add representability checks |
| `domain/model/{ImageVolume,VolumeGeometry,AffineMatrix4,VoxelData,IntensityTransform}.java` | Reuse unchanged. X is column index, Y row index, Z slice index. `rawValueAt(x,y,z)` returns a long. Geometry columns are direction × spacing. Revalidate finiteness and orthogonality rather than assuming the generic constructor proves them |
| `application/provenance/manifest/{ManifestOperation,ContentDigest}.java` | Kinds are COPY and CONVERT_DICOM_TO_NIFTI, not reverse conversion. Reuse `ContentDigest` and `RelativePath` value types only; retain F1 schema and replay semantics unchanged |
| `application/port/out/VerifiedNiftiOutputAccess.java`; `application/service/VerifiedConversionEvidenceService.java` | F8 binds forward reconstruction, a single staged NIfTI and F1 receipts. It is not a generic DICOM-series publication facility. Borrow its intent/verify/acknowledge ordering as a design principle, not its incompatible protocol |
| `application/service/F9HeadlessConversionService.java` | Orchestrates selected DICOM members through M6/F8 and F1. M8 must not call it, invent a selected DICOM list, or reinterpret its successful status |
| `infrastructure/dicom/Dcm4cheInstanceReader.java` | Existing adapter is useful evidence of library integration, but its domain projection is insufficient for the full new metadata/UID/encoding audit. New parse-back adapter reads the generated instances directly through dcm4che |
| `pom.xml` | Java release 21; dcm4che-core 5.33.0; JUnit 5.12.2; compiler plugin 3.14.1; Surefire 3.5.3. No new Maven dependency or plugin is necessary |
| Existing architecture tests and `VerifiedCopyProviderTest` | Keep unchanged. Full Windows regression needs the three existing `f6.test.windows*` fixture properties and native link fixtures. Linux inherited qualification needs a writable distinct `/dev/shm`; root is not equivalent to ordinary permission testing |

The recorded F9 baseline has 20 focused and 815 full-clean Windows tests. Those are historical tracker facts, not newly rerun evidence. M8 qualification will report actual discovery counts; it will not predeclare a new total or reclassify F8/F9 acceptance.

### Dependency ownership

Application M8 types use Java, existing generic domain types, F7 result types and reusable provenance value types. They contain no dcm4che, Swing, FlatLaf, infrastructure or presentation imports. dcm4che is confined to the two new infrastructure adapters. Filesystem and journal side effects belong to the workspace adapter. A new bootstrap command wires the adapters; no existing entry point is edited. A later GUI can depend on the M8 application service.

## 3. Frozen input and resource profile

### 3.1 Input semantics

Accept only what F7 successfully reads, narrowed as follows:

- Single-file NIfTI-1 (`n+1`), plain or gzip as detected by content. Exactly three dimensions; positive width/height/depth, including depth one.
- UINT8, INT8, UINT16 or INT16, with matching bitpix; either byte order. Intent code zero. No NIfTI-2, ANALYZE, paired files, 4D, vector/RGB/complex, float, or unsupported intent interpretation.
- Use F7's selected sform/qform and its disagreement policy unchanged. Preserve all returned findings, including weak identity and ignored/uninterpreted information.
- Meter/millimeter/micron inputs use F7's conversion to millimeters. Reject unreliable/missing geometry or incompatible source transforms through F7's existing result.
- Carry the generic intensity transform unchanged into planning. F7's undeclared slope-zero case becomes effective slope 1/intercept 0; preserve the distinction between undeclared and declared identity in provenance.
- Extensions may be accepted by F7 within its bounds, but are not interpreted as patient information or copied into DICOM. No second NIfTI parser is added to production.
- Caller must confirm MR origin and the applicability of the BIPED physical-coordinate interpretation. A readable NIfTI header alone cannot establish modality or original patient identity. A newly generated Frame of Reference does not claim to restore a scanner/acquisition frame or registration lineage.

All raw values are validated against the declared scalar range. Fail on invalid values; do not mask an out-of-range value into a different one.

### 3.2 Concrete limits

Binary MiB/GiB units below. `M8Limits.defaults()` is the submission profile. Callers may lower limits; raising any default requires a revised approved profile. Arithmetic uses checked long multiplication/addition before allocation or file creation.

| Limit | Default |
|---|---:|
| Encoded source | 512 MiB |
| Decoded NIfTI | 512 MiB |
| Voxel count | 32,000,000 |
| Each dimension | 1–4096, also subject to voxel/byte budgets |
| Prefix / gzip header | 1 MiB / 64 KiB |
| F7 working allowance | 768 MiB |
| Aggregate accounted application working allowance | 1 GiB |
| Generated DICOM bytes, all slices | 128 MiB |
| Generated metadata overhead allowance | 16 KiB per instance |
| One checkpoint / all checkpoints | 8 MiB / 64 MiB; at most 8 records |
| Maximum provenance JSON nesting | 16 |
| Maximum faults retained | One per distinct (phase, code), primary first |

Account at least `16*N + 4*W*H + 64 MiB + 1 MiB` for F7 working arrays, one write/read slice buffer pair, bounded adapter overhead and stream buffers. No second full-volume voxel copy is allowed. Require `Runtime.maxMemory()` to exceed the accounted working estimate plus 256 MiB before loading; qualification uses `-Xmx2g`. This is an allocation policy, not a promise that all JVM/provider memory is precisely bounded.

Preflight worst-case output `2*N + depth*16384`, and temporary disk use `encoded source + output bound + 64 MiB journal + 64 MiB margin`. Check available space; still handle real write failures. Count actual written bytes, not just estimates. Limit violations are typed refusals. Do not recover from an arbitrary VM-fatal error by reporting success.

## 4. Geometry and numeric policy — M8-GEO-1

Use column-vector indexing. Let `A` be `volume.geometry().voxelToWorldAffine()` in RAS/mm, and let `D = diag(-1,-1,1,1)`. The proposed converter computes `B = D*A`; its columns are `u,v,w,o`. Source voxel `(x,y,k)` has LPS center `o + x*u + y*v + k*w`.

DICOM's plane mapping uses IOP's first triplet along increasing column index, its second along increasing row index, IPP at the first transmitted voxel center, and Pixel Spacing in row/column order [S2]. Therefore this contract sets:

| Output field | Contract value |
|---|---|
| Rows / Columns | source height / width |
| Column spacing `sx` / row spacing `sy` | norms of `u` / `v` |
| First IOP axis `e1` | `u/sx` |
| Second IOP axis `e2` | normalized `v/sy - dot(v/sy,e1)*e1` |
| IPP for instance `k+1` | `o + k*w`, computed directly, not by accumulating rounded steps |
| Pixel Spacing | `[sy,sx]` |
| Slice normal | `n = cross(e1,e2)` |
| Spacing Between Slices | `abs(dot(w,n))` for depth > 1; omit for depth one |
| Slice Thickness | Present, zero length: center spacing does not establish acquisition thickness |

These are M8's representability rules:

1. All inputs, intermediate values and serialized numeric results must be finite. Norms and spacing are positive. Original normalized in-plane dot product must be at most `1e-6` in absolute value.
2. `norm(w - dot(w,n)*n)/norm(w) <= 1e-6`. Reject tilted/sheared stacks outside this bound. Do not silently orthogonalize substantial shear.
3. The small Gram–Schmidt adjustment above corrects numerical noise only. After DICOM decimal serialization, IOP norm error and pairwise dot magnitude must each be ≤ `1e-6`.
4. Compare reconstructed output coordinates against the original `B`, not the normalized approximation: all four in-plane corner centers of every slice must differ by ≤ **0.001 mm Euclidean distance**. For singleton axes also check a virtual one-index step, so a one-pixel image cannot conceal invalid directions. This is an absolute error budget independent of origin magnitude.
5. Preserve source slice indexing: `k` becomes Instance Number `k+1`; no flipping, transposition, or slice reordering. Negative `dot(w,n)` is permitted. Record its sign; geometric sorting by a consumer may reverse Instance Number order. This does not change the stored voxel-to-position association.
6. Verify both projected neighbor spacing and all per-slice IPPs using the serialized-position predicate below. Equal slice count, endpoint agreement or the 0.001 mm corner bound alone is insufficient.

### Serialized-position predicate — B02

For depth greater than one, let `s = abs(dot(w,n)) > 0` and `sigma = sign(dot(w,n))`, using the source-derived normal defined above. Let `nD` be the normalized cross product of the two **parsed serialized** IOP triplets, and let `S` be the parsed positive Spacing Between Slices DS. Require `dot(nD,n) > 0`. For each adjacent pair of serialized IPPs `P[k]`, `P[k+1]`, calculate `delta = P[k+1] - P[k]` and `q = dot(delta,nD)`.

Every condition below is mandatory:

- Parsed positions must be numerically distinct; `q` must be finite and strictly nonzero, and `sigma*q > 0`. String inequality is insufficient. Both positive and negative source slice progression use this same signed rule.
- `abs(S-s) <= 1e-6*s`.
- `abs(abs(q)-S) <= tau`, where **`tau = 1e-6*min(s,S)` mm**, with **no absolute tolerance floor**. Thus tolerance cannot admit a collapsed or reversed interval merely because spacing is small.
- The lateral residual `norm(delta-q*nD) <= 1e-6*min(s,S)` mm. Retain the existing corner-position and orientation bounds independently; these checks do not replace them.

Before the projection check, subtract IPP components as exact decimals from their DS text (BigDecimal), then convert each difference to double. Do not subtract two already-rounded large-origin doubles. Validate the ordinary double-decoded positions too: if a reader-visible double projection collapses, reverses sign or violates the same separation tolerance, reject. Nonfinite intermediates, nonpositive/underflowed tolerance or numerically unprovable comparisons are refusals, not reasons to relax a bound.

Planning evaluates the complete serialized stack with these rules **before INTENT and before writing any DICOM instance**. Failure is PLAN/GEOMETRY_UNREPRESENTABLE and returns a pre-publication refusal. The independent DICOM verifier repeats the checks using parsed output IPPs/IOP/spacing and the source affine; planned adjacent positions are an additional expectation, not a substitute for reading adjacent output instances. Each Dcm4cheMrSliceVerifier instance is bound to one job and receives calls in sourceZ order 0…depth−1 exactly once. It retains the previous instance's actually parsed IPP/IOP/spacing privately, checks each adjacent pair on the next call, and rejects out-of-order, repeated or other-job calls. The service requires depth successful checks before STAGED_VERIFIED. This adds only bounded private state/helpers inside the existing adapter, no new public API or path. The standalone qualification checker repeats the rule independently.

Depth one has no adjacent-pair test and no Spacing Between Slices attribute; its existing orientation, virtual-step and corner checks still apply. A source stack that is physically distinct but not representable under the serialized rules is explicitly unsupported; do not increase spacing, perturb positions or resample to make it pass.

### Decimal String encoding

Every DS component must be a locale-independent finite decimal of at most 16 ASCII characters, excluding DICOM value padding. Use `BigDecimal.valueOf(value)`, round HALF_EVEN at significant precisions 15 down to 1, and consider stripped plain and exponent forms. Normalize exponent spelling and negative zero. Select the candidate with least absolute error; ties prefer shorter then lexicographically smaller text. Parse the chosen text back before acceptance. Reject if required geometric/intensity budgets fail, or a positive spacing/nonzero slope becomes zero. Do not truncate strings to fit.

### Pixel and intensity policy — M8-PIX-1

Conventional MR requires 16-bit allocated samples [S1]. M8 writes every sample in a 16-bit container: unsigned for UINT8/UINT16, signed two's-complement for INT8/INT16. Bits Allocated=16, Bits Stored=16, High Bit=15; Samples per Pixel=1; MONOCHROME2; native OW little-endian Pixel Data. Eight-bit input is widened without changing numerical values. Original scalar type and widening are recorded. No claim of unchanged storage width is made.

For every sample `r`, expected intensity is `s*r+b`. Store Rescale Slope/Intercept and Rescale Type `US` (unspecified units) under the explicitly declared Standard Extended profile (§5). The implementation does not change the integer samples to bake in scaling. Negative slopes are supported. Compare parsed DICOM intensities for every voxel to the source using:

`abs(actual-expected) <= 1e-6 + 1e-9*max(1,abs(expected))`.

Also test source range endpoints before writing. Nonfinite results, slope-sign changes, unrepresentable DS values or failed comparisons cause refusal. Pixel equality remains exact even when intensity comparison uses this tolerance. No window/level, inversion or color rendering is used to validate samples.

## 5. DICOM output and metadata — M8-MR-1

### 5.1 Conformance selection

Generate one Part 10 file per slice using MR Image Storage SOP Class `1.2.840.10008.5.1.4.1.1.4`, Explicit VR Little Endian transfer syntax `1.2.840.10008.1.2.1`. No Enhanced MR, multiframe, encapsulated/compressed Pixel Data or private tags.

The MR module inventory does not include Modality LUT [S3]. Standard Extended SOP Classes may add standard Type 3 attributes without changing standard semantics, using the original SOP Class UID and declaring the additions [S4]. M8's conformance profile therefore declares `(0028,1052) Rescale Intercept`, `(0028,1053) Rescale Slope`, `(0028,1054) Rescale Type` as standard extensions populated by this producer. They remain optional extensions in DICOM conformance terms; no recipient support is assumed. The application requires their presence in its own output verification profile. Missing/ignored scaling in an external viewer is not evidence of byte corruption, and viewer interoperability must not be claimed from dcm4che parsing alone.

### 5.2 Caller template

`M8Metadata` requires:

- `mrOriginConfirmed=true` and `bipedCoordinatesConfirmed=true`.
- Exactly one `ScanningSequence`: SE, IR, GR, EP or RM.
- Exactly one `SequenceVariant`: SK, MTC, SS, TRSS, SP, MP, OSP or NONE.

The caller supplies these from actual known acquisition context. RM and NONE are **not unknown-value defaults**. Unknown required Type 1 metadata means `METADATA_REQUIRED`; multi-valued combinations are outside this initial template. Optional acquisition history is left unknown rather than fabricated. No free-form patient, institution, protocol or acquisition text is accepted in this bounded API.

### 5.3 Attribute allowlist

This is the complete emitted dataset policy; unlisted optional attributes are omitted. dcm4che may generate standard Part 10 file-meta bookkeeping. Dataset attributes are written in tag order. Type 2 unknown values are explicitly present with zero length. Required MR fields and conditions follow [S1]; general study/series/image policies follow [S5–S7].

| Group | Attributes and values |
|---|---|
| Patient | Patient Name, Patient ID, Patient Birth Date, Patient Sex: empty. Anatomical Orientation Type: BIPED. No claim of anonymization, no `PatientIdentityRemoved=YES` |
| Study | Study Instance UID: generated. Study Date/Time, Referring Physician Name, Study ID, Accession Number: empty |
| Series | Modality MR; Series Instance UID generated; Series Number 1; Patient Position empty; Laterality empty (unknown). Series Description fixed ASCII `PAI DERIVED NIFTI`; no inferred body-part code |
| Frame | Frame of Reference UID generated once; Position Reference Indicator empty |
| Equipment | Manufacturer `Project PAI`; Manufacturer Model Name `M8`; Software Versions fixed build/profile identifier. These describe the generating software, not an invented scanner |
| General acquisition | Optional acquisition UID/number/date/time and irradiation event attributes omitted; no invented acquisition event |
| General image | Instance Number k+1; Image Type `DERIVED\SECONDARY\OTHER`; Content Date/Time and Instance Creation Date/Time from one captured UTC generation instant; Timezone Offset From UTC `+0000`; Derivation Description fixed `NIfTI to DICOM; source metadata not restored` |
| Plane | IOP, IPP, Pixel Spacing and optional Spacing Between Slices from §4; Slice Thickness empty. Omit Slice Location and Patient Orientation |
| Pixel | Rows, Columns, Samples per Pixel, Photometric Interpretation, Bits Allocated/Stored, High Bit, Pixel Representation and exact Pixel Data per §4. No Number of Frames, Planar Configuration, padding value or VOI LUT |
| MR | Scanning Sequence and Sequence Variant from caller. Scan Options, MR Acquisition Type, Repetition Time, Echo Time, Echo Train Length: empty. Include empty Inversion Time for IR. Include empty Trigger Time as unknown; do not claim nongating. Unknown MR Acquisition Type is not set to 3D merely because input has three dimensions |
| Contrast | Include Contrast/Bolus Agent empty; optional agent details omitted. This includes the conditional module without asserting that no contrast was used [S8] |
| Scaling extension | Rescale Slope/Intercept from effective source transform; Rescale Type US |
| SOP common | SOP Class UID fixed above; SOP Instance UID unique per slice; generation dates/time as above. Character values are ASCII and default character repertoire; no Specific Character Set needed |
| File meta | File Meta Information Version; matching Media Storage SOP Class/Instance UID; transfer syntax above; dcm4che's valid implementation identification. Verify dataset/file-meta UID agreement |

Do not emit `LossyImageCompression=00`, `BurnedInAnnotation=NO`, or `RecognizableVisualFeatures=NO`: source history/content is unknown [S7]. Omitting identifying text does not de-identify voxel content. Do not copy NIfTI description/extensions into metadata or logs.

### 5.4 UIDs and provenance categories

Generate random version-4 UUIDs through an injectable supplier; encode each DICOM UID as `2.25.<unsigned 128-bit UUID decimal>`. Validate syntax, length ≤64, UUID version and uniqueness. One new Study UID, Series UID and Frame of Reference UID per job, plus one SOP UID per slice; all mutually distinct. Never reuse input-like text as a UID, and do not regenerate identifiers halfway through a job. Re-execution is a new job, not an idempotent overwrite.

Persist attribute origins: **PRESERVED** (raw values/scaling), **INFERRED** (geometry derived from source affine), **USER_SUPPLIED** (confirmed metadata), **GENERATED** (UIDs/time/software labels), **DEFAULTED** (profile encoding constants), **NON_RECOVERABLE** (unknown acquisition/patient history). Empty required fields are NON_RECOVERABLE, not reconstructed defaults. The complete allowlist and origin map form the new conformance document.

## 6. Output safety, provenance and ordering — M8-IO-1

### 6.1 Provider and source boundary

Supported initial execution profiles: Windows default filesystem provider on local NTFS, and Linux default provider on local ext4 (including explicitly labelled WSL2). Output staging and destination must be on the same FileStore and support hard links. Refuse UNC, mapped network output, ZIP/custom providers, network filesystems and ambiguous output containment. Input may reside on another supported local store; it is streamed into a private snapshot.

The Windows profile caps every resolved source/private/final/checkpoint path at 240 UTF-16 code units, checked including generated suffixes before creating the job. Reject device/extended-path syntax from callers. Linux paths remain bounded by the existing logical-reference budget and the provider's actual component limits. Path-budget refusal is RESOURCE_LIMIT, not a claim that the underlying imaging file is invalid. This deliberately does not claim to remove the predecessor's documented long-path limitation.

Resolve existing input/output roots once, no-follow inspect every relevant component, and revalidate them before mutation/publication. Reject source files and private/final entries that are symlinks or nonregular files. A mounted local NTFS volume is not automatically a forbidden symlink; qualify its actual FileStore. Refuse unprovable junction/reparse behavior. Windows requires the native read-open sharing restrictions used for the source/stage safety policy; if unavailable, return PROVIDER_UNSUPPORTED, not a generic read success. Native tests and injected unsupported-option tests remain separately labelled.

Private means job-owned and non-discovered, not automatically confidential: create POSIX job directories mode 0700; on NTFS check the effective owner and reject write access granted to unrelated users/broad groups in the private directory. Administrators/SYSTEM and the current account may retain access. Do not alter caller-root ACLs, grant privileges or change security policy. If privacy/ownership cannot be established using create-time/inherited permissions, fail closed.

Create `output/.pai-m8-<UUID>/` exclusively for private files. Create the final `output/m8-<UUID>/` exclusively only at publication. Collisions, even empty pre-existing directories, are refusals. No scanning or cleaning of other jobs.

Snapshot procedure: open original read-only, retain observable identity/size/time, stream once into `source.nii` while hashing encoded bytes and enforcing limits, close snapshot, independently hash the original through the retained source handle, and compare digest/observations. No source path rename, deletion or writable open. Construct F7's snapshot-root reader only after snapshot creation has finished. Rehash the snapshot after reading and immediately before publication. No requirement that original bytes remain unchanged indefinitely after capture; evidence identifies exactly the captured encoded bytes. Protection assumes a cooperative namespace and excludes indistinguishable hostile replacement or hostile concurrent mutation.

### 6.2 State machine and checkpoint acknowledgment

The workspace owns a one-shot session, pins/leases, stage files and a separate `PAI-M8-JOURNAL-1` protocol. It does not write an F1 manifest.

| Order | Required action and evidence |
|---:|---|
| 1 | Validate request and provider; exclusively create private job; capture source snapshot; F7 load; complete geometry/metadata/resource plan and all UIDs. No DICOM output yet |
| 2 | Write and acknowledge INTENT with the complete immutable plan, source digest, profile ID and source findings |
| 3 | For k=0…depth−1, open a new private `image-%06d.dcm`, write one slice, close, force, hash, reopen through independent verifier, compare all values/metadata/geometry, close and rehash. Store one SliceEvidence per slice |
| 4 | Verify total count, UIDs, source snapshot digest and final planned byte budgets; acknowledge STAGED_VERIFIED with the full ordered slice inventory |
| 5 | Acknowledge PUBLISHING before the first public link. Reserve final child exclusively. Session replays and binds the receipt to its actual staged inventory; caller-constructed evidence is not a publication capability |
| 6 | Hard-link each verified stage file to its reserved final filename, without replacement. Verify identity, digest, count and directory membership of all final files. A link failure stops the loop immediately |
| 7 | Acknowledge PUBLISHED with final inventory. Remove only owned private snapshot/stage links after identity checks. Preserve checkpoint records. Cleanup/release uncertainty prevents successful completion |
| 8 | Acknowledge COMPLETED as the journal-side completion candidate, not proof of artifact success; create `PAI-M8-COMPLETE.json` last, by a no-replace link from a closed, forced and verified private marker. It binds the COMPLETED receipt and full inventory digest. Reopen and verify marker, final membership and digests against the completion predicate below |
| 9 | Close/release all remaining job resources. Only then return SUCCESS or print a success line. A release failure returns RECOVERY_REQUIRED even if verified output/marker exists |

All stage writes precede any public DICOM link. Multi-file visibility is **not atomic**: a crashed publication can leave a partial series. Artifact completion and execution success are evaluated by the conjunctions below, never by a single event or file.

### Completion predicate and terminal-state interpretation — B01

At the time of inspection, **ARTIFACT_COMPLETE = J AND M AND I**, where:

- **J — valid terminal receipt:** strict replay of the entire bounded job journal succeeds, its current head is the exact COMPLETED receipt bound by the marker, and no later recovery record, malformed/torn record, unexpected checkpoint or chain ambiguity is present. An earlier COMPLETED record followed by RECOVERY_REQUIRED is not terminal completion.
- **M — verified marker:** the owned final entry `PAI-M8-COMPLETE.json` is a no-follow regular file, its bounded canonical bytes match the expected marker, and its schema/profile/job ID, COMPLETED sequence/hash and inventory digest match J and I. An inaccessible marker, stale/other-job marker, unexplained entry replacement, symlink, malformed content or hash mismatch does not satisfy M.
- **I — matching complete public inventory:** exactly the planned depth regular DICOM entries plus the marker exist in the final child; each planned filename, instance mapping, size and SHA-256 matches the fully verified inventory bound by J. No missing, extra, duplicate or changed entry is allowed. Content digests are measured again, not trusted from the marker alone.

The canonical marker contains only fixed-order `schema` (PAI-M8-COMPLETE-1), `profile`, `jobId`, `completedSequence`, `completedSha256`, `inventorySha256`, followed by LF. The inventory digest is over the canonical JSON encoding of the ordered SliceEvidence array in the acknowledged checkpoint, including its final LF. Use the journal's serialization rules. Expected marker bytes are derived from the validated terminal checkpoint, avoiding circular self-hashing or trusting an unvalidated marker's claims. Creation-time entry identity is checked while the session is live. Later inspection validates the observed regular entries and bytes under the cooperative-namespace assumption; it cannot prove the absence of an indistinguishable historical same-byte replacement.

**EXECUTION_SUCCESS = ARTIFACT_COMPLETE AND successful release of all job resources AND no execution faults.** `Session.replay()` proves only journal replay and returns a receipt, never artifact or execution success. `sealCompletion()` must evaluate J, M and I; the service cannot return SUCCESS merely because replay returned COMPLETED. No new result status, event, path or public method is introduced.

| Observed outcome | Required disposition |
|---|---|
| Crash after COMPLETED acknowledgment but before marker publication/verification | RECOVERY_REQUIRED on inspection; preserve journal and data; no implicit success or auto-sealing |
| Marker create/link/close/read-back fails, or its outcome is uncertain | RECOVERY_REQUIRED even when all slices are verified and journal head says COMPLETED |
| Marker missing/deleted, corrupt, stale, symlinked or detectably replaced | RECOVERY_REQUIRED; do not recreate, overwrite, delete public files or silently roll back the terminal record |
| Marker valid but journal is invalid/nonterminal or inventory differs | RECOVERY_REQUIRED; marker presence cannot override J or I |
| J, M and I pass but release fails | Execution remains RECOVERY_REQUIRED; retain verified-content knowledge; append recovery observation if possible |
| J, M and I pass and release succeeds without faults | SUCCESS may be returned after release |

If possible, append the existing RECOVERY_REQUIRED event after detecting an execution failure, including after COMPLETED; if recording fails, the return/inspection result is still RECOVERY_REQUIRED. A crash leaves no obligation to invent a missing failure record. A read-only inspector reports recovery without writing anything. Prior success evidence records a past observation only: later marker deletion/corruption or inventory change causes the **current** inspection to fail, without retroactively rewriting that history. No success predicate promises indefinite persistence or power-loss durability.

No claim of power-loss durability, directory-entry fsync guarantees, hostile-writer isolation, network durability, or atomic series rename is made. `force` plus read-back establishes the specified process-level acknowledgment evidence only.

### 6.3 Journal wire contract

Checkpoint filenames are `checkpoint-000001.json` upward. Encode strict UTF-8 JSON without BOM, duplicate keys, nonfinite numbers or extraneous keys. Use fixed field order, LF termination and JSON escaping; canonical numeric evidence needing exact round-trip is encoded with `Double.toHexString`, not locale-dependent decimals. JSON integers are decimal, booleans lowercase, null explicit. Arrays preserve source/slice order and faults preserve primary-first order. Resource limits apply before allocation/parsing.

Each record contains, in order: `schema` (PAI-M8-JOURNAL-1), `profile` (M8-MR-1), `jobId`, `sequence`, `previousSha256` (64 zeroes for first), `event`, `utc`, `plan`, `slices`, `faults`. Plan fields follow §7 record order and include every planned slice's DS strings, UIDs and source-index mapping. Slice evidence includes relative filename, SOP UID, size/SHA-256, compared voxel/corner counts and measured maximum errors. No original absolute path, patient text, exception message or stack trace is serialized. Retain source hash and generated identifiers locally; evidence export should not assume those are anonymous.

Record SHA-256 is over exact complete bytes; it is returned in a Receipt and becomes the next record's previous hash. Creation is CREATE_NEW. Close/force/read-back/replay must establish the exact next sequence and legal transition before acknowledge returns. A returned digest alone is not acknowledgment. Replay enforces no gaps/duplicates, correct hash chain, identical plan across records, monotonic state, exact inventory cardinality and no unknown future schema. Reject malformed/truncated/trailing data. Do not overwrite/rewrite a damaged record.

Legal transitions: INTENT → STAGED_VERIFIED → PUBLISHING → PUBLISHED → COMPLETED. COMPLETED is the journal-side candidate for terminal artifact completion: only J AND M AND I in §6.2 establishes that completion, and bare COMPLETED replay never establishes success. FAILED may follow INTENT or STAGED_VERIFIED only when no public DICOM link is possible and owned cleanup is confirmed. RECOVERY_REQUIRED may follow any state, including COMPLETED, after side effects/uncertainty; it cannot transition back to success. If its own recording fails, retain earlier evidence and return RECOVERY_REQUIRED anyway. At most eight checkpoints accommodates one final recovery record. No automatic replay-and-resume action is included.

### 6.4 Failure semantics

Result status is SUCCESS, REJECTED, FAILED or RECOVERY_REQUIRED. Publication knowledge is NONE, PARTIAL_OR_UNKNOWN or COMPLETE_BYTES_VERIFIED; knowledge must never be upgraded merely because no exception was thrown.

- REJECTED: unsupported/invalid request, metadata, input, geometry, resource or provider before INTENT; remove only positively owned private temporary artifacts. Cleanup uncertainty upgrades to RECOVERY_REQUIRED.
- FAILED: post-INTENT known failure before public links, known visibility/ownership, acknowledged FAILED and successful bounded cleanup/release.
- RECOVERY_REQUIRED: any possible public partial output, journal ambiguity, changed stage/final identity, cleanup/release uncertainty, or unexpected runtime failure after effects. Retain measured source/stage/final digests and earlier acknowledgments. Never delete public instances automatically.
- SUCCESS: J AND M AND I and successful release, with no faults, as defined in §6.2. A replayed COMPLETED event alone never satisfies this rule. Exactly depth instances.

Classify by **activity**, not the broad exception class. A directory creation or stream close error is WRITE_FAILED; source read or parse-back I/O is READ_FAILED at its own phase; metadata/pixel disagreement is VERIFICATION_FAILED. AccessDeniedException retains the phase with ACCESS_DENIED. Typed containment, unsupported-provider, collision, limit and interruption codes take priority over broad I/O mapping. Preserve the primary fault first; append cleanup/journal/release faults without replacing it. Restore interruption status after bounded cleanup. No further slice/publication action after failure.

Here, WRITE_FAILED on close means closing a writing stream. Closing a read handle or releasing a lease is RELEASE/RELEASE_FAILED. Retain the original F7 failure enums in the result; map them to safe M8 facts without changing F7:

| F7 failure group | M8 phase/code |
|---|---|
| PATH_OUTSIDE_ROOT, SYMLINK_DISALLOWED, CONTAINMENT_UNPROVEN | SOURCE/CONTAINMENT_UNPROVEN |
| SOURCE_CHANGED | SOURCE/SOURCE_CHANGED |
| SPECIAL_FILE_UNSUPPORTED, UNSUPPORTED_VARIANT, UNSUPPORTED_DIMENSIONALITY, UNSUPPORTED_DATATYPE, UNSUPPORTED_INTENT, UNSUPPORTED_SPATIAL_UNITS | SOURCE/UNSUPPORTED_INPUT |
| INVALID_HEADER, INVALID_SCALING, GEOMETRY_UNAVAILABLE, TRANSFORM_DISAGREEMENT, INVALID_EXTENSION, TRUNCATED_INPUT, TRAILING_DATA, GZIP_INTEGRITY_FAILED | SOURCE/INVALID_INPUT |
| INPUT_UNAVAILABLE, READ_FAILED | SOURCE/READ_FAILED |
| RESOURCE_LIMIT | SOURCE/RESOURCE_LIMIT |
| INTERRUPTED | SOURCE/INTERRUPTED; preserve interrupt |
| CLOSE_FAILED | RELEASE/RELEASE_FAILED; uncertainty retained |

Before INTENT, a known refusal/read failure can return REJECTED only after safe cleanup; an uncertain close/release cannot. After any public-link attempt, a fault conservatively returns RECOVERY_REQUIRED. Publication knowledge may still be NONE if the adapter positively proves that no public link was created; entering PUBLISHING alone must not fabricate actual publication. No FAILED transition after PUBLISHING is permitted.

## 7. Exact Java contract

All types use package prefix `org.cbihi.mrinormalizer`. Signatures below are normative declarations, not implementation code. `List`, `Optional`, `UUID`, `Clock`, `Instant`, `Supplier`, streams and paths are Java types. Referenced existing types retain their current package/API. Records defensively copy lists; non-null components and cross-field invariants are validated. Public APIs expose no dcm4che types.

### 7.1 Application values — package `application.m8`

```java
public record M8Request(UUID jobId, RelativePath source, M8Metadata metadata) {}

public record M8Metadata(boolean mrOriginConfirmed,
                         boolean bipedCoordinatesConfirmed,
                         ScanningSequence scanningSequence,
                         SequenceVariant sequenceVariant) {
    public enum ScanningSequence { SE, IR, GR, EP, RM }
    public enum SequenceVariant { SK, MTC, SS, TRSS, SP, MP, OSP, NONE }
}

public record M8Limits(NiftiReadLimits nifti, int maxDimension,
                       long maxWorkingBytes, long maxOutputBytes,
                       long maxCheckpointBytes, long maxJournalBytes) {
    public static M8Limits defaults();
}

public record M8Plan(UUID jobId, ContentDigest sourceDigest,
                     ScalarType sourceScalar, int width, int height, int depth,
                     AffineMatrix4 sourceRasAffine,
                     IntensityTransform sourceIntensity,
                     List<NiftiReadFinding> sourceFindings,
                     M8Metadata metadata, Instant generatedAt,
                     String studyUid, String seriesUid, String frameUid,
                     List<String> orientationDs, List<String> pixelSpacingDs,
                     Optional<String> sliceSpacingDs,
                     String slopeDs, String interceptDs,
                     int sliceStepSign, List<Slice> slices) {
    public record Slice(int sourceZ, int instanceNumber,
                        String filename, String sopUid, List<String> positionDs) {}
}

public record M8Fault(Phase phase, Code code) {
    public enum Phase { REQUEST, SOURCE, PLAN, WRITE, VERIFY, JOURNAL,
                        PUBLISH, CLEANUP, RELEASE }
    public enum Code { INVALID_REQUEST, METADATA_REQUIRED, INVALID_INPUT,
        UNSUPPORTED_INPUT, RESOURCE_LIMIT, GEOMETRY_UNREPRESENTABLE,
        SCALING_UNREPRESENTABLE, UID_INVALID, CONTAINMENT_UNPROVEN,
        PROVIDER_UNSUPPORTED, ACCESS_DENIED, SOURCE_CHANGED, OUTPUT_CONFLICT,
        READ_FAILED, WRITE_FAILED, VERIFICATION_FAILED, STAGE_CHANGED,
        FINAL_CHANGED, ACK_UNCERTAIN, PUBLICATION_UNCERTAIN, CLEANUP_FAILED,
        RELEASE_FAILED, INTERRUPTED, INTERNAL_ERROR }
    public static final class Failure extends RuntimeException {
        public Failure(List<M8Fault> faults, boolean effectsUncertain);
        public List<M8Fault> faults();
        public boolean effectsUncertain();
    }
}

public record M8Result(UUID jobId, Status status, Publication publication,
                       Optional<RelativePath> outputDirectory,
                       Optional<ContentDigest> sourceDigest,
                       Optional<M8Workspace.Receipt> receipt,
                       List<M8Workspace.SliceEvidence> evidence,
                       List<NiftiReadFinding> sourceFindings,
                       List<NiftiReadFailure> sourceFailures,
                       List<M8Fault> faults) {
    public enum Status { SUCCESS, REJECTED, FAILED, RECOVERY_REQUIRED }
    public enum Publication { NONE, PARTIAL_OR_UNKNOWN, COMPLETE_BYTES_VERIFIED }
}
```

`source.root()` must be SOURCE. Output directory and evidence filenames are generated OUTPUT-relative references. `jobId` must be a version-4 UUID, generated by bootstrap by default; duplicate job use is a collision, not resume. Values that do not form a valid request are rejected before side effects. `Failure` uses a fixed safe message, no provider cause/stack disclosure. A null request throws IllegalArgumentException before any effect (there is no request job ID to return); bootstrap never constructs it. Result invariants enforce §6.4 and retain available evidence on uncertainty. SUCCESS has no source failures or faults.

### 7.2 Ports — package `application.port.out`

```java
public interface DicomMrSliceWriter {
    void write(ImageVolume source, M8Plan plan, int sourceZ, OutputStream target);
}

public interface DicomMrSliceVerifier {
    Check verify(ImageVolume source, M8Plan plan, int sourceZ, InputStream input);
    record Check(long comparedVoxels, int comparedCorners,
                 double maxWorldErrorMm, double maxIntensityError) {}
}

public interface M8Workspace {
    Session open(M8Request request);
    enum Event { INTENT, STAGED_VERIFIED, PUBLISHING, PUBLISHED,
                 COMPLETED, FAILED, RECOVERY_REQUIRED }
    record Receipt(UUID jobId, long sequence, String sha256, Event event) {}
    record SliceEvidence(int sourceZ, String filename, String sopUid,
                         ContentDigest digest, DicomMrSliceVerifier.Check check) {}
    record Checkpoint(long sequence, String previousSha256, Event event,
                      Instant utc, M8Plan plan, List<SliceEvidence> slices,
                      List<M8Fault> faults) {}
    interface Session extends AutoCloseable {
        ContentDigest sourceDigest();
        NiftiReadResult readSnapshot();
        void verifySnapshotUnchanged();
        OutputStream createStage(int sourceZ);
        InputStream reopenStage(int sourceZ);
        ContentDigest digestClosedStage(int sourceZ);
        Receipt acknowledge(Event event, M8Plan plan,
                            List<SliceEvidence> evidence, List<M8Fault> faults);
        Receipt replay(Receipt expected);
        void publish(Receipt publishingReceipt, List<SliceEvidence> evidence);
        List<SliceEvidence> verifyPublished(List<SliceEvidence> expected);
        void cleanupOwnedData();
        void sealCompletion(Receipt completedReceipt);
        M8Result.Publication publicationKnowledge();
        RelativePath outputDirectory();
        @Override void close();
    }
}
```

Writer and verifier borrow streams and do not close them; the service closes before digest/acknowledgment. Stream failures are classified at the write/read boundary. `open` captures the bounded snapshot and returns a session only on success; an open failure must internally clean owned temporary data or report uncertainty. `readSnapshot` delegates to the accepted F7 reader. Only one call/load is allowed. `createStage` requires acknowledged INTENT, matching planned slice index, and CREATE_NEW. Session internally enforces exact receipt replay, state, ownership, count and digest binding before publish/seal. `verifyPublished` hashes every final file and returns the expected evidence only on exact match; missing/extra files fail. Acknowledgment replays its own new record before returning. `cleanupOwnedData` cannot remove checkpoints, public files or unrecognized entries; before publication it can remove owned stage/snapshot data, after publication only their private names. `close` is idempotent and never publishes or deletes public output.

### 7.3 Services and adapters

```java
// application.conversion
public final class M8GeometryPolicy {
    public M8GeometryPolicy(M8Limits limits, Supplier<UUID> uidSource);
    public M8Plan plan(M8Request request, ImageVolume volume,
                       List<NiftiReadFinding> findings, ContentDigest source,
                       Instant generatedAt);
}

// application.service
public final class M8ConversionService {
    public M8ConversionService(M8Workspace workspace, DicomMrSliceWriter writer,
                               DicomMrSliceVerifier verifier,
                               M8GeometryPolicy planner, Clock clock);
    public synchronized M8Result execute(M8Request request);
}

// infrastructure.dicom
public final class Dcm4cheMrSliceWriter implements DicomMrSliceWriter {
    public Dcm4cheMrSliceWriter();
    public void write(ImageVolume source, M8Plan plan, int sourceZ, OutputStream target);
}
public final class Dcm4cheMrSliceVerifier implements DicomMrSliceVerifier {
    public Dcm4cheMrSliceVerifier(M8Limits limits);
    public Check verify(ImageVolume source, M8Plan plan, int sourceZ, InputStream input);
}

// infrastructure.filesystem
public final class NioM8Workspace implements M8Workspace {
    public NioM8Workspace(Path sourceRoot, Path outputRoot,
                         M8Limits limits, Clock clock);
    public Session open(M8Request request);
}

// infrastructure.provenance
public final class M8JournalCodec {
    public M8JournalCodec(M8Limits limits);
    public byte[] encode(M8Workspace.Checkpoint checkpoint);
    public M8Workspace.Checkpoint decode(byte[] bytes);
}

// bootstrap — composition root, not a presentation dependency
public final class M8Command {
    public static void main(String[] args);
    public static int run(String[] args, PrintStream out, PrintStream err);
}
```

Service instances are single-use; a second `execute` is rejected before opening a workspace. Clock and UUID supplier are injectable. Private/package-private helpers and nested records may live inside these authorized files; no additional top-level source path or public method is implicitly authorized.

The NIO adapter may add a **package-private nested** `Faults` interface and package-private constructor for controlled injection at source read, directory create, stage write/close, hash/reopen, checkpoint force/read-back, each link, final read-back, marker link and cleanup/release. Default implementation does nothing. Tests must identify injected evidence, and faults must be raised at the actual activity boundary. No public test seam or global mutable hook.

### 7.4 Independent verifier rules

Verifier never calls the writer, its attribute-building helper or its pixel serializer. It parses closed bytes through `DicomInputStream`, checks Part 10/file-meta agreement and the allowlist, then independently interprets OW samples by signedness and compares each to `rawValueAt`. It computes world coordinates directly from parsed IOP/IPP/PixelSpacing and compares to `D*A` from the original volume. It does not compare only to the writer's planned DS strings. Exact planned metadata/UID strings are checked in addition to independent semantic checks. No use of M6/M7 or reconversion to NIfTI.

Validate required attribute presence/VR/VM/value, correct pixel length, no encapsulation/fragments/multiframe, no unexpected attributes, no missing/duplicate/extra instances, consistent UIDs and instance mapping, and finite numeric metrics. Parsing success is not IOD validation: these explicit profile checks are required. The adapter can allocate at most one bounded slice's data plus parser overhead; reject larger/undefined-length pixel encodings before bulk allocation. An external checker in §10 supplies a second parsing implementation for qualification, not another production dependency.

Each successful `Check` reports exactly width×height compared voxels and four corner checks, using x endpoints 0/max(1,width−1) and y endpoints 0/max(1,height−1). Singleton-axis virtual checks are therefore counted and identified by dimensions, rather than omitted or presented as additional real voxels. Aggregate counters must equal the sum of per-slice checks.

## 8. Exact implementation path budget

**M8-I1 budget: 29 ADD, 0 MODIFY, 0 DELETE.** No Maven changes. Prefix `P` below means `src/main/java/org/cbihi/mrinormalizer/`; `T` means `src/test/java/org/cbihi/mrinormalizer/`. Each row names exactly one path by concatenation, not a wildcard.

| # | Prefix | Path |
|---:|---|---|
| 1 | P | application/m8/M8Request.java |
| 2 | P | application/m8/M8Metadata.java |
| 3 | P | application/m8/M8Limits.java |
| 4 | P | application/m8/M8Plan.java |
| 5 | P | application/m8/M8Result.java |
| 6 | P | application/m8/M8Fault.java |
| 7 | P | application/port/out/M8Workspace.java |
| 8 | P | application/port/out/DicomMrSliceWriter.java |
| 9 | P | application/port/out/DicomMrSliceVerifier.java |
| 10 | P | application/conversion/M8GeometryPolicy.java |
| 11 | P | application/service/M8ConversionService.java |
| 12 | P | infrastructure/dicom/Dcm4cheMrSliceWriter.java |
| 13 | P | infrastructure/dicom/Dcm4cheMrSliceVerifier.java |
| 14 | P | infrastructure/filesystem/NioM8Workspace.java |
| 15 | P | infrastructure/provenance/M8JournalCodec.java |
| 16 | P | bootstrap/M8Command.java |
| 17 | T | m8/M8ModelTest.java |
| 18 | T | m8/M8GeometryTest.java |
| 19 | T | m8/M8DicomEncodingTest.java |
| 20 | T | m8/M8ConversionIntegrationTest.java |
| 21 | T | infrastructure/filesystem/M8WorkspaceTest.java |
| 22 | T | infrastructure/filesystem/M8RecoveryTest.java |
| 23 | T | m8/M8JournalTest.java |
| 24 | T | m8/M8CommandTest.java |
| 25 | T | architecture/M8BoundaryTest.java |
| 26 | — | docs/architecture/M8-DICOM-CONFORMANCE-PROFILE.md |
| 27 | — | scripts/m8/Verify-M8-Windows.ps1 |
| 28 | — | scripts/m8/verify-m8-linux.sh |
| 29 | — | scripts/m8/check-m8-output.py |

Synthetic binary fixtures are generated by test helpers nested in these test files, not committed as additional resources. No medical image is added to Git. Evidence archives/logs remain outside the checkout. Governance documentation is a separate preceding checkpoint, with budget specified in §12; it is not hidden inside these 29 paths.

All existing tracked paths, including F0–F9, M6/M7, pom.xml, existing tests and tracker, remain frozen during M8-I1. No opportunistic refactoring. A newly discovered incompatibility is reported with the smallest proposed amendment and cannot be silently fixed in a predecessor. Changes to this path list require explicit amendment approval, not another general design phase.

## 9. Finite obligation-to-test matrix

The following 32 rows are the complete M8-R1 acceptance obligations. Each row may be parameterized over the finite cases listed; this is not a promised JUnit method count. Every row must have an actual test/evidence reference and result. PASS/FAIL/NOT RUN are distinct. Native observations and injections are separately identified.

| ID | Frozen obligation / finite cases | Test owner and required evidence |
|---|---|---|
| M8-01 | Request/model invariants, immutable lists, status/publication/receipt consistency, invalid/null/missing metadata, single-use service | M8ModelTest; rejected cases create no DICOM output |
| M8-02 | Four scalars × two endian orders × plain/gzip × depths 1 and 3 (32 small cases) | M8ConversionIntegrationTest; complete pixel/geometry/scaling comparisons |
| M8-03 | Scalar extrema: 0/255, −128/127, 0/65535, −32768/32767; odd in-plane pixel count; exact 8→16 widening | M8DicomEncodingTest; byte lengths, VR, signedness and complete parsed values |
| M8-04 | Reject paired, NIfTI-2, 4D, float/RGB, unsupported intent, inconsistent bitpix | M8ConversionIntegrationTest; retain F7 failure classification; no output |
| M8-05 | Truncated header/payload, malformed extension, bad gzip CRC, trailing decoded bytes | M8ConversionIntegrationTest; no success/no publication |
| M8-06 | Valid sform; qform fallback; disagreement; neither usable; meters/mm/microns; source findings retained | M8ConversionIntegrationTest |
| M8-07 | Axis-aligned, oblique, axis-permuted, reflected and negative slice progression; singleton axes | M8GeometryTest plus parsed outputs; source index and world association preserved |
| M8-08 | In-plane shear/stack tilt, zero/singular/nonfinite geometry; both sides of orientation/corner bounds; positive and negative slice progression with spacing errors just below, exactly at and just above the B02 relative bound | M8GeometryTest; reject unrepresentable stacks before INTENT/writing, including tiny-spacing and large-origin cases |
| M8-09 | DS 16-character boundary, exponent/locale/negative-zero handling; distinct source IPPs rounding to identical DS coordinates; text-distinct but numerically equal positions; distinct decimal IPPs collapsing in double; a nearby representable stack | M8GeometryTest; apply B02 with no absolute floor; reject collapse, sign reversal, nonfinite/underflowed comparisons; retain a positive control |
| M8-10 | Undeclared identity, declared identity, positive/nonunit/negative slope, nonzero intercept, DS-unrepresentable scaling | M8DicomEncodingTest; exact raw values and every scaled value |
| M8-11 | Required Type 1 metadata unknown/false confirmation, each sequence and variant enum, IR conditional field | M8ModelTest/M8DicomEncodingTest; no RM/NONE fallback |
| M8-12 | Full allowlist, required Type 2 empties, forbidden fabricated history/privacy assertions, file-meta agreement | M8DicomEncodingTest; explicit tag/VR/VM checks |
| M8-13 | UID syntax/length/uniqueness, shared Study/Series/FoR, distinct per-slice SOP; injected duplicate/bad UUID supplier | M8GeometryTest/M8DicomEncodingTest |
| M8-14 | Exactly depth files, deterministic filenames/index mapping, no overwrite/merge into existing empty or populated destination | M8WorkspaceTest/M8ConversionIntegrationTest |
| M8-15 | Per-limit boundary and +1, arithmetic overflow, low heap preflight, oversized parse-back PixelData | M8ModelTest/M8ConversionIntegrationTest; use low configured limits, no giant allocation requirement |
| M8-16 | Source read-only behavior, source/snapshot mutation, retained-handle digest mismatch, weak identity finding | M8WorkspaceTest; before/after source hashes and injected/native evidence labels |
| M8-17 | Symlink/junction/nonregular source or output, traversal, root substitution; local mounted volume vs network paths | M8WorkspaceTest; actual native fixtures where applicable, fail on missing required fixtures |
| M8-18 | Native same-store hard links, output collision, native sharing refusal; injected unavailable sharing/hard-link capabilities | M8WorkspaceTest; platform and provider recorded, no claiming injection is native evidence |
| M8-19 | Owner/ACL policy, unrelated-writer access and ownership observation anomaly | M8WorkspaceTest; actual permissions where safely provisioned; injected observations distinctly labelled |
| M8-20 | Creation/write/close/read/reopen failures classified by activity, including parent creation WRITE_FAILED | M8WorkspaceTest/M8RecoveryTest; returned and checkpointed primary classifications |
| M8-21 | Corrupt one voxel, dimension, signedness, affine, scaling, UID, file meta or required tag independently | M8DicomEncodingTest; verifier refuses each, demonstrating sensitivity independently of writer |
| M8-22 | Full counts/finite maxima; missing/extra/duplicate slice; changed digest; parsed adjacent IPPs collapsed/reversed or separated inconsistently with declared spacing, including within the old 0.001 mm corner bound | M8ConversionIntegrationTest/M8WorkspaceTest; independent verifier and external checker reject; both signed progression controls pass |
| M8-23 | Journal round-trip, canonical bytes, duplicate/unknown keys, bad sequence/hash, truncated record, unknown schema, size/nesting bounds | M8JournalTest; strict refusal, no repair |
| M8-24 | All acknowledgment write/force/read-back failures; COMPLETED replay with missing/invalid marker, invalid inventory, later RECOVERY_REQUIRED, or a torn trailing checkpoint | M8RecoveryTest; separately falsify J, M and I; bare COMPLETED never yields artifact completion or SUCCESS |
| M8-25 | Invented/stale/other-job receipt or mismatched evidence cannot authorize link/marker | M8WorkspaceTest; workspace replays/binds actual session state |
| M8-26 | Fault before/between links, after last link before ACK; marker creation/link/close/read-back failure; marker deletion, byte corruption, detectable replacement/other-job substitution; release failure after marker | M8RecoveryTest; RECOVERY_REQUIRED where specified; preserve evidence, no auto-seal/repair/public cleanup; later tampering invalidates current inspection despite prior success |
| M8-27 | Primary failure plus cleanup/release/journal failure; artifact replacement during cleanup | M8RecoveryTest; primary retained, uncertainty monotonic, replaced entries preserved |
| M8-28 | Interrupt during snapshot/write/verify/link; bounded cleanup and preserved interrupt status | M8RecoveryTest; no completion/success text |
| M8-29 | Child-JVM termination after STAGED_VERIFIED, first link, final link, COMPLETED acknowledgment before marker creation, and marker link before verification | M8RecoveryTest; inspect J/M/I independently; bare COMPLETED or marker presence is insufficient; no reconstructed successful execution after a crash. If J/M/I now pass, report artifact complete but execution RECOVERY_REQUIRED because release is unestablished; otherwise also RECOVERY_REQUIRED. No auto-resume |
| M8-30 | CLI usage/error mapping, root selection, structured safe output, cleanup-before-success, no exception/PHI leakage | M8CommandTest |
| M8-31 | Dependency rules, frozen predecessor hashes, both-platform regression, no forward-service invocation | M8BoundaryTest plus qualification scripts |
| M8-32 | One real supported NIfTI → DICOM demonstration and one controlled rejection; separate parser compares complete output to NIfTI | check-m8-output.py plus retained evidence; not a round-trip acceptance test |

M8-19 does not claim native ownership transfer merely because an injected anomaly is rejected. Hostile-writer replacement, unsupported native providers not available in the environment, power-loss durability and full external-viewer interoperability remain NOT QUALIFIED. These do not convert mandatory matrix rows into silent skips: each row's specified injected/native components must be accounted for individually.

## 10. Implementation and executable verification plan

### 10.1 One implementation cycle

After M8-R1 and explicit M8-start authorization: (1) capture predecessor hashes/clean-index evidence; (2) write model, geometry and negative-oracle tests; (3) implement pure planning and DICOM writer/verifier; (4) implement bounded workspace/journal and fault tests; (5) wire service/command; (6) run focused, affected and clean gates; (7) real input and independent checker; (8) freeze source hashes and hand evidence to reviewer. These are internal work ordering within M8-I1, not new approval stages.

Do not execute these commands during M8-D1. Java/Javac must be 21 and Maven 3.9.16 for qualification reproducibility. Record executable paths, full versions, OS/filesystem, effective token/user and source hashes. Maven or compiler stderr is not itself failure; capture process exit codes and preserve both streams.

### 10.2 Gates

Use `-Dmaven.test.failure.ignore=false`, no skip flags, fresh Surefire reports, and an actual nonzero discovered test count. Start with clean compilation to avoid stale generated-class evidence. Copy reports/logs outside target after each gate before another clean. Fail if missing fixtures cause the gate not to run; never re-label missing evidence as PASS.

Linux example, in an isolated native `/home` ext4 checkout as non-root, with writable distinct `/dev/shm`:

```bash
export MAVEN_OPTS='-Xmx2g'
mvn -B -DargLine=-Xmx2g -Dmaven.test.failure.ignore=false '-Dtest=M8*' clean test
mvn -B -DargLine=-Xmx2g -Dmaven.test.failure.ignore=false '-Dtest=M8*,*Nifti*,*Dicom*,*Conversion*,*Manifest*,*VerifiedCopy*,CopyJournalSequenceTest,*Boundary*' test
mvn -B -DargLine=-Xmx2g -Dmaven.test.failure.ignore=false clean test
```

Windows runner accepts explicit `-Repo`, `-Evidence`, `-CrossStoreRoot`, `-UncRoot`, `-MappedRoot` and `-NativeFixtureProfile` parameters. It verifies actual mounts/mappings/access in the same executing token, native Java symlink capability where inherited tests need it, and preserves token evidence. It passes these **existing** inherited properties to all Maven gates:

```powershell
$native = @(
  "-Df6.test.windowsCrossStoreRoot=$CrossStoreRoot",
  "-Df6.test.windowsUncRoot=$UncRoot",
  "-Df6.test.windowsMappedRoot=$MappedRoot"
)
$common = @('-B', '-DargLine=-Xmx2g', '-Dmaven.test.failure.ignore=false')
& mvn @common @native '-Dtest=M8*' clean test
# Capture exit code; preserve this gate's reports before continuing.
& mvn @common @native '-Dtest=M8*,*Nifti*,*Dicom*,*Conversion*,*Manifest*,*VerifiedCopy*,CopyJournalSequenceTest,*Boundary*' test
# Capture exit code and preserve reports again.
& mvn @common @native clean test
```

The implemented scripts must stop on any nonzero exit or XML failures/errors/skips, and on source-hash/scope changes. The snippets illustrate the exact selections; running three bare commands without checking intermediate results is not the qualification runner. Introduce `m8.test.native=true` in both scripts so M8 native fixtures are required in qualification; ordinary unit runs may separate them but cannot count omitted native rows as qualified. Conditional tests must declare applicability and the runner reports it explicitly rather than silently accepting an assumed test.

Required M8 acceptance evidence: Windows local NTFS and non-root Linux ext4 focused/affected/clean gates on identical source bytes, plus M8-32. WSL2 is labelled WSL2. Elevated Windows qualification is permissible for fixtures and must be labelled; it does not establish ordinary-user operation. An additional full ordinary-user Windows suite is **not required** by this contract. An ordinary-user success/rejection smoke test is useful supplementary evidence if executable without policy changes; otherwise record NOT RUN. No privilege or ACL-policy change is authorized by the scripts.

If a frozen predecessor test fails on a platform, retain evidence and investigate without editing the predecessor. M8 cannot declare full regression PASS or cure that failure by expanding its authorized paths. Any necessary predecessor correction needs a bounded explicit amendment. Passing a regression suite does not itself close all F8/F9 formal-acceptance obligations.

### 10.3 Headless entry point and demonstration

The new bootstrap command accepts exactly:

```text
--source-root <existing-directory>
--input <SOURCE-relative-file>
--output-root <existing-directory>
--confirm-mr
--confirm-biped
--scanning-sequence <SE|IR|GR|EP|RM>
--sequence-variant <SK|MTC|SS|TRSS|SP|MP|OSP|NONE>
```

No arbitrary metadata file, hidden default confirmation, overwrite/resume flag, or forward-converter call. Command generates job UUID and uses default limits. Unknown/duplicate flags fail. Exit codes: 0 SUCCESS, 2 request/usage or REJECTED, 3 FAILED, 4 RECOVERY_REQUIRED. Print one final bounded structured status after resource release: status, job ID, relative output location, counts and safe codes. No stack trace/raw path/patient data in routine output. The local caller already supplied root paths; they need not be repeated in logs.

Qualification scripts build the classpath from Maven dependencies and invoke `org.cbihi.mrinormalizer.bootstrap.M8Command` with Java 21. They must preserve the actual invocation and exit status without embedding credentials or raw medical metadata.

Use a real supported NIfTI with known MR template values (the independently verified selected-slab reference is an eligible candidate if its owner confirms the template). Hash the NIfTI before/after; compare every output sample, intensity and physical location, all UIDs and exact instance count. For a controlled rejection, make an isolated synthetic unsupported 4D or sheared NIfTI; assert a refusal, no completion marker and no public DICOM output. Do not deliberately corrupt the user's medical source.

`check-m8-output.py` uses Python standard library only: bounded NIfTI-1 plain/gzip parsing for the declared demo profile and a separate Explicit VR Little Endian Part 10 parser for the generated allowlist, without Java writer/verifier code or dcm4che. It accepts source file and final series directory, validates source/header/data and output/marker inventory, and emits six booleans plus measured counts: dimensions, exact stored values, geometry, scaling, metadata/UID consistency, complete publication. All must pass; print actual interpreter version. It must fail on a deliberately changed pixel and a deliberately changed IPP in disposable copies, demonstrating independent checker sensitivity. It is a qualification oracle, not a production parser or generic DICOM validator. No original DICOM acquisition or forward conversion is required.

### 10.4 Evidence package

One final review package: baseline and governance-parent SHAs; exact 29-path ADD inventory; full patch and per-file SHA-256; pre/post predecessor hashes; toolchain/token/filesystem records; gate commands/exit codes/logs/fresh XML recount; finite matrix results; source/output hashes and sanitized real-demo summary; controlled rejection; native/injected applicability; preserved initial failures and justified corrections. Exclude medical images, private paths and patient-bearing metadata from shared review archives. Hash the archive after final assembly. No declared test totals until execution.

## 11. Acceptance and retained limitations

M8-R1 approves a design only. M8-I1 is complete when all mandatory rows are evidenced on the final candidate, no blocking finding remains, path/source integrity passes, and the exact Windows/Linux profile is stated. M8-P1 independently reviews source and evidence, then awaits separate local-commit and user-controlled push authorization. Tracker reconciliation is separate and records actual SHAs and limitations.

Accepting M8 under the proposed exception does not close F8, F9, F10, F11 or global Pre-M8; does not authorize GUI; and does not claim original DICOM reconstruction, clinical validation, universal viewer compatibility or round-trip preservation. Output is a new derived series with a deliberately bounded metadata template. If deadline constraints require Windows-only evidence, that would need a separately approved submission qualification exception, not a silent change to this contract's dual-platform acceptance plan.

## 12. Minimum governance action and proposed authorization

The fastest compliant start is a bounded M8 exception, not an assertion that all Pre-M8 prerequisites have become complete. After independent review, prepare one documentation-only checkpoint:

| Action | Exact path |
|---|---|
| ADD approved contract | `docs/planning/M8-IMPLEMENTATION-CONTRACT-1.0.md` |
| ADD explicit exception | `docs/planning/M8-IMPLEMENTATION-START-EXCEPTION-01.md` |
| MODIFY only the M8/scope records | `docs/planning/PRE-M8-FOUNDATION-TRACKER.md` |

Budget: **2 ADD + 1 MODIFY**, no Java/build/test changes. This step is not performed or authorized for editing by M8-D1. Preserve F8's outstanding obligations, F9 Windows-only status, F10/F11/GUI restrictions and all predecessor freezes. Record round-trip as outside current M8 acceptance/deferred; do not tick it complete. Record G2 and G3 explicitly rather than implying they are already in F1 or the old architecture.

Proposed owner approval, to be used only after M8-R1:

> I approve the independently reviewed PAI-M8-D1-01 contract and PAI-M8-START-EXCEPTION-01 for standalone NIfTI-to-DICOM implementation. I approve the bounded M8-owned provenance/publication protocol and the declared Standard Extended MR scaling profile in this contract. I authorize the separate 2-ADD/1-MODIFY governance reconciliation, subject to its exact diff review. This approval does not authorize M8-I1. After the approved governance checkpoint is published, M8-I1 requires my separate explicit authorization within its exact 29-ADD/0-MODIFY budget and qualification plan. This exception does not declare F8/F9/global Pre-M8 accepted, permit predecessor changes, authorize GUI or round-trip work, or waive later-phase obligations. Staging, local commit and remote publication require my separate authorization; GitHub pushes remain mine.

This text is proposed, not recorded approval. A reviewer or implementation agent must not interpret the present design request as this approval. If the baseline has drifted beyond governance-only changes, reconcile its source identity before implementation; do not silently rebase to live HEAD.

## 13. Independent-review handoff

Review this one contract against the frozen SHA, concentrating on: F7 compatibility; MR conformance and honest required metadata; source-to-output geometry/scaling math; independent verification; publication/recovery certainty; exact APIs and file budget; finite matrix coverage; and the explicit G1/G2/G3 approval boundaries. Return material blockers with obligation IDs and concrete corrections. Corrections belong to M8-D1/M8-R1, not a new general architecture phase.

Requested disposition: **APPROVE FOR EXPLICIT M8-I1 AUTHORIZATION**, or **CHANGES REQUIRED** with a bounded list. No source implementation, test execution, tracker edit, commit or push is part of that design review. No claim of implementation feasibility testing has been made in this audit.

For revision 2, focus the recheck on §4's serialized-position predicate and its ordered parse-back implementation contract, §6.2–6.4's completion semantics, and amended M8-08/09/22/24/26/29. In particular verify that the J/M/I conjunction is not replaced by bare terminal-event replay, and that no absolute spacing tolerance can admit collapsed adjacent slices. Other previously passed areas remain unchanged except the necessary cross-reference and explicit separate M8-I1 authorization wording. The revision does not claim independent review approval.

## 14. Standards references consulted

These sources establish the standard's attribute meanings; numerical limits, profile restrictions and transaction policies above are explicit Project PAI design decisions. The consulted current pages identify DICOM 2026d; pin that edition in the conformance artifact rather than silently following later changes.

- S1: [PS3.3 C.8.3 MR Image Module](https://dicom.nema.org/medical/dicom/current/output/chtml/part03/sect_C.8.3.html).
- S2: [PS3.3 C.7.6.2 Image Plane](https://dicom.nema.org/medical/dicom/current/output/chtml/part03/sect_C.7.6.2.html).
- S3: [PS3.3 A.4.3 MR IOD module inventory](https://dicom.nema.org/medical/dicom/current/output/chtml/part03/sect_A.4.3.html).
- S4: [PS3.2 §7.3 Standard Extended SOP Class rules](https://dicom.nema.org/medical/dicom/current/output/chtml/part02/sect_7.3.html).
- S5: [PS3.3 C.7.2 General Study](https://dicom.nema.org/medical/dicom/current/output/chtml/part03/sect_C.7.2.html).
- S6: [PS3.3 C.7.3 General Series](https://dicom.nema.org/medical/dicom/current/output/chtml/part03/sect_C.7.3.html).
- S7: [PS3.3 C.7.6 General Image](https://dicom.nema.org/medical/dicom/current/output/chtml/part03/sect_C.7.6.html).
- S8: [PS3.3 C.7.6.4 Contrast/Bolus](https://dicom.nema.org/medical/dicom/current/output/chtml/part03/sect_C.7.6.4.html).
- S9: [PS3.3 C.7.10 General Acquisition](https://dicom.nema.org/medical/dicom/current/output/chtml/part03/sect_C.7.10.html).

**Revision 2 delivered for focused M8-R1 recheck of B01/B02. G1/G2/G3 and M8-I1 remain unapproved. No implementation or publication is authorized.**
