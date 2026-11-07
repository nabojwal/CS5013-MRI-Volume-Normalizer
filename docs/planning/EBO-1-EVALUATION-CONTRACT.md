# Project PAI — EBO-1 evaluation-only batch orchestration

**Contract:** PAI-EBO-1-CONTRACT-1.0. **Expected exact parent:** `9da0f11c6799cb1be080594cd7c7221ae9f83cd2` (`checkpoint/m8-i1-windows-native-pass`).

**Status (2026-11-02): EBO-1 R2 PUBLISHED / ACCEPTED / CLOSED for the bounded non-clinical evaluation profile**, at Git commit `b7739fd5db9a8e83e12f3a95b7f03bad31bd7a89` on `checkpoint/ebo-1-r2-windows-evidenced`. The integration branch `integration/post-ebo1-r2` starts at that same SHA; `main` has not been merged. **M8-I1 formal acceptance remains a separate decision**. Existing M8 classes, CLI, metadata validator, NTFS workspace behavior, Maven configuration and other predecessor code were not modified in the bounded EBO R2 commit.

### R2 acceptance and historical qualification interpretation (2026-11-02)

The original 11-file EBO R1 implementation was qualified on native Windows
(Java 21) with **883/883** full regression tests and a non-clinical BraTS
single-volume/four-volume evaluation demonstration. Independent R1 acceptance
review found 22/27 obligations demonstrated and five needing further evidence
(T03, T06, T12, T18, T26). Those five were added as executable JUnit cases
in `EboAcceptanceClosureTest.java`. Native Windows execution confirmed a real
T06 unsupported-UNC-root preflight defect; **only**
`EboFileDiscovery.java` was corrected to reject such roots before any M8 job
or report I/O. No legacy M8 or predecessor behavior was changed.

After correction, native Windows **five focused tests passed 5/5**, the
inherited F6 mapped-drive targeted test passed **1/1**, and the clean full
regression passed **888/888** (zero failures, errors or skips). The remote
release commit contains exactly the original 11 EBO additions (with the T06
correction) plus the one closure JUnit class; source blob integrity was
verified. This evidence supports **T01-T27 acceptance within this contract's
non-clinical evaluation profile only**. The earlier R1 BraTS demonstration
remains frozen and **was not rerun using R2**; a new R2 BraTS pixel-comparison
claim would be false. The R2 corrected source manifest and sanitized Windows
qualification evidence were preserved outside Git at
`D:\PAI-Validation\EBO-1-R2-RELEASE-RECORD` (private local storage; do not
commit medical datasets or private operational evidence).

The original R1 acceptance matrix and its proposed native Windows execution
script further below are **retained as the original historical contract and
pre-execution instructions**. Phrases such as "not yet observed" and "not
executed here" in that preserved R1 procedure do **not** override the later
R1/R2 results above. Separate F8/F9/M8 formal acceptance and the global
Pre-M8 gate are not conferred by EBO closure.

## User-facing CLI

Supply a Java 21 runtime classpath with this project and its existing dependencies. The output directory must exist on an M8-supported local filesystem. Exactly one input mode is required:

```powershell
java -cp $runtimeClasspath org.cbihi.mrinormalizer.bootstrap.EboCommand --input-file 'D:\HeldOut\scan_A.nii.gz' --output-root 'D:\Validation\Output' --metadata-profile evaluation
java -cp $runtimeClasspath org.cbihi.mrinormalizer.bootstrap.EboCommand --input-dir 'D:\HeldOut' --output-root 'D:\Validation\Output' --metadata-profile evaluation
```

The `evaluation` profile is **caller-selected non-clinical template assumptions**, *not recovered acquisition facts*. It supplies `mrOriginConfirmed=true`, `bipedCoordinatesConfirmed=true`, `scanningSequence=SE`, and `sequenceVariant=NONE` solely for non-clinical evaluation outputs. This does **not** claim SE is the original sequence nor that NONE is an unknown-value default; it is a specified synthetic template value. No metadata sidecars are required. Use the original `M8Command` for workflows requiring independently confirmed acquisition information.

Directory mode scans only top-level `.nii` / `.nii.gz` files, case-insensitively; unrelated regular files are ignored. At most 256 candidates; sorted by Java ordinal filename ordering. Discovered matching symlinks, colliding logical filenames (casefolded NFC), hard links to the same file, or unsafe entries cause preflight rejection before any M8 job or EBO report is created. EboCommand discovers exactly once and executes precisely that immutable, preflight-validated ordered list. Input and output directories must not overlap in directory mode. Inputs are never changed. Only one M8 job executes at a time. Each uses an independent UUID, workspace, planner, writer and verifier. Successful jobs publish `m8-<uuid>` directories with `image-*.dcm` and `PAI-M8-COMPLETE.json`. The frozen M8 engine may additionally retain `.pai-m8-<jobId>` private journal directories; their contents are not public DICOM instances or public completion markers and must never be included in EBO result-count verification. One `ebo-<batch-uuid>-summary.json` is published at output root through same-filesystem no-replace hardlink and read-back verification; the report maps each input name to its M8 output. Reruns preserve prior series and reports.

## Failure policy

`SUCCESS` requires `COMPLETE_BYTES_VERIFIED`. `REJECTED` or `FAILED` with `NONE` publication is recorded and the next file runs. `RECOVERY_REQUIRED`, any inconsistent M8 publication state, any uncaught runtime exception or output uncertainty stops the batch. Every subsequently discovered file appears as `NOT_ATTEMPTED`. An interruption without such uncertainty stops the batch with `INTERRUPTED`. A report-writing failure returns exit code 5; previously published M8 outputs remain untouched. No automatic retry or cleanup of uncertain artifacts.

**Exit codes:** 0 complete success; 2 invalid/preflight request; 3 ordinary total failure; 4 partial success; 5 recovery/report uncertainty; 130 interruption. EBO exit codes do not change the existing M8 CLI exit codes.

**Report schema:** `PAI-EBO-REPORT-1.0`; includes batchId, inputMode, metadataProfile and explicit template disclosure, discoveredCount, attemptedCount, successCount, rejectedCount, failedCount, recoveryRequiredCount, unattemptedCount, overallStatus, plus one ordinal-aligned entry per candidate. Entries contain input, jobId (nullable), state, m8Status, publication, comparedSlices, outputDirectory and typed faults. Report names are relative; voxel data, absolute source paths, and exception text are not stored; input filenames may themselves be sensitive.

## Frozen acceptance matrix — 27 obligations

| ID | Obligation | Test / evidence to obtain |
|---|---|---|
| T01 | Single CLI mode | `EboCommandTest.t01_t05_t14_singleRealJobAndExplicitTemplate` |
| T02 | Directory CLI mode | `EboCommandTest.t02_t15_directoryCreatesIndependentSeries` |
| T03 | Invalid/missing/duplicate flags | `EboCommandTest.t03_t04_t06_usageIsFailClosed` |
| T04 | Invalid metadata profile | `EboCommandTest.t03_t04_t06_usageIsFailClosed` |
| T05 | Explicit metadata assumptions | `EboCommandTest.t01_t05_t14_singleRealJobAndExplicitTemplate`, report test |
| T06 | Missing/invalid roots | `EboCommandTest.t03_t04_t06_usageIsFailClosed` |
| T07 | Mixed extension discovery | `EboBatchControllerTest.t07_t08_t09_t10_discoverOnlyCandidatesWithStableOrder` |
| T08 | Case-varied extension discovery | Same discovery test |
| T09 | Ignore unrelated filenames | Same discovery test |
| T10 | Stable ordering | Same discovery test |
| T11 | Empty/overlimit | `EboBatchControllerTest.t11_t12_preflightRejectsEmptyManyLinksAndOverlap` |
| T12 | Symlinks/unsafe/overlap, hard-link aliases, logical-name collisions | Preflight test plus `EboCommandTest.t12_hardLinkDuplicatesRejectedBeforeAnyM8JobOrReport` and `t12_logicalNameCollisionsRejectedWhenFilesystemPermitsThem`; requires symlink-capable provider for symlink assertion |
| T13 | Input immutability | `EboBatchControllerTest.t13_t16_t17_t18_t21_t22_safeFailuresContinueSequentially`; real-input SHA gate |
| T14 | One real M8 series | `EboCommandTest.t01_t05_t14_singleRealJobAndExplicitTemplate` |
| T15 | Multiple actual M8 series | `EboCommandTest.t02_t15_directoryCreatesIndependentSeries` |
| T16 | Sequential execution | `EboBatchControllerTest.t13_t16_t17_t18_t21_t22_safeFailuresContinueSequentially` |
| T17 | Independent UUIDs | Same controller test |
| T18 | Existing outputs unchanged on rerun | Controller test plus Windows real-data rerun check |
| T19 | Four scalar types | `EboCommandTest.t19_t20_scalarEndiannessCompressionAndDepth` |
| T20 | Compression/endianness | Same 32-case test |
| T21 | Success-reject-success | Controller test |
| T22 | Ordinary failed continuation | Controller test, distinct failure assertion |
| T23 | Recovery halt | `EboBatchControllerTest.t23_t24_recoveryAndUnexpectedExceptionsHaltWithUnattemptedEntries` |
| T24 | Escaping exceptions/interruption | Controller test, interruption assertion |
| T25 | Report counts/schema | `EboReportWriterTest.t25_reportReconcilesAndDeclaresTemplateAssumptions` |
| T26 | No report overwrite/report error | `EboReportWriterTest.t26_existingReportIsNotOverwritten` |
| T27 | Full regression + actual native Windows runs | Mandatory external qualification; no synthetic or Python substitute |

Do not call any obligation demonstrated until its named test and relevant assertions have actually executed on a supported Java environment. Platform-specific checks require the native provider. A failure or unexecuted gate must be recorded explicitly; passing old M8 regression does not certify EBO.

## Native Windows qualification — required, not assumed

Use a **distinct clean input directory** containing only the four anatomical volumes `BraTS2021_01061_{flair,t1,t1ce,t2}.nii.gz`, excluding `seg`; create a separate empty output directory. Compute input SHA-256 hashes before and after. Execute `EboCommand` single FLAIR and then `EboCommand --input-dir` through the same built Java application (different output roots). The expected *not yet observed* outputs are 155 **public** `.dcm` + one **public** M8 marker for single and 620 **public** `.dcm` + four **public** M8 markers over four distinct series for batch. The verification script validates only the public series directories declared in the EBO report, checks job-ID/output mapping, and excludes retained `.pai-m8-*` private journal contents. Run focused JUnit tests and full Maven regression with the already provisioned native Windows F6 fixtures. Record the exact Java runtime, executed commit/tree state, console logs, hashes, reports and counts. Never ship medical images in the source handoff.

### Copy-and-run PowerShell 5.1 qualification procedure (not executed here)

Use this on the user's native Windows machine **after** applying the 11-path EBO patch to the exact remote checkpoint and successfully building the unchanged Maven project. It does not modify the input dataset or source repository. Run as a single `.ps1` script rather than separately pasting `if` and `else` blocks. Run against an existing validated Maven runtime classpath file.

```powershell
$ErrorActionPreference = 'Stop'
$repository = 'D:\Projects\PAI-M8-Windows-NTFS-Trial'
$braTs = 'D:\PAI-Validation\BraTS2021_01061\BraTS2021_01061'
$validation = 'D:\PAI-Validation\EBO-1-Qualification'
$selected = Join-Path $validation 'HeldOut-4'
$single = Join-Path $validation 'Single-Out'
$batch = Join-Path $validation 'Batch-Out'
$log = Join-Path $validation 'Logs'
$names = @('flair','t1','t1ce','t2')

foreach ($p in @($validation,$selected,$single,$batch,$log)) {
    if (-not (Test-Path -LiteralPath $p)) { New-Item -ItemType Directory -Path $p | Out-Null }
}
if (@(Get-ChildItem -LiteralPath $selected -Force).Count -gt 0 -or
    @(Get-ChildItem -LiteralPath $single -Force).Count -gt 0 -or
    @(Get-ChildItem -LiteralPath $batch -Force).Count -gt 0) {
    throw 'STOP: Fresh input staging and output directories required; no deletion or overwrite authorized.'
}

$before = @{}
foreach ($modality in $names) {
    $name = 'BraTS2021_01061_' + $modality + '.nii.gz'
    $original = Join-Path $braTs $name
    if (-not (Test-Path -LiteralPath $original)) { throw "Missing $name" }
    $before[$name] = (Get-FileHash -LiteralPath $original -Algorithm SHA256).Hash
    Copy-Item -LiteralPath $original -Destination (Join-Path $selected $name)
}

Set-Location $repository
$dependencies = (Get-Content 'target\m8-runtime-classpath.txt' -Raw).Trim()
$classpath = (Resolve-Path 'target\classes').Path + [IO.Path]::PathSeparator + $dependencies

function Invoke-Ebo($label, [string[]]$eboArgs) {
    $stdout = Join-Path $log ($label + '-stdout.log')
    $stderr = Join-Path $log ($label + '-stderr.log')
    $arguments = @('-cp',$classpath,'org.cbihi.mrinormalizer.bootstrap.EboCommand') + $eboArgs
    $proc = Start-Process -FilePath java.exe -ArgumentList $arguments -Wait -PassThru -NoNewWindow `
        -RedirectStandardOutput $stdout -RedirectStandardError $stderr
    Get-Content -LiteralPath $stdout
    Get-Content -LiteralPath $stderr
    if ($proc.ExitCode -ne 0) { throw "EBO $label failed with exit code $($proc.ExitCode)" }
}

$flair = Join-Path $selected 'BraTS2021_01061_flair.nii.gz'
Invoke-Ebo 'Single' @('--input-file',$flair,'--output-root',$single,'--metadata-profile','evaluation')
Invoke-Ebo 'Batch' @('--input-dir',$selected,'--output-root',$batch,'--metadata-profile','evaluation')

$specs = @(
    [pscustomobject]@{ Root=$single; Series=1; Dcm=155; ExpectedInputs=@('BraTS2021_01061_flair.nii.gz') },
    [pscustomobject]@{ Root=$batch; Series=4; Dcm=620; ExpectedInputs=@($names | ForEach-Object { 'BraTS2021_01061_' + $_ + '.nii.gz' }) }
)
foreach ($spec in $specs) {
    $root = $spec.Root; $expectedSeries = $spec.Series; $expectedDcm = $spec.Dcm
    # Inspect the one EBO report first. M8 retains .pai-m8-* private journals;
    # never count markers or DICOM files recursively across the output root.
    $reports = @(Get-ChildItem -LiteralPath $root -File -Filter 'ebo-*-summary.json')
    if ($reports.Count -ne 1) { throw "Expected exactly one EBO report in $root" }
    $report = Get-Content -LiteralPath $reports[0].FullName -Raw | ConvertFrom-Json
    if ($report.successCount -ne $expectedSeries -or $report.attemptedCount -ne $expectedSeries -or
        $report.discoveredCount -ne $expectedSeries -or $report.rejectedCount -ne 0 -or
        $report.failedCount -ne 0 -or $report.unattemptedCount -ne 0 -or
        $report.overallStatus -ne 'SUCCESS' -or @($report.entries).Count -ne $expectedSeries) {
        throw "EBO summary count mismatch in $root"
    }
    if ($report.metadataProfile.name -cne 'evaluation' -or
        $report.metadataProfile.scanningSequence -cne 'SE' -or
        $report.metadataProfile.sequenceVariant -cne 'NONE' -or
        $report.metadataProfile.mrOriginConfirmed -ne $true -or
        $report.metadataProfile.bipedCoordinatesConfirmed -ne $true -or
        $report.metadataProfile.source -notmatch 'caller-selected non-clinical template assumptions') {
        throw "EBO evaluation metadata disclosure missing from $root"
    }
    $actualInputs = @($report.entries | ForEach-Object { $_.input } | Sort-Object -CaseSensitive)
    $expectedInputs = @($spec.ExpectedInputs | Sort-Object -CaseSensitive)
    if (@(Compare-Object -ReferenceObject $expectedInputs -DifferenceObject $actualInputs -CaseSensitive).Count -ne 0) {
        throw "Incorrect NIfTI-to-report mapping in $root"
    }
    $seenOutputs = @{}
    $seenJobs = @{}
    $dcmTotal = 0
    $markerTotal = 0
    foreach ($entry in $report.entries) {
        if ($entry.state -cne 'SUCCESS' -or $entry.m8Status -cne 'SUCCESS' -or
            $entry.publication -cne 'COMPLETE_BYTES_VERIFIED' -or $entry.comparedSlices -ne 155 -or
            -not $entry.jobId -or $entry.jobId -notmatch '^[0-9a-fA-F-]{36}$') {
            throw "Unverified EBO entry for $($entry.input)"
        }
        $expectedDirectory = 'm8-' + $entry.jobId
        if ($entry.outputDirectory -cne $expectedDirectory -or $seenOutputs.ContainsKey($expectedDirectory) -or
            $seenJobs.ContainsKey($entry.jobId)) {
            throw "Missing or conflicting EBO job/output identity for $($entry.input)"
        }
        $seenOutputs[$expectedDirectory] = $true
        $seenJobs[$entry.jobId] = $true
        $publicPath = Join-Path $root $expectedDirectory
        if (-not (Test-Path -LiteralPath $publicPath -PathType Container)) {
            throw "Missing public M8 series: $publicPath"
        }
        $dcmFiles = @(Get-ChildItem -LiteralPath $publicPath -File | Where-Object {
            $_.Name -match '^image-[0-9]{6}\.dcm$'
        })
        $publicMarkers = @(Get-ChildItem -LiteralPath $publicPath -File -Filter 'PAI-M8-COMPLETE.json')
        if ($dcmFiles.Count -ne 155 -or $publicMarkers.Count -ne 1) {
            throw "Public series inventory mismatch: $publicPath"
        }
        for ($slice=1; $slice -le 155; $slice++) {
            $sliceName = 'image-{0:D6}.dcm' -f $slice
            if (-not (Test-Path -LiteralPath (Join-Path $publicPath $sliceName) -PathType Leaf)) {
                throw "Missing expected public slice: $sliceName in $publicPath"
            }
        }
        $dcmTotal += $dcmFiles.Count
        $markerTotal += $publicMarkers.Count
        Write-Host "MAPPED $($entry.input) => $expectedDirectory (155 public DICOM + 1 public marker)"
    }
    $publicDirectories = @(Get-ChildItem -LiteralPath $root -Directory | Where-Object { $_.Name -like 'm8-*' })
    if ($seenOutputs.Count -ne $expectedSeries -or $publicDirectories.Count -ne $expectedSeries -or
        $dcmTotal -ne $expectedDcm -or $markerTotal -ne $expectedSeries) {
        throw "Public inventory/count reconciliation failed for $root"
    }
    Write-Host "VERIFIED $root : $expectedSeries public series, $dcmTotal public DICOM instances, $markerTotal public markers"
}
foreach ($modality in $names) {
    $name = 'BraTS2021_01061_' + $modality + '.nii.gz'
    if ($before[$name] -ne (Get-FileHash -LiteralPath (Join-Path $braTs $name) -Algorithm SHA256).Hash -or
        $before[$name] -ne (Get-FileHash -LiteralPath (Join-Path $selected $name) -Algorithm SHA256).Hash) {
        throw "Source SHA-256 mismatch: $name"
    }
}
Write-Host 'EBO-1 SINGLE AND BATCH NATIVE WINDOWS COUNTS AND SOURCE HASHES VERIFIED'
```

A separate DICOM parser must independently inspect output pixel data and geometry if independent scientific equivalence is required. This script verifies EBO execution, M8-reported completion, file inventory, input-to-output mapping and input immutability, not independently the entire voxel array.

---

## Later integration reference — 2026-11-07 (non-contractual postscript)

The statement above that `main` had not been merged was **accurate at the
2026-11-02 EBO-1 R2 checkpoint**. Subsequently, [PR #1](https://github.com/nabojwal/CS5013-MRI-Volume-Normalizer/pull/1)
merged the integrated Windows MRI conversion/GUI prototype into `main` at
`b032a51cd369b428f201c6b0dd8d23a49f195eec` on **2026-11-07**.
That later integration did **not** change the frozen R2 acceptance outcome:
**27/27 obligations accepted within the non-clinical evaluation scope**,
with the previously reported **888/888** regression as dated evidence.
It did not turn the R1 BraTS demonstration into an R2 rerun, establish
independent formal M8 acceptance, or close F8/F9, F10/F11 or the global
Pre-M8 gate. For the integrated state and its separately reported Windows
**961/961** test result, see the [canonical tracker](PRE-M8-FOUNDATION-TRACKER.md)
and [test results](../testing/TEST-RESULTS.md). This postscript does not
amend the EBO-1 R2 contract, execution record, qualification or approval.
