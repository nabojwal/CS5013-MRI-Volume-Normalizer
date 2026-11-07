#!/usr/bin/env python3
"""Generate a deterministic, small, synthetic 3D NIfTI-1 UINT16 volume.
No patient data, medical metadata, additional packages or network access.
"""
import argparse
import pathlib
import struct

WIDTH, HEIGHT, DEPTH = 4, 3, 3
VOXELS = [(z * 100) + (y * 10) + x for z in range(DEPTH)
          for y in range(HEIGHT) for x in range(WIDTH)]


def make() -> bytes:
    h = bytearray(352)
    struct.pack_into('<i', h, 0, 348)
    struct.pack_into('<8h', h, 40, 3, WIDTH, HEIGHT, DEPTH, 1, 1, 1, 1)
    struct.pack_into('<h', h, 68, 0)     # intent_code
    struct.pack_into('<h', h, 70, 512)   # uint16
    struct.pack_into('<h', h, 72, 16)
    struct.pack_into('<8f', h, 76, 1., 1., 1., 2., 1., 1., 1., 1.)
    struct.pack_into('<f', h, 108, 352.)
    struct.pack_into('<f', h, 112, 1.)  # declared identity slope
    struct.pack_into('<f', h, 116, 0.)
    h[123] = 2                     # millimetres
    struct.pack_into('<h', h, 252, 0)  # no qform
    struct.pack_into('<h', h, 254, 1)  # sform
    struct.pack_into('<4f', h, 280, 1., 0., 0., 0.)
    struct.pack_into('<4f', h, 296, 0., 1., 0., 0.)
    struct.pack_into('<4f', h, 312, 0., 0., 2., 0.)
    h[344:348] = b'n+1\x00'
    return bytes(h) + struct.pack('<%dH' % len(VOXELS), *VOXELS)


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument('output', type=pathlib.Path)
    args = p.parse_args()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_bytes(make())
    print('SYNTHETIC_NIFTI_GENERATED: 4x3x3 UINT16, 1x1x2 mm')


if __name__ == '__main__':
    main()
