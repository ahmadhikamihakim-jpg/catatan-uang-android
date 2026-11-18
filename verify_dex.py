#!/usr/bin/env python3
"""Pemeriksaan pasca-build: pastikan tidak ada kelas di dex yang superclass/interface-nya
hilang (penyebab NoClassDefFoundError di perangkat). Keluar dengan kode 1 bila ada yang hilang."""
import re, subprocess, sys, glob

DEXDUMP = '/home/hatch/android-tools/sdk/build-tools/34.0.0/dexdump'
dexes = sorted(glob.glob(sys.argv[1] + '/classes*.dex'))
if not dexes:
    print('tidak ada dex'); sys.exit(1)
out = ''
for d in dexes:
    out += subprocess.run([DEXDUMP, d], capture_output=True).stdout.decode('utf-8', 'replace')
defs = set(re.findall(r"Class descriptor\s+: '([^']+)'", out))
missing = {}
for m in re.finditer(r"Class descriptor\s+: '([^']+)'\s+Access flags.*?Superclass\s+: '([^']+)'(?:\s+Interfaces\s+-\s+(.*?))?\s+Static fields", out, re.S):
    cls, sup, ifs = m.group(1), m.group(2), m.group(3) or ''
    for r in [sup] + re.findall(r": '([^']+)'", ifs):
        if r.startswith(('Landroidx/', 'Lcom/google/', 'Lkotlin', 'Lorg/jetbrains/')) and r not in defs:
            missing.setdefault(r, []).append(cls)
if missing:
    print('MASIH ADA KELAS HILANG:')
    for k, v in sorted(missing.items()):
        print(' ', k, '<-', v[:2])
    sys.exit(1)
print(f'OK: {len(defs)} kelas, tidak ada supertype/interface yang hilang')
