# Run from Windows PowerShell 5.1+ AFTER committing Docker files.
# No administrative access to the GitHub remote or Google Drive is required.
# A FAIL never becomes passing submission evidence.
param([switch]$SkipSave)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Push-Location $root
try {
    $paths = @('Dockerfile','.dockerignore','docker/entrypoint.sh',
        'docker/make_fixture.py','docker/verify_demo.py',
        'docker/verify-docker.ps1','docker/DOCKER-SUBMISSION.md')
    foreach ($path in $paths) {
        if (-not (Test-Path -LiteralPath $path)) { throw "Missing required path: $path" }
        $listed = (& git ls-files -- $path) | Out-String
        if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($listed)) {
            throw "Commit the Docker bundle before building: $path is not tracked by Git."
        }
    }
    $scopeStatus = (& git status --porcelain -- Dockerfile .dockerignore docker) | Out-String
    if ($LASTEXITCODE -ne 0 -or -not [string]::IsNullOrWhiteSpace($scopeStatus)) {
        throw "Docker bundle contains uncommitted changes. Commit them first:`n$scopeStatus"
    }
    $sha = (& git rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0 -or $sha -notmatch '^[0-9a-f]{40}$') {
        throw 'Cannot read a full source commit SHA.'
    }
    $image = "pai-mri:$sha"
    Write-Host "Source commit: $sha"
    Write-Host "Docker image:  $image"

    $osType = (& docker info --format '{{.OSType}}' 2>&1) | Out-String
    if ($LASTEXITCODE -ne 0) { throw "Docker Desktop is not available:`n$osType" }
    if ($osType.Trim() -ne 'linux') {
        throw 'Select Linux containers mode in Docker Desktop before this build.'
    }
    & docker build --pull --build-arg "GIT_SHA=$sha" --tag $image .
    if ($LASTEXITCODE -ne 0) { throw 'Docker image build failed.' }

    $evidenceDir = Join-Path $root 'docker-evidence'
    New-Item -ItemType Directory -Path $evidenceDir -Force | Out-Null
    $stdout = Join-Path $evidenceDir 'demo.stdout.log'
    $stderr = Join-Path $evidenceDir 'demo.stderr.log'
    # Start-Process avoids Windows PowerShell native stderr promotion, and
    # produces separate stdout/stderr logs with an explicit numeric exit.
    $p = Start-Process -FilePath 'docker.exe' -ArgumentList @('run','--rm',$image) `
        -Wait -PassThru -NoNewWindow -RedirectStandardOutput $stdout `
        -RedirectStandardError $stderr
    $code = $p.ExitCode
    Get-Content -LiteralPath $stdout
    if ((Get-Item $stderr).Length -gt 0) { Get-Content -LiteralPath $stderr }
    if ($code -ne 0) {
        throw "Docker demo exited with $code. Inspect docker-evidence; no passing image has been packaged."
    }
    $output = Get-Content -Raw -LiteralPath $stdout
    if ($output -notmatch 'PAI_DOCKER_DEMO_PASS' -or
        $output -notmatch 'PAI_INDEPENDENT_NIFTI_PASS' -or
        $output -notmatch 'FBO_STATUS=SUCCESS' -or
        $output -notmatch 'status=SUCCESS') {
        throw 'Container exited zero without all required end-to-end PASS signals.'
    }
    # The independent DICOM verifier writes its PASS marker on stderr.
    $errtext = Get-Content -Raw -LiteralPath $stderr
    if ($errtext -notmatch 'PAI_INDEPENDENT_DICOM_PASS') {
        throw 'Independent DICOM payload verifier PASS marker missing.'
    }

    $inspectJson = (& docker image inspect $image | Out-String)
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect Docker image.' }
    $inspect = @($inspectJson | ConvertFrom-Json)
    if ($inspect.Count -ne 1) { throw 'Expected one image inspection record.' }
    $revision = $inspect[0].Config.Labels.'org.opencontainers.image.revision'
    if ($revision -ne $sha) { throw 'Docker image label does not match Git HEAD.' }
    $imageId = $inspect[0].Id
    $imageBytes = 'Not exported (SkipSave)'
    $archiveSha = 'Not exported (SkipSave)'
    $tar = Join-Path $evidenceDir ("pai-mri-$sha.tar")
    if (-not $SkipSave) {
        & docker save --output $tar $image
        if ($LASTEXITCODE -ne 0) { throw 'Docker image archive export failed.' }
        $info = Get-Item -LiteralPath $tar
        $imageBytes = $info.Length
        $archiveSha = (Get-FileHash -Algorithm SHA256 -LiteralPath $tar).Hash
        Write-Host "Image tar: $tar"
        Write-Host "SHA-256:   $archiveSha"
    }
    $when = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
    $record = @"
# Project PAI - Docker demo evidence (local, user-executed)

- UTC execution time: $when
- Source commit: $sha
- Image tag: $image
- Image ID: $imageId
- Container command: docker run --rm $image
- Container process exit: $code
- Synthetic EBO reverse conversion: SUCCESS (see demo.stdout.log)
- Independent derived DICOM voxel/geometry validation: PASS (see demo.stderr.log)
- Synthetic FBO forward conversion: SUCCESS (see demo.stdout.log)
- Independent NIfTI voxel/affine validation: PASS (see demo.stdout.log)
- Docker image export: $tar
- Docker image tar bytes: $imageBytes
- Docker image tar SHA-256: $archiveSha
- Limit: single deterministic, 4x3x3 UINT16 research fixture only; **not** an external MRI, general DICOM, clinical, GUI or cross-platform acceptance test.
- Distribution: upload the actual `.tar` file to Google Drive; set course staff Google group view access; verify download permissions. Do not upload private MRI fixtures.
- Final submission: rebuild on the final tagged commit and ensure its full SHA equals the image tag and report header.
"@
    Set-Content -LiteralPath (Join-Path $evidenceDir 'EVIDENCE.md') -Value $record -Encoding UTF8
    Write-Host 'PAI_DOCKER_SUBMISSION_EVIDENCE_READY'
    Write-Host "Evidence files: $evidenceDir"
} finally {
    Pop-Location
}
