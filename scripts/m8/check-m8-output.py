#!/usr/bin/env python3
"""Independent bounded NIfTI-1/Explicit-VR-LE M8-MR-1 qualification oracle.

Not a generic DICOM validator, not a production dependency, not a clinical tool.
Uses only Python's standard library. Never edits source or published DICOM files.
"""
import argparse
import gzip
import hashlib
import json
from decimal import Decimal, InvalidOperation
import math
from pathlib import Path
import struct
import sys

MAX_NIFTI = 512 * (1 << 20)
MAX_DICOM = 128 * (1 << 20)
SOP = '1.2.840.10008.5.1.4.1.1.4'
TS = '1.2.840.10008.1.2.1'


def require(condition, message):
    if not condition:
        raise ValueError(message)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def nifti(source):
    path=Path(source)
    require(path.is_file() and not path.is_symlink(), 'source type')
    require(0 < path.stat().st_size <= MAX_NIFTI, 'source encoded size')
    with path.open('rb') as stream:
        encoded=stream.read(MAX_NIFTI+1)
    require(len(encoded) <= MAX_NIFTI, 'source encoded size')
    if encoded[:2] == b'\x1f\x8b':
        # Bounded inflate: gzip.decompress() would allocate before checking limits.
        with gzip.GzipFile(fileobj=__import__('io').BytesIO(encoded)) as compressed:
            data=compressed.read(MAX_NIFTI+1)
            require(len(data)<=MAX_NIFTI and compressed.read(1)==b'', 'decoded limit')
    else:
        data = encoded
    require(352 <= len(data) <= MAX_NIFTI, 'decoded source bounds')
    order = '<' if struct.unpack_from('<i', data)[0] == 348 else '>'
    require(struct.unpack_from(order+'i', data)[0] == 348, 'invalid NIfTI header')
    get = lambda fmt, offset: struct.unpack_from(order+fmt, data, offset)[0]
    require(data[344:348] == b'n+1\x00', 'unsupported NIfTI variant')
    require(get('h', 40) == 3, 'unsupported dimensions')
    width, height, depth = (get('h', v) for v in (42, 44, 46))
    require(0 < width <= 4096 and 0 < height <= 4096 and 0 < depth <= 4096,
            'invalid dimensions')
    count = width * height * depth
    require(count <= 32_000_000, 'voxel resource limit')
    datatype = get('h', 70)
    fmt = {2:'B', 256:'b', 512:'H', 4:'h'}.get(datatype)
    require(fmt is not None, 'unsupported datatype')
    bytes_per = struct.calcsize(fmt)
    offset = get('f', 108)
    require(offset.is_integer() and offset >= 352, 'invalid data offset')
    offset = int(offset)
    require(len(data) == offset + bytes_per*count, 'source byte count')
    units = data[123] & 7
    factor = {1:1000., 2:1., 3:0.001}.get(units)
    require(factor is not None, 'unsupported units')
    require(get('h', 254) > 0, 'checker demo requires sform')
    affine = [[get('f', 280 + 16*r + 4*c)*factor for c in range(4)]
              for r in range(3)]
    slope = get('f', 112)
    intercept = get('f', 116)
    if slope == 0: slope, intercept = 1., 0.
    require(math.isfinite(slope) and math.isfinite(intercept), 'invalid intensity')
    return dict(raw=data, order=order, fmt=fmt, offset=offset, count=count,
                width=width, height=height, depth=depth, affine=affine,
                slope=slope, intercept=intercept, sha=digest(encoded))


def dicom(path,max_bytes):
    path=Path(path)
    bound=min(MAX_DICOM,max_bytes)
    require(path.is_file() and not path.is_symlink(), 'DICOM file type')
    require(132 <= path.stat().st_size <= bound, 'DICOM file size limit')
    with path.open('rb') as stream:raw=stream.read(bound+1)
    require(132 <= len(raw) <= bound and raw[128:132] == b'DICM', 'Part 10 signature')
    p=132; tags={}
    while p < len(raw):
        require(p+8 <= len(raw), 'truncated tag header')
        group, element = struct.unpack_from('<HH', raw, p)
        vr = raw[p+4:p+6].decode('ascii')
        require(vr.isalpha() and vr.isupper(), 'Explicit VR required')
        if vr in ('OB','OD','OF','OL','OV','OW','SQ','UC','UR','UT','UN'):
            require(p+12 <= len(raw), 'truncated long VR')
            require(raw[p+6:p+8] == b'\x00\x00', 'long VR reserved bytes')
            n = struct.unpack_from('<I', raw, p+8)[0]; start=p+12
        else:
            n = struct.unpack_from('<H', raw, p+6)[0]; start=p+8
        require(n != 0xffffffff and n <= len(raw)-start, 'unsupported/truncated value length')
        tag=(group << 16)|element
        require(tag not in tags, 'duplicate DICOM tag')
        tags[tag]=(vr,raw[start:start+n])
        p=start+n
    require(p==len(raw), 'trailing DICOM bytes')
    return tags,raw


def text(tags,tag):
    require(tag in tags, f'missing {tag:08x}')
    return tags[tag][1].rstrip(b' \x00').decode('ascii')


def numbers(tags,tag,count):
    arr=[float(x) for x in text(tags,tag).split('\\')]
    require(len(arr)==count and all(map(math.isfinite,arr)), f'invalid numeric VM {tag:08x}')
    return arr


def dot(a,b):return sum(x*y for x,y in zip(a,b))
def sub(a,b):return [x-y for x,y in zip(a,b)]
def norm(v):return math.sqrt(dot(v,v))
def cross(a,b):return [a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]]


def compare(nif, datasets):
    w,h,d=nif['width'],nif['height'],nif['depth']
    result={'dimensions':True,'exact_stored_values':True,'geometry':True,
            'intensity_scaling':True,'metadata_uid_consistency':True}
    first=None; positions=[]; max_error=0.; checked=0; sop_ids=set()
    for k,tags in enumerate(datasets):
        try:
            allowed={
              0x00080008, 0x00080012, 0x00080013, 0x00080016, 0x00080018,
              0x00080020, 0x00080023, 0x00080030, 0x00080033, 0x00080050,
              0x00080060, 0x00080070, 0x00080090, 0x00080201, 0x0008103E,
              0x00081090, 0x00082111,
              0x00100010, 0x00100020, 0x00100030, 0x00100040, 0x00102210,
              0x00180010, 0x00180020, 0x00180021, 0x00180022, 0x00180023,
              0x00180050, 0x00180080, 0x00180081, 0x00180082, 0x00180088,
              0x00180091, 0x00181020, 0x00181060, 0x00185100,
              0x0020000D, 0x0020000E, 0x00200010, 0x00200011, 0x00200013,
              0x00200032, 0x00200037, 0x00200052, 0x00200060, 0x00201040,
              0x00280002, 0x00280004, 0x00280010, 0x00280011, 0x00280030,
              0x00280100, 0x00280101, 0x00280102, 0x00280103,
              0x00281052, 0x00281053, 0x00281054, 0x7fe00010
            }
            actual={tag for tag in tags if tag >> 16 != 0x0002}
            require(actual.issubset(allowed),'unexpected DICOM dataset tag')
            require(text(tags,0x00080016)==SOP,'SOP class')
            require(text(tags,0x00020002)==SOP,'file meta SOP class')
            require(text(tags,0x00020010)==TS,'transfer syntax')
            uid=text(tags,0x00080018)
            require(uid==text(tags,0x00020003) and uid.startswith('2.25.') and len(uid)<=64,
                    'SOP/file meta UID')
            require(uid not in sop_ids,'duplicate SOP UID')
            sop_ids.add(uid)
            require(text(tags,0x00080060)=='MR','modality')
            require(text(tags,0x00080008)=='DERIVED\\SECONDARY\\OTHER','derived image type')
            require(text(tags,0x00200013)==str(k+1),'slice index')
            ids=(text(tags,0x0020000D),text(tags,0x0020000E),text(tags,0x00200052))
            if first is None:first=ids
            require(ids==first,'shared series/study/FoR UID')
            require(all(x.startswith('2.25.') and len(x)<=64 for x in ids),'UID format')
            require(text(tags,0x00280004)=='MONOCHROME2','photometric')
            rep=struct.unpack('<H',tags[0x00280103][1])[0]
            signed=(nif['fmt'] in ('b','h'))
            require(rep==int(signed),'pixel representation')
            for tag,v in ((0x00280100,16),(0x00280101,16),(0x00280102,15),
                          (0x00280002,1)):
                require(struct.unpack('<H',tags[tag][1])[0]==v,'stored pixel encoding')
            require(struct.unpack('<H',tags[0x00280010][1])[0]==h and
                    struct.unpack('<H',tags[0x00280011][1])[0]==w,'rows/columns')
        except (ValueError, KeyError, UnicodeError, struct.error):
            result['metadata_uid_consistency']=False
            result['dimensions']=False
        try:
            orient=numbers(tags,0x00200037,6)
            pos=numbers(tags,0x00200032,3)
            spacing=numbers(tags,0x00280030,2)
            require(all(x>0 for x in spacing),'positive spacing')
            positions.append((pos, text(tags,0x00200032).split('\\')))
            for x,y in ((0,0),(max(1,w-1),0),(0,max(1,h-1)),(max(1,w-1),max(1,h-1))):
                expected=[(-1 if r<2 else 1)*sum(nif['affine'][r][c]*q
                        for c,q in enumerate((x,y,k,1))) for r in range(3)]
                actual=[pos[r]+x*spacing[1]*orient[r]+y*spacing[0]*orient[r+3] for r in range(3)]
                error=norm(sub(expected,actual))
                max_error=max(max_error,error)
                require(math.isfinite(error) and error<=0.001,'geometry corner')
        except (ValueError, KeyError, UnicodeError, struct.error):
            result['geometry']=False
        try:
            pix=tags[0x7fe00010][1]
            require(len(pix)==w*h*2,'pixel data length')
            signed=nif['fmt'] in ('b','h')
            source_format=nif['order']+nif['fmt']
            out_format='<'+('h' if signed else 'H')
            slope=numbers(tags,0x00281053,1)[0]
            intercept=numbers(tags,0x00281052,1)[0]
            require(text(tags,0x00281054)=='US','rescale type')
            for i in range(w*h):
                expected=struct.unpack_from(source_format,nif['raw'],nif['offset']+(k*w*h+i)*struct.calcsize(nif['fmt']))[0]
                actual=struct.unpack_from(out_format,pix,i*2)[0]
                if actual != expected:result['exact_stored_values']=False
                desired=nif['slope']*expected+nif['intercept']
                measured=slope*actual+intercept
                if abs(measured-desired)>1e-6+1e-9*max(1,abs(desired)):
                    result['intensity_scaling']=False
                checked+=1
        except (ValueError,KeyError,UnicodeError,struct.error):
            result['exact_stored_values']=False;result['intensity_scaling']=False
    if len(positions)==d and d>1:
        try:
            orient=numbers(datasets[0],0x00200037,6)
            normal=cross(orient[:3],orient[3:]);normal=[v/norm(normal) for v in normal]
            step=[(-1 if r<2 else 1)*nif['affine'][r][2] for r in range(3)]
            u=[(-1 if r<2 else 1)*nif['affine'][r][0] for r in range(3)]
            v=[(-1 if r<2 else 1)*nif['affine'][r][1] for r in range(3)]
            source_normal=cross(u,v);source_normal=[z/norm(source_normal) for z in source_normal]
            require(dot(normal,source_normal)>0,'serialized orientation reversal')
            signed_source=dot(step,source_normal)
            source_spacing=abs(signed_source)
            claimed=numbers(datasets[0],0x00180088,1)[0]
            tol=1e-6*min(source_spacing,claimed)
            require(math.isfinite(tol) and tol>0 and
                    abs(claimed-source_spacing)<=1e-6*source_spacing,'spacing mismatch')
            for k in range(1,d):
                # Compare exact decimal DS differences before converting to binary float.
                decimal_delta=[float(Decimal(positions[k][1][r])-
                       Decimal(positions[k-1][1][r])) for r in range(3)]
                difference=sub(positions[k][0],positions[k-1][0])
                for delta in (decimal_delta,difference):
                    q=dot(delta,normal)
                    require(math.isfinite(q) and q!=0 and q*signed_source>0 and
                        abs(abs(q)-claimed)<=tol,'B02 signed slice separation')
                    require(norm(sub(delta,[q*z for z in normal]))<=tol,'B02 lateral spacing')
        except (ValueError,KeyError,UnicodeError,ZeroDivisionError,InvalidOperation,OverflowError):result['geometry']=False
    return result,checked,max_error


def completion(root,datasets,raws,source_sha):
    try:
        root=Path(root)
        require(root.is_dir() and not root.is_symlink(), 'series directory')
        marker_path=root/'PAI-M8-COMPLETE.json'
        require(marker_path.is_file() and not marker_path.is_symlink(), 'completion marker')
        marker_raw=marker_path.read_bytes()
        require(len(marker_raw)<=8192 and marker_raw.endswith(b'\n'),'marker bound')
        marker=json.loads(marker_raw)
        require(list(marker)==['schema','profile','jobId','completedSequence',
                    'completedSha256','inventorySha256'],'marker schema fields')
        require(marker['schema']=='PAI-M8-COMPLETE-1' and marker['profile']=='M8-MR-1',
                'marker schema')
        require(marker_raw==(json.dumps(marker,separators=(',',':'),ensure_ascii=False)+'\n').encode(),
                'marker canonical bytes')
        job=marker['jobId']
        require(root.name=='m8-'+job,'marker job binding')
        directory=root.parent/('.pai-m8-'+job)
        require(directory.is_dir() and not directory.is_symlink(),'private journal directory')
        all_cp=sorted(p for p in directory.iterdir() if p.name.startswith('checkpoint-'))
        require(5<=len(all_cp)<=8,'journal cardinality')
        previous='0'*64; last=None; plan=None; previous_event=None; staged_inventory=None
        for i,p in enumerate(all_cp,1):
            require(p.name==f'checkpoint-{i:06d}.json' and p.is_file() and not p.is_symlink(),
                    'journal exact sequence and entry type')
            require(0<p.stat().st_size<=8*(1<<20),'checkpoint bound')
            raw=p.read_bytes();cp=json.loads(raw)
            require(raw==(json.dumps(cp,separators=(',',':'),ensure_ascii=False)+'\n').encode(),
                    'checkpoint canonical bytes')
            require(list(cp)==['schema','profile','jobId','sequence','previousSha256',
                    'event','utc','plan','slices','faults'],'checkpoint schema')
            require(cp['sequence']==i and cp['previousSha256']==previous,'journal chain')
            require(cp['jobId']==job and cp['schema']=='PAI-M8-JOURNAL-1' and
                    cp['profile']=='M8-MR-1','journal identity')
            events={None:['INTENT'],'INTENT':['STAGED_VERIFIED','FAILED','RECOVERY_REQUIRED'],
                'STAGED_VERIFIED':['PUBLISHING','FAILED','RECOVERY_REQUIRED'],
                'PUBLISHING':['PUBLISHED','RECOVERY_REQUIRED'],
                'PUBLISHED':['COMPLETED','RECOVERY_REQUIRED'],
                'COMPLETED':['RECOVERY_REQUIRED'],'FAILED':['RECOVERY_REQUIRED'],
                'RECOVERY_REQUIRED':[]}
            require(cp['event'] in events[previous_event],'journal transition')
            if plan is None: plan=cp['plan']
            require(cp['plan']==plan and cp['plan']['jobId']==job,'journal immutable plan')
            require(cp['plan']['sourceSha256']==source_sha,'source-to-journal binding')
            if cp['event']=='STAGED_VERIFIED':staged_inventory=cp['slices']
            if cp['event'] in ('PUBLISHING','PUBLISHED','COMPLETED'):
                require(staged_inventory is not None and cp['slices']==staged_inventory,
                        'journal inventory binding')
            previous_event=cp['event'];previous=digest(raw);last=cp
        require(last['event']=='COMPLETED','terminal receipt')
        require(len(datasets)==len(raws)==plan['depth']==len(last['slices'])==len(plan['slices']),
                'exact complete inventory cardinality')
        inventory_bytes=(json.dumps(last['slices'],separators=(',',':'),ensure_ascii=False)+'\n').encode()
        require(marker['inventorySha256']==digest(inventory_bytes),'inventory digest')
        require(marker['completedSequence']==len(all_cp) and
                marker['completedSha256']==previous,'terminal receipt')
        files=sorted(p.name for p in root.iterdir())
        wanted=sorted([f'image-{k+1:06d}.dcm' for k in range(len(datasets))]+['PAI-M8-COMPLETE.json'])
        require(files==wanted,'exact final series membership')
        sop_ids=set()
        for k in range(len(datasets)):
            info=last['slices'][k];raw=raws[k];expected=plan['slices'][k]
            require(info['sourceZ']==k and expected['sourceZ']==k and expected['instanceNumber']==k+1
                    and info['filename']==expected['filename']==f'image-{k+1:06d}.dcm',
                    'slice mapping')
            sop=info['sopUid']
            require(sop==expected['sopUid'] and sop==text(datasets[k],0x00080018)
                and sop not in sop_ids,'SOP UID integrity/uniqueness')
            sop_ids.add(sop)
            require(info['size']==len(raw) and info['sha256']==digest(raw),'DICOM digest')
        return True
    except (OSError,ValueError,KeyError,TypeError,json.JSONDecodeError,InvalidOperation,IndexError):return False


def main():
    ap=argparse.ArgumentParser(description=__doc__)
    ap.add_argument('source');ap.add_argument('series_directory')
    args=ap.parse_args()
    root=Path(args.series_directory)
    nif=nifti(args.source)
    names=[root/f'image-{k+1:06d}.dcm' for k in range(nif['depth'])]
    pairs=[dicom(p,2*nif['width']*nif['height']+16384) for p in names]
    checks,count,maximum=compare(nif,[t for t,_ in pairs])
    checks['complete_publication']=completion(root,[t for t,_ in pairs],[b for _,b in pairs],nif['sha'])
    print(json.dumps({'python':sys.version.split()[0],'checks':checks,'compared_voxels':count,
                      'max_world_error_mm':maximum,'source_sha256':nif['sha']},sort_keys=True))
    if not all(checks.values()):return 1
    # Negative-oracle sensitivity on disposable in-memory attribute copies; never alter user files.
    mutated=[dict(t) for t,_ in pairs]
    broken=bytearray(mutated[0][0x7fe00010][1]);broken[0]^=1
    mutated[0][0x7fe00010]=(mutated[0][0x7fe00010][0],bytes(broken))
    damaged,_count,_max=compare(nif,mutated)
    require(not damaged['exact_stored_values'],'independent pixel negative oracle insensitive')
    mutated=[dict(t) for t,_ in pairs]
    mutated[0][0x00200032]=(mutated[0][0x00200032][0],b'9999\\9999\\9999 ')
    damaged,_count,_max=compare(nif,mutated)
    require(not damaged['geometry'],'independent IPP negative oracle insensitive')
    return 0

if __name__=='__main__':
    try:sys.exit(main())
    except Exception as e:print('Independent M8 checker refused: '+type(e).__name__,file=sys.stderr);sys.exit(2)
