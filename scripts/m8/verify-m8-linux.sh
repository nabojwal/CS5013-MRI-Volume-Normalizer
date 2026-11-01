#!/usr/bin/env bash
# Evidence runner: intentionally aborts instead of silently skipping unavailable native fixtures.
set -euo pipefail
REPO="${1:?usage: verify-m8-linux.sh REPO EVIDENCE MANIFEST MANIFEST_SHA256}"; EVIDENCE="${2:?fresh evidence directory required}"
MANIFEST="${3:?independently reviewed candidate manifest required}"
MANIFEST_SHA256="${4:?independently approved manifest SHA-256 required}"
REPO="$(realpath "$REPO")"
[[ -f "$MANIFEST" ]] || { echo 'Manifest not found' >&2; exit 2; }
MANIFEST="$(realpath "$MANIFEST")"
[[ ! -e "$EVIDENCE" ]] || { echo 'Evidence destination must not already exist' >&2; exit 2; }
mkdir -m 700 -- "$EVIDENCE"
EVIDENCE="$(realpath "$EVIDENCE")"
[[ "$(sha256sum "$MANIFEST" | cut -d' ' -f1)" == "$MANIFEST_SHA256" ]] || { echo 'Reviewed manifest identity mismatch' >&2; exit 2; }
[[ "$EVIDENCE" != "$REPO"* ]] || { echo 'Evidence must be outside repository' >&2; exit 2; }
[[ "$(id -u)" -ne 0 ]] || { echo 'Native Linux qualification requires non-root' >&2; exit 2; }
command -v mvn >/dev/null || { echo 'Maven unavailable' >&2; exit 2; }
[[ "$(java -version 2>&1 | head -1)" == *'21.'* ]] || { echo 'Java 21 required' >&2; exit 2; }
[[ "$(stat -f -c %T "$REPO")" == ext2/ext3 || "$(stat -f -c %T "$REPO")" == ext4 ]] || {
 echo 'Native Linux qualification requires local ext4 repository' >&2; exit 2; }
[[ -w /dev/shm ]] || { echo 'Writable /dev/shm required' >&2; exit 2; }
[[ "$(stat -c %d /dev/shm)" != "$(stat -c %d "$REPO")" ]] || { echo 'Separate /dev/shm FileStore required' >&2; exit 2; }
[[ "$(mvn -version | head -1)" == *'3.9.16'* ]] || { echo 'Maven 3.9.16 required' >&2; exit 2; }
cd "$REPO"
[[ "$(git rev-parse HEAD)" == "1f00ce6e5213be841aa32100aa891754d549afd4" ]] || { echo 'Wrong implementation parent' >&2; exit 2; }
git diff --exit-code --quiet HEAD -- || { echo 'Tracked predecessor changed' >&2; exit 2; }
git diff --cached --exit-code --quiet || { echo 'Index has staged changes' >&2; exit 2; }
EXPECTED=('docs/architecture/M8-DICOM-CONFORMANCE-PROFILE.md' 'scripts/m8/Verify-M8-Windows.ps1' 'scripts/m8/check-m8-output.py' 'scripts/m8/verify-m8-linux.sh' 'src/main/java/org/cbihi/mrinormalizer/application/conversion/M8GeometryPolicy.java' 'src/main/java/org/cbihi/mrinormalizer/application/m8/M8Fault.java' 'src/main/java/org/cbihi/mrinormalizer/application/m8/M8Limits.java' 'src/main/java/org/cbihi/mrinormalizer/application/m8/M8Metadata.java' 'src/main/java/org/cbihi/mrinormalizer/application/m8/M8Plan.java' 'src/main/java/org/cbihi/mrinormalizer/application/m8/M8Request.java' 'src/main/java/org/cbihi/mrinormalizer/application/m8/M8Result.java' 'src/main/java/org/cbihi/mrinormalizer/application/port/out/DicomMrSliceVerifier.java' 'src/main/java/org/cbihi/mrinormalizer/application/port/out/DicomMrSliceWriter.java' 'src/main/java/org/cbihi/mrinormalizer/application/port/out/M8Workspace.java' 'src/main/java/org/cbihi/mrinormalizer/application/service/M8ConversionService.java' 'src/main/java/org/cbihi/mrinormalizer/bootstrap/M8Command.java' 'src/main/java/org/cbihi/mrinormalizer/infrastructure/dicom/Dcm4cheMrSliceVerifier.java' 'src/main/java/org/cbihi/mrinormalizer/infrastructure/dicom/Dcm4cheMrSliceWriter.java' 'src/main/java/org/cbihi/mrinormalizer/infrastructure/filesystem/NioM8Workspace.java' 'src/main/java/org/cbihi/mrinormalizer/infrastructure/provenance/M8JournalCodec.java' 'src/test/java/org/cbihi/mrinormalizer/architecture/M8BoundaryTest.java' 'src/test/java/org/cbihi/mrinormalizer/infrastructure/filesystem/M8RecoveryTest.java' 'src/test/java/org/cbihi/mrinormalizer/infrastructure/filesystem/M8WorkspaceTest.java' 'src/test/java/org/cbihi/mrinormalizer/m8/M8CommandTest.java' 'src/test/java/org/cbihi/mrinormalizer/m8/M8ConversionIntegrationTest.java' 'src/test/java/org/cbihi/mrinormalizer/m8/M8DicomEncodingTest.java' 'src/test/java/org/cbihi/mrinormalizer/m8/M8GeometryTest.java' 'src/test/java/org/cbihi/mrinormalizer/m8/M8JournalTest.java' 'src/test/java/org/cbihi/mrinormalizer/m8/M8ModelTest.java')
[[ "${#EXPECTED[@]}" -eq 29 ]] || exit 2
for path in "${EXPECTED[@]}"; do
  [[ -f "$path" ]] || { echo 'Required M8 path missing' >&2; exit 2; }
  if git ls-files --error-unmatch -- "$path" >/dev/null 2>&1; then
    echo 'New M8 path already tracked; wrong implementation parent' >&2; exit 2
  fi
done
# Preserve unrelated untracked local material; enumerate only authorized candidate files.
# Bind every candidate file to the immutable reviewed manifest, not merely its
# own before/after hash. The manifest itself is SHA-256-pinned by the caller.
python3 - "$REPO" "$MANIFEST" "${EXPECTED[@]}" <<'PYMANIFEST'
import hashlib,json,pathlib,subprocess,sys
root=pathlib.Path(sys.argv[1]); data=json.loads(pathlib.Path(sys.argv[2]).read_text())
expected=set(sys.argv[3:]); items=data.get('files')
if data.get('parent')!='1f00ce6e5213be841aa32100aa891754d549afd4' or not isinstance(items,list) or len(items)!=29:
    sys.exit('Invalid reviewed manifest baseline/count')
if {x['path'] for x in items}!=expected:sys.exit('Manifest path scope mismatch')
for x in items:
    raw=(root/x['path']).read_bytes()
    if len(raw)!=x['size'] or hashlib.sha256(raw).hexdigest()!=x['sha256']:
        sys.exit('Candidate content mismatch: '+x['path'])
    blob=subprocess.check_output(['git','-C',str(root),'hash-object','--',str(root/x['path'])],text=True).strip()
    if blob!=x['git_blob']:sys.exit('Candidate Git blob mismatch: '+x['path'])
print('Reviewed 29-file candidate manifest identity: PASS')
PYMANIFEST
printf '%s\n' "${EXPECTED[@]}" > "$EVIDENCE/approved-paths.txt"
export MAVEN_OPTS=-Xmx2g
printf 'java=%s\n' "$(java -version 2>&1 | head -1)" > "$EVIDENCE/environment.txt"
printf 'maven=%s\n' "$(mvn --version | head -1)" >> "$EVIDENCE/environment.txt"
printf 'kernel=%s\nuser=%s\n' "$(uname -a)" "$(id)" >> "$EVIDENCE/environment.txt"
find src -type f -name '*.java' -print0 | sort -z | xargs -0 sha256sum > "$EVIDENCE/source-sha256-before.txt"
sha256sum "${EXPECTED[@]}" > "$EVIDENCE/m8-29-hashes-before.txt"
run(){ local name="$1";shift
 rm -f target/surefire-reports/TEST-*.xml
 set +e; mvn -B -DargLine=-Xmx2g -Dmaven.test.failure.ignore=false -Dm8.test.native=true "$@" > "$EVIDENCE/$name.log" 2>&1; local status=$?;set -e
 printf '%s\n' "$status" > "$EVIDENCE/$name.exit"
 [[ ! -e "$EVIDENCE/$name-reports" ]] || { echo 'Stale gate reports detected' >&2; exit 2; }
 mkdir "$EVIDENCE/$name-reports";cp -a target/surefire-reports/*.xml "$EVIDENCE/$name-reports/" 2>/dev/null || true
 [[ "$status" -eq 0 ]] || { echo "$name FAILED" >&2; exit "$status"; }
 python3 - "$EVIDENCE/$name-reports" <<'PY'
import pathlib,sys,xml.etree.ElementTree as E
files=list(pathlib.Path(sys.argv[1]).glob('*.xml'))
if not files:sys.exit('No fresh Surefire XML')
tests=0
for p in files:
 r=E.parse(p).getroot()
 if any(int(r.attrib.get(k,'0')) for k in ('errors','failures','skipped')):sys.exit('Not all tests passed')
 tests+=int(r.attrib.get('tests','0'))
if not tests:sys.exit('No tests discovered')
print('discovered=',tests)
PY
}
run focused '-Dtest=M8*' clean test
run affected '-Dtest=M8*,*Nifti*,*Dicom*,*Conversion*,*Manifest*,*VerifiedCopy*,CopyJournalSequenceTest,*Boundary*' test
run full clean test
find src -type f -name '*.java' -print0 | sort -z | xargs -0 sha256sum > "$EVIDENCE/source-sha256-after.txt"
sha256sum "${EXPECTED[@]}" > "$EVIDENCE/m8-29-hashes-after.txt"
cmp "$EVIDENCE/m8-29-hashes-before.txt" "$EVIDENCE/m8-29-hashes-after.txt"
cmp "$EVIDENCE/source-sha256-before.txt" "$EVIDENCE/source-sha256-after.txt"
git diff --exit-code --quiet HEAD -- || { echo 'Predecessor changed during gates' >&2; exit 2; }
git diff --cached --exit-code --quiet || { echo 'Unexpected staging during gates' >&2; exit 2; }
python3 - "$REPO" "$MANIFEST" "${EXPECTED[@]}" <<'PYMANIFEST'
import hashlib,json,pathlib,sys
r=pathlib.Path(sys.argv[1]);d=json.loads(pathlib.Path(sys.argv[2]).read_text())
assert {x['path'] for x in d['files']}==set(sys.argv[3:])
for x in d['files']:
 raw=(r/x['path']).read_bytes()
 assert len(raw)==x['size'] and hashlib.sha256(raw).hexdigest()==x['sha256'],x['path']
PYMANIFEST
echo 'Linux source and gate evidence captured. Real-NIfTI demonstration remains separate.'
