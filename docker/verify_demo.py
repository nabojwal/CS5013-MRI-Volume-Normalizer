#!/usr/bin/env python3
"""Independent, stdlib-only MRI container demo checks.

This is an external oracle: it does not call Project PAI's Java writer or
verifier. It checks every synthetic voxel and relevant physical geometry.
A PASS applies only to this bounded synthetic 3D UINT16 fixture.
"""
import argparse
import json
import os
from pathlib import Path
import shutil
import struct
import sys

WIDTH, HEIGHT, DEPTH = 4, 3, 3
EXPECTED_SAMPLES = [z * 100 + y * 10 + x
                    for z in range(DEPTH) for y in range(HEIGHT)
                    for x in range(WIDTH)]
LONG_VR = {b'OB', b'OD', b'OF', b'OL', b'OV', b'OW', b'SQ', b'UC', b'UN',
           b'UR', b'UT', b'UV', b'SV'}


def require(cond, msg):
    if not cond:
        raise ValueError(msg)


def mount_type(path):
    target = os.path.realpath(path)
    match = (0, None)
    for line in Path('/proc/self/mountinfo').read_text().splitlines():
        left, sep, right = line.partition(' - ')
        if not sep:
            continue
        fields = left.split()
        if len(fields) < 5:
            continue
        mount = fields[4].replace('\\040', ' ')
        if target == mount or target.startswith(mount.rstrip('/') + '/'):
            if len(mount) > match[0]:
                match = (len(mount), right.split()[0])
    return match[1]


def check_fs(path):
    fs = mount_type(path)
    print(f'PAI_WORK_VOLUME_FILESYSTEM={fs}', flush=True)
    require(fs == 'ext4',
            'M8 Linux output/source policy requires ext4, not this '+str(fs)+
            ' filesystem. Use Docker Desktop Linux containers with a local '+
            'ext4-backed Docker volume; do not relax production file safety.')


def nifti_read(path):
    raw = Path(path).read_bytes()
    require(len(raw) >= 352, 'NIfTI truncation')
    require(raw[344:348] == b'n+1\x00', 'NIfTI magic mismatch')
    require(struct.unpack_from('<i', raw, 0)[0] == 348, 'NIfTI-1 header mismatch')
    dims = struct.unpack_from('<8h', raw, 40)
    require(dims[:4] == (3, WIDTH, HEIGHT, DEPTH), 'NIfTI dimensions mismatch')
    require(dims[4:] in ((0, 0, 0, 0), (1, 1, 1, 1)),
            'NIfTI unexpected higher dimensions')
    require(struct.unpack_from('<2h', raw, 70) == (512, 16),
            'NIfTI is not UINT16')
    require((raw[123] & 7) == 2, 'NIfTI spatial units are not millimetres')
    off = struct.unpack_from('<f', raw, 108)[0]
    require(off >= 352 and off == int(off), 'Invalid data offset')
    off = int(off)
    n = WIDTH * HEIGHT * DEPTH
    require(len(raw) == off + 2*n, 'Unexpected NIfTI payload size')
    voxels = struct.unpack_from('<%dH' % n, raw, off)
    require(list(voxels) == EXPECTED_SAMPLES,
            'NIfTI stored raw voxel array differs')
    scode = struct.unpack_from('<h', raw, 254)[0]
    require(scode > 0, 'No NIfTI sform for independent geometry validation')
    affine = [struct.unpack_from('<4f', raw, 280+16*r) for r in range(3)]
    expected = [(1., 0., 0., 0.), (0., 1., 0., 0.), (0., 0., 2., 0.)]
    for r in range(3):
        for c in range(4):
            require(abs(affine[r][c] - expected[r][c]) < 1e-4,
                    'NIfTI RAS affine mismatch')
    return voxels


def dicom_elements(path):
    raw = Path(path).read_bytes()
    require(len(raw) >= 132 and raw[128:132] == b'DICM',
            'DICOM Part-10 marker missing')
    i = 132
    result = {}
    while i + 8 <= len(raw):
        group, element = struct.unpack_from('<HH', raw, i)
        vr = raw[i+4:i+6]
        require(vr.isalpha() and vr.isupper(), 'Unexpected/non-explicit DICOM VR')
        if vr in LONG_VR:
            require(i + 12 <= len(raw), 'Truncated DICOM long-VR header')
            length = struct.unpack_from('<I', raw, i+8)[0]
            i += 12
        else:
            length = struct.unpack_from('<H', raw, i+6)[0]
            i += 8
        require(length != 0xFFFFFFFF, 'Unsupported undefined-length DICOM element')
        require(i + length <= len(raw), 'Truncated DICOM value')
        key = (group, element)
        require(key not in result, 'Duplicate DICOM element')
        result[key] = (vr, raw[i:i+length])
        i += length
    require(i == len(raw), 'Unexpected trailing DICOM bytes')
    return result


def field(ds, group, element):
    key = (group, element)
    require(key in ds, 'Required DICOM element missing: %04X,%04X' % key)
    return ds[key][1]


def uid(ds, group, element):
    return field(ds, group, element).decode('ascii').strip(' \x00')


def ds_numbers(ds, group, element):
    s = field(ds, group, element).decode('ascii').strip(' \x00')
    return [float(x) for x in s.split('\\')]


def check_dicom(src, root, target):
    nifti_read(src)
    root = Path(root)
    directories = [p for p in root.glob('m8-*') if p.is_dir()]
    require(len(directories) == 1, 'Expected exactly one public M8 series')
    series = directories[0]
    marker = series / 'PAI-M8-COMPLETE.json'
    require(marker.is_file(), 'M8 completion marker missing')
    decoded = json.loads(marker.read_text(encoding='utf-8'))
    require(decoded.get('schema') == 'PAI-M8-COMPLETE-1',
            'M8 completion marker schema mismatch')
    files = sorted(series.glob('*.dcm'))
    require(len(files) == DEPTH, 'DICOM instance count differs from source depth')
    target = Path(target)
    require(target.is_dir() and not list(target.iterdir()),
            'FBO input series directory must exist and be empty')
    shared_uid = None
    seen_sops = set()
    seen_z = set()
    for path in files:
        ds = dicom_elements(path)
        require(uid(ds, 0x0002, 0x0010) == '1.2.840.10008.1.2.1',
                'DICOM transfer syntax is not Explicit VR Little Endian')
        require(uid(ds, 0x0008, 0x0016) == '1.2.840.10008.5.1.4.1.1.4',
                'DICOM MR Image Storage SOP Class expected')
        u = uid(ds, 0x0020, 0x000e)
        require(u, 'Missing series UID')
        if shared_uid is None:
            shared_uid = u
        require(u == shared_uid, 'DICOM series UID inconsistency')
        sop = uid(ds, 0x0008, 0x0018)
        require(sop not in seen_sops, 'Repeated SOP Instance UID')
        seen_sops.add(sop)
        rows = struct.unpack('<H', field(ds, 0x0028, 0x0010))[0]
        cols = struct.unpack('<H', field(ds, 0x0028, 0x0011))[0]
        require((rows, cols) == (HEIGHT, WIDTH), 'Rows/Columns mismatch')
        require(struct.unpack('<H', field(ds, 0x0028, 0x0100))[0] == 16,
                'BitsAllocated mismatch')
        require(struct.unpack('<H', field(ds, 0x0028, 0x0103))[0] == 0,
                'Expected unsigned DICOM pixel data')
        orient = ds_numbers(ds, 0x0020, 0x0037)
        exp_orient = [-1., 0., 0., 0., -1., 0.]
        require(len(orient) == 6 and all(abs(a-b) < 1e-5 for a,b in zip(orient, exp_orient)),
                'DICOM LPS orientation mismatch')
        p = ds_numbers(ds, 0x0020, 0x0032)
        require(len(p) == 3 and abs(p[0]) < 1e-5 and abs(p[1]) < 1e-5,
                'DICOM LPS image origin mismatch')
        z = int(round(p[2]/2))
        require(z in range(DEPTH) and z not in seen_z and abs(p[2]-2*z) < 1e-5,
                'DICOM slice position/index mismatch')
        seen_z.add(z)
        spacing = ds_numbers(ds, 0x0028, 0x0030)
        require(len(spacing) == 2 and all(abs(s-1) < 1e-5 for s in spacing),
                'DICOM pixel spacing mismatch')
        slope = ds_numbers(ds, 0x0028, 0x1053)
        intercept = ds_numbers(ds, 0x0028, 0x1052)
        require(len(slope) == 1 and abs(slope[0]-1) < 1e-5 and
                len(intercept) == 1 and abs(intercept[0]) < 1e-5,
                'DICOM rescale transformation mismatch')
        pixel = field(ds, 0x7fe0, 0x0010)
        require(len(pixel) == WIDTH*HEIGHT*2, 'Pixel Data length mismatch')
        values = struct.unpack('<%dH' % (WIDTH*HEIGHT), pixel)
        expected = EXPECTED_SAMPLES[z*WIDTH*HEIGHT:(z+1)*WIDTH*HEIGHT]
        require(list(values) == expected,
                'DICOM pixel array differs at source slice %d' % z)
        shutil.copyfile(path, target/path.name)
    require(seen_z == set(range(DEPTH)), 'Missing DICOM source slice')
    print('PAI_INDEPENDENT_DICOM_PASS: all 36 pixels, geometry, scaling and UIDs',
          file=sys.stderr)
    print(shared_uid)  # stdout consumed as FBO --series selector


def check_nifti(src, forward_root):
    nifti_read(src)
    candidates = list(Path(forward_root).rglob('selected.nii'))
    require(len(candidates) == 1, 'Expected exactly one FBO selected.nii')
    nifti_read(candidates[0])
    print('PAI_INDEPENDENT_NIFTI_PASS: dimensions, all 36 pixels, RAS affine')


def main():
    p = argparse.ArgumentParser()
    s = p.add_subparsers(dest='action', required=True)
    q = s.add_parser('filesystem')
    q.add_argument('path')
    q = s.add_parser('dicom')
    q.add_argument('source_nifti')
    q.add_argument('reverse_root')
    q.add_argument('forward_series_dir')
    q = s.add_parser('nifti')
    q.add_argument('source_nifti')
    q.add_argument('forward_root')
    a = p.parse_args()
    if a.action == 'filesystem':
        check_fs(a.path)
    elif a.action == 'dicom':
        check_dicom(a.source_nifti, a.reverse_root, a.forward_series_dir)
    else:
        check_nifti(a.source_nifti, a.forward_root)


if __name__ == '__main__':
    try:
        main()
    except Exception as e:
        print('PAI_INDEPENDENT_VERIFICATION_FAIL: ' + str(e), file=sys.stderr)
        sys.exit(41)
