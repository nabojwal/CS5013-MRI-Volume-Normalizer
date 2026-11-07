# Project PAI — Docker submission guide (headless evaluation profile)

## Critical scope and status

This bundle **does not replace the Windows Swing/FlatLaf desktop launcher** and
is **not a tested Docker image**. It packages the existing Java 21 Maven
project as a **Linux headless** application, then attempts a deterministic
synthetic NIfTI → derived MR DICOM → NIfTI demonstration using the existing
EBO/FBO CLI entry points. The demonstration is only qualified **after**
executing it on the target Docker Desktop host and checking the logs.

**Do not claim the image passes before running the script.** The code has a
strict non-Windows **ext4 filesystem requirement** for M8. Ordinary Docker
image root filesystems are usually `overlay`; Windows bind mounts often use
shared/virtual filesystems, which are not the accepted M8 source/output root.
The Dockerfile declares `VOLUME /work`, so a normal `docker run` creates a
fresh Linux Docker-managed volume. On systems where that volume isn't ext4,
the demo intentionally fails with a clear error. Do **not** change the M8
filesystem policy solely to make the Docker demonstration pass.

## Prepare the files in your local repository

Copy the contents of this bundle into the root of:

`D:\Projects\CS5013-MRI-Volume-Normalizer`

You should see:

```text
Dockerfile
.dockerignore
docker/entrypoint.sh
docker/make_fixture.py
docker/verify_demo.py
docker/verify-docker.ps1
docker/DOCKER-SUBMISSION.md
```

There is **no Dockerfile in the merged repository baseline**. No repository
production code, tests, schemas or approved contracts are modified by this
bundle. Use Git branching and an ordinary review before merging into `main`:

```powershell
git switch -c feature/docker-submission
git add Dockerfile .dockerignore docker/entrypoint.sh docker/make_fixture.py docker/verify_demo.py docker/verify-docker.ps1 docker/DOCKER-SUBMISSION.md
git commit -m "build: add bounded Docker headless conversion demo and verification"
```

Untracked architecture drafts or private MRI data are **not** to be staged.

## Build, run and verify on Windows Docker Desktop

Requirements: Docker Desktop using **Linux containers**, available disk space,
network access during image build, and PowerShell 5.1 or later. The image uses
official Maven/Java 21 builder and Java 21 runtime images, plus Python 3 only
for the independent demonstration oracle. Docker build resolves the Maven
and configured dcm4che artifact repositories. Internet access is not needed
for the run after the image is built.

After the Docker files are committed, in your repo root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\docker\verify-docker.ps1
```

This script:

1. Checks Docker Linux mode and that Docker bundle files are committed.
2. Creates the image tagged `pai-mri:<full-40-character-Git-HEAD>`.
3. Executes `docker run --rm pai-mri:<full-SHA>` with **no extra arguments**.
4. Checks the EBO and FBO command exit statuses and independent verification
   of all 36 voxel values, DICOM LPS geometry, NIfTI RAS affine, datatype,
   scaling, instance UIDs and image dimensions for the bundled synthetic sample.
5. Writes **actual** stdout/stderr, image SHA, timestamps and test disposition
   under `docker-evidence/` and, only on success, exports a `.tar` archive via
   `docker save`, including its SHA-256 digest.

If anything fails, the script throws an error: **not submission-ready**.
A typical diagnostic is `PAI_WORK_VOLUME_FILESYSTEM=overlay`. This means the
host Docker storage did not meet the existing ext4-only policy; the solution
is not to fake a PASS or override the production filesystem gate.

### Manual equivalent commands

```powershell
$sha = (git rev-parse HEAD).Trim()
docker build --pull --build-arg "GIT_SHA=$sha" -t "pai-mri:$sha" .
docker run --rm "pai-mri:$sha"
docker save -o "pai-mri-$sha.tar" "pai-mri:$sha"
```

**Important:** Manual `docker run` success must still include the
`PAI_DOCKER_DEMO_PASS` marker and independently verified output messages.
A Java startup or image build alone is **not** conversion validation.

The image can also run a manually prepared EBO or FBO command with a
suitable **pre-populated ext4-backed Linux Docker volume**:

```powershell
docker run --rm "pai-mri:$sha" help
# ebo/fbo require explicitly prepared paths and supported input data.
```

Do not use host Windows patient data as a demonstration fixture or upload it
into the image. This demo contains only synthetic nonpatient voxels.

## Save, load, share and rerun the exact image

The verification script exports:

- `docker-evidence/pai-mri-<full-sha>.tar` — actual Docker image archive.
- `docker-evidence/demo.stdout.log` and `demo.stderr.log` — runtime evidence.
- `docker-evidence/EVIDENCE.md` — source/image identity and SHA-256 record.

On the TA's machine, after downloading the actual `.tar` archive:

```powershell
docker load -i .\pai-mri-<full-sha>.tar
docker run --rm pai-mri:<full-sha>
```

Replace `<full-sha>` with the full 40-character commit. No local Java, Maven,
input DICOM archive, patient data or custom environment variables are needed
for this **synthetic headless** demo. The grader still needs Docker and a
supported ext4-backed local volume in their Docker environment.

## CS5013 final-report requirements

Your uploaded LaTeX template calls for:

- A Docker image uploaded to **the team's Google Drive**, with **view access**
  granted to the course staff Google group (address from the course page).
  Upload the real `.tar`, not this repository ZIP or the `Dockerfile` alone.
- A final report header linking that Drive file. The report's
  `Deployment and stakeholder handoff` section must contain the exact
  `docker load` and `docker run` commands demonstrated above.
- A Git tag `final-submission` placed on the **exact full SHA** cited in the
  report header. The image tag and embedded image revision label must agree
  with that SHA.
- A Docker image that actually runs with `docker run <image>` and performs
  the claimed behavior. Confirm this from **recorded executable evidence**.
- No embedded API keys, login tokens, secrets or medical image datasets.

**After merging the Docker branch into `main`**, the commit SHA will usually
change. **Rebuild and rerun** `verify-docker.ps1` from the final `main` commit,
then use only that final SHA and tar file in the report. Once final artifacts
and source are frozen, tag exactly that commit:

```powershell
git switch main
git pull github main
powershell -NoProfile -ExecutionPolicy Bypass -File .\docker\verify-docker.ps1
git tag final-submission (git rev-parse HEAD)
git push github final-submission
```

Only create/push the tag when everything is accepted and ready for submission.
Don't commit local private medical data or `docker-evidence/`.

## Evidence interpretation

A passing synthetic demo proves the **specific container runtime**, Java
command wiring, generation of conventional derived-MR Part-10 slices and a
bounded reconstruction, with independent numeric voxel/geometry checks. It
**does not** prove general real-MRI support, original acquisition metadata
recovery, Windows GUI-in-container, full lossless round trip of DICOM metadata,
or formal F8/F9/M8 foundation acceptance.
