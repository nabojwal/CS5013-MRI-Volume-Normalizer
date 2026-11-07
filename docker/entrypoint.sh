#!/bin/sh
set -eu

JAVA_CP='/opt/pai/classes:/opt/pai/lib/*'
run_ebo() {
    java -Djava.awt.headless=true -cp "$JAVA_CP" \
      org.cbihi.mrinormalizer.bootstrap.EboCommand "$@"
}
run_fbo() {
    java -Djava.awt.headless=true -cp "$JAVA_CP" \
      org.cbihi.mrinormalizer.bootstrap.FboCommand "$@"
}

demo() {
    echo 'PAI_DOCKER_DEMO_START: synthetic 3D NIfTI -> derived DICOM -> NIfTI'
    # The production M8 adapter explicitly requires ext4 (not overlay or
    # Docker Desktop's Windows bind-mount provider). Never weaken this check.
    python3 -B /opt/pai/verify_demo.py filesystem /work
    if [ -n "$(find /work -mindepth 1 -maxdepth 1 -print -quit)" ]; then
        echo 'ERROR: /work must be a fresh, empty Docker volume for the demo.' >&2
        exit 31
    fi
    mkdir -p /work/source /work/reverse /work/forward-source/demo-series /work/forward-output
    cp /opt/pai/fixture/demo.nii /work/source/demo.nii
    sha256sum /work/source/demo.nii

    echo 'PAI_DEMO_STAGE=reverse EBO'
    run_ebo --input-file /work/source/demo.nii \
      --output-root /work/reverse --metadata-profile evaluation \
      > /work/ebo.stdout 2> /work/ebo.stderr || {
        code=$?
        cat /work/ebo.stdout /work/ebo.stderr >&2
        echo "PAI_DOCKER_DEMO_FAIL: EBO exit=$code; output kept in /work" >&2
        exit "$code"
      }
    cat /work/ebo.stdout
    test ! -s /work/ebo.stderr || cat /work/ebo.stderr >&2
    grep -q 'status=SUCCESS' /work/ebo.stdout || {
        echo 'PAI_DOCKER_DEMO_FAIL: EBO did not report SUCCESS' >&2
        exit 32
    }

    echo 'PAI_DEMO_STAGE=independent_DICOM_verification'
    uid=$(python3 -B /opt/pai/verify_demo.py dicom \
      /work/source/demo.nii /work/reverse /work/forward-source/demo-series)
    echo 'PAI_DEMO_STAGE=forward FBO'
    run_fbo --input-root /work/forward-source \
      --output-root /work/forward-output --series demo-series "$uid" \
      > /work/fbo.stdout 2> /work/fbo.stderr || {
        code=$?
        cat /work/fbo.stdout /work/fbo.stderr >&2
        echo "PAI_DOCKER_DEMO_FAIL: FBO exit=$code; output kept in /work" >&2
        exit "$code"
      }
    cat /work/fbo.stdout
    test ! -s /work/fbo.stderr || cat /work/fbo.stderr >&2
    grep -q 'FBO_STATUS=SUCCESS' /work/fbo.stdout || {
        echo 'PAI_DOCKER_DEMO_FAIL: FBO did not report SUCCESS' >&2
        exit 33
    }

    echo 'PAI_DEMO_STAGE=independent_NIfTI_verification'
    python3 -B /opt/pai/verify_demo.py nifti \
       /work/source/demo.nii /work/forward-output
    sha256sum /work/source/demo.nii
    echo 'PAI_DOCKER_DEMO_PASS: EBO/FBO exit 0; independent voxel and geometry checks passed'
}

case "${1:-demo}" in
  demo)
    shift || true
    if [ "$#" -ne 0 ]; then echo 'Unexpected demo arguments' >&2; exit 2; fi
    demo
    ;;
  ebo)
    shift
    run_ebo "$@"
    ;;
  fbo)
    shift
    run_fbo "$@"
    ;;
  help|-h|--help)
    cat <<'USAGE'
Project PAI (headless, nonclinical) — commands:
  docker run --rm IMAGE                 # self-contained reverse+forward demo
  docker run --rm IMAGE demo            # same
  docker run --rm -v VOLUME:/work IMAGE ebo --input-file /work/source/X.nii --output-root /work/reverse --metadata-profile evaluation
  docker run --rm -v VOLUME:/work IMAGE fbo --input-root /work/forward-source --output-root /work/forward-output --series SERIES_DIR SERIES_UID
The Swing GUI runs natively with Launch-PAI.cmd, not in this Linux image.
USAGE
    ;;
  *)
    echo 'Unknown command. Use help, demo, ebo or fbo.' >&2
    exit 2
    ;;
esac
