param(
  [Parameter(Mandatory=$true)][string]$Repo,
  [Parameter(Mandatory=$true)][string]$Evidence,
  [Parameter(Mandatory=$true)][string]$CrossStoreRoot,
  [Parameter(Mandatory=$true)][string]$UncRoot,
  [Parameter(Mandatory=$true)][string]$MappedRoot,
  [Parameter(Mandatory=$true)][string]$CandidateManifest,
  [Parameter(Mandatory=$true)][string]$CandidateManifestSha256,
  [Parameter(Mandatory=$true)][string]$NativeFixtureProfile
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repoPath = (Resolve-Path -LiteralPath $Repo).Path
if (Test-Path -LiteralPath $Evidence) { throw 'Evidence directory must be new and empty; choose a fresh path.' }
$manifestPath = (Resolve-Path -LiteralPath $CandidateManifest).Path
$actualManifestHash = (Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash.ToLowerInvariant()
$expectedManifestHash = $CandidateManifestSha256.Trim().ToLowerInvariant()
if ($expectedManifestHash -notmatch '^[0-9a-f]{64}$') { throw 'Invalid manifest digest input.' }
if ($actualManifestHash -cne $expectedManifestHash) { throw 'Reviewed manifest SHA-256 mismatch.' }
New-Item -ItemType Directory -Path $Evidence | Out-Null
$evidencePath = (Resolve-Path -LiteralPath $Evidence).Path
if ($evidencePath.StartsWith($repoPath, [StringComparison]::OrdinalIgnoreCase)) { throw 'Evidence must be outside repository.' }
$expectedParent = '1f00ce6e5213be841aa32100aa891754d549afd4'
$head = (& git -C $repoPath rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or $head -cne $expectedParent) { throw 'Wrong M8 parent' }
& git -C $repoPath diff --quiet HEAD --
if ($LASTEXITCODE -ne 0) { throw 'Protected predecessor changes found' }
& git -C $repoPath diff --cached --quiet
if ($LASTEXITCODE -ne 0) { throw 'Unexpected staged changes found' }
$expectedPaths = @('docs/architecture/M8-DICOM-CONFORMANCE-PROFILE.md',
'scripts/m8/Verify-M8-Windows.ps1',
'scripts/m8/check-m8-output.py',
'scripts/m8/verify-m8-linux.sh',
'src/main/java/org/cbihi/mrinormalizer/application/conversion/M8GeometryPolicy.java',
'src/main/java/org/cbihi/mrinormalizer/application/m8/M8Fault.java',
'src/main/java/org/cbihi/mrinormalizer/application/m8/M8Limits.java',
'src/main/java/org/cbihi/mrinormalizer/application/m8/M8Metadata.java',
'src/main/java/org/cbihi/mrinormalizer/application/m8/M8Plan.java',
'src/main/java/org/cbihi/mrinormalizer/application/m8/M8Request.java',
'src/main/java/org/cbihi/mrinormalizer/application/m8/M8Result.java',
'src/main/java/org/cbihi/mrinormalizer/application/port/out/DicomMrSliceVerifier.java',
'src/main/java/org/cbihi/mrinormalizer/application/port/out/DicomMrSliceWriter.java',
'src/main/java/org/cbihi/mrinormalizer/application/port/out/M8Workspace.java',
'src/main/java/org/cbihi/mrinormalizer/application/service/M8ConversionService.java',
'src/main/java/org/cbihi/mrinormalizer/bootstrap/M8Command.java',
'src/main/java/org/cbihi/mrinormalizer/infrastructure/dicom/Dcm4cheMrSliceVerifier.java',
'src/main/java/org/cbihi/mrinormalizer/infrastructure/dicom/Dcm4cheMrSliceWriter.java',
'src/main/java/org/cbihi/mrinormalizer/infrastructure/filesystem/NioM8Workspace.java',
'src/main/java/org/cbihi/mrinormalizer/infrastructure/provenance/M8JournalCodec.java',
'src/test/java/org/cbihi/mrinormalizer/architecture/M8BoundaryTest.java',
'src/test/java/org/cbihi/mrinormalizer/infrastructure/filesystem/M8RecoveryTest.java',
'src/test/java/org/cbihi/mrinormalizer/infrastructure/filesystem/M8WorkspaceTest.java',
'src/test/java/org/cbihi/mrinormalizer/m8/M8CommandTest.java',
'src/test/java/org/cbihi/mrinormalizer/m8/M8ConversionIntegrationTest.java',
'src/test/java/org/cbihi/mrinormalizer/m8/M8DicomEncodingTest.java',
'src/test/java/org/cbihi/mrinormalizer/m8/M8GeometryTest.java',
'src/test/java/org/cbihi/mrinormalizer/m8/M8JournalTest.java',
'src/test/java/org/cbihi/mrinormalizer/m8/M8ModelTest.java')
if ($expectedPaths.Count -ne 29) { throw 'Incorrect frozen path budget' }
foreach($relative in $expectedPaths) {
  $absolute = Join-Path $repoPath ($relative.Replace('/',[IO.Path]::DirectorySeparatorChar))
  if(-not (Test-Path -LiteralPath $absolute -PathType Leaf)) { throw "M8 path missing: $relative" }
  & git -C $repoPath ls-files --error-unmatch -- $relative *> $null
  if ($LASTEXITCODE -eq 0) { throw "Already tracked M8 path: $relative" }
}
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
if ($manifest.parent -cne $expectedParent -or $manifest.files.Count -ne 29) { throw 'Reviewed manifest parent/count mismatch' }
$manifestPaths = @($manifest.files | ForEach-Object { $_.path })
if (@(Compare-Object $expectedPaths $manifestPaths).Count -ne 0) { throw 'Reviewed manifest paths mismatch' }
function Assert-Reviewed-Content {
  foreach ($entry in $manifest.files) {
    $file = Join-Path $repoPath ($entry.path.Replace('/', [IO.Path]::DirectorySeparatorChar))
    $bytes = (Get-Item -LiteralPath $file).Length
    $sha = (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($bytes -ne $entry.size -or $sha -cne $entry.sha256) { throw "Reviewed file mismatch: $($entry.path)" }
    $blob = (& git -C $repoPath hash-object -- $file).Trim()
    if ($LASTEXITCODE -ne 0 -or $blob -cne $entry.git_blob) { throw "Git blob mismatch: $($entry.path)" }
  }
}
Assert-Reviewed-Content
$expectedPaths | Set-Content (Join-Path $evidencePath 'authorized-paths.txt')
foreach($p in @($CrossStoreRoot,$UncRoot,$MappedRoot)) { if(-not (Test-Path -LiteralPath $p)) { throw "Required fixture unavailable: $p" } }
if (-not $NativeFixtureProfile) { throw 'Native fixture profile required' }
& java -version 2> "$evidencePath\java-version.txt"
if ($LASTEXITCODE -ne 0) { throw 'Java unavailable' }
& mvn --version > "$evidencePath\maven-version.txt"
if ($LASTEXITCODE -ne 0) { throw 'Maven unavailable' }
if ((Get-Content "$evidencePath\maven-version.txt" -First 1) -notmatch '3\.9\.16') { throw 'Maven 3.9.16 required' }
$version = (Get-Content "$evidencePath\java-version.txt" -First 1)
if ($version -notmatch '21\.') { throw 'Java 21 required' }
$files = @(Get-ChildItem -LiteralPath (Join-Path $repoPath 'src') -Filter '*.java' -Recurse -File | Sort-Object FullName)
$before = @($files | Get-FileHash -Algorithm SHA256 | ForEach-Object { "$($_.Hash) $($_.Path)" })
$before | Set-Content "$evidencePath\source-before.txt"
$candidateBefore = @($expectedPaths | ForEach-Object {
  $path = Join-Path $repoPath $_
  $digest = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash
  "$digest $_"
})
$candidateBefore | Set-Content "$evidencePath\m8-29-before.txt"
$native = @(
 "-Df6.test.windowsCrossStoreRoot=$CrossStoreRoot",
 "-Df6.test.windowsUncRoot=$UncRoot",
 "-Df6.test.windowsMappedRoot=$MappedRoot",
 '-Dm8.test.native=true'
)
$common = @('-B','-DargLine=-Xmx2g','-Dmaven.test.failure.ignore=false')
function Invoke-Gate([string]$Name, [string[]]$GateArgs) {
 $log = Join-Path $evidencePath "$Name.log"
 $reportDir = Join-Path $repoPath 'target\surefire-reports'
 if(Test-Path -LiteralPath $reportDir) { Get-ChildItem -LiteralPath $reportDir -Filter 'TEST-*.xml' -ErrorAction SilentlyContinue | Remove-Item -Force }
 & mvn -f (Join-Path $repoPath 'pom.xml') @common @native @GateArgs *> $log
 $exit = $LASTEXITCODE
 "$exit" | Set-Content (Join-Path $evidencePath "$Name.exit")
 $reports = Join-Path $evidencePath "$Name-reports"
 if (Test-Path -LiteralPath $reports) { throw "Stale gate evidence directory: $reports" }
 New-Item -ItemType Directory -Path $reports | Out-Null
 Get-ChildItem (Join-Path $repoPath 'target\surefire-reports') -Filter '*.xml' -ErrorAction SilentlyContinue |
   Copy-Item -Destination $reports
 if ($exit -ne 0) { throw "$Name Maven exit $exit" }
 $xml = @(Get-ChildItem -Path $reports -Filter '*.xml')
 if ($xml.Count -eq 0) { throw "$Name has no Surefire XML" }
 $count = 0
 foreach ($r in $xml) {
   [xml]$report = Get-Content -LiteralPath $r.FullName -Raw
   $suite = $report.testsuite
   if ([int]$suite.errors -ne 0 -or [int]$suite.failures -ne 0 -or [int]$suite.skipped -ne 0) { throw "$Name contains test failure/error/skip" }
   $count += [int]$suite.tests
 }
 if ($count -eq 0) { throw "$Name discovered zero tests" }
 "$count" | Set-Content (Join-Path $evidencePath "$Name.count")
}
Invoke-Gate 'focused' @('-Dtest=M8*','clean','test')
Invoke-Gate 'affected' @('-Dtest=M8*,*Nifti*,*Dicom*,*Conversion*,*Manifest*,*VerifiedCopy*,CopyJournalSequenceTest,*Boundary*','test')
Invoke-Gate 'full' @('clean','test')
$after = @($files | Get-FileHash -Algorithm SHA256 | ForEach-Object { "$($_.Hash) $($_.Path)" })
$after | Set-Content "$evidencePath\source-after.txt"
$candidateAfter = @($expectedPaths | ForEach-Object {
  $path = Join-Path $repoPath $_
  $digest = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash
  "$digest $_"
})
$candidateAfter | Set-Content "$evidencePath\m8-29-after.txt"
if (@(Compare-Object $candidateBefore $candidateAfter).Count -ne 0) { throw 'M8 candidate changed during qualification' }
if (@(Compare-Object $before $after).Count -ne 0) { throw 'Source mutated during qualification' }
Assert-Reviewed-Content
Write-Host 'Windows source and focused/affected/full evidence saved; real MRI demonstration remains separate.'

& git -C $repoPath diff --quiet HEAD --
if ($LASTEXITCODE -ne 0) { throw 'Protected predecessor changed during qualification' }
& git -C $repoPath diff --cached --quiet
if ($LASTEXITCODE -ne 0) { throw 'Unexpected index changes during qualification' }
