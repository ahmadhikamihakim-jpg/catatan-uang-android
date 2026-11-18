#!/usr/bin/env python3
"""Ekstrak AAR/JAR hasil resolver & buat manifest gabungan untuk build manual."""
import glob, os, re, shutil, sys, zipfile
import xml.etree.ElementTree as ET

PROJ = os.path.dirname(os.path.abspath(__file__))
M2 = os.path.expanduser('~/android-tools/m2')
OUT = os.path.join(PROJ, 'build-firebase')
LIBS = os.path.join(OUT, 'libs')
APP_ID = 'id.hakim.app'

if os.path.exists(LIBS):
    shutil.rmtree(LIBS)
os.makedirs(LIBS)

aars, jars = [], []
daftar = os.path.expanduser('~/android-tools/resolved-list.txt')
for line in open(daftar):
    pkg, fn = line.strip().split('|', 1)
    (aars if pkg == 'aar' else jars).append(fn)
aars.sort(); jars.sort()

lib_dirs = []
app_children = []
for i, aar in enumerate(aars):
    d = os.path.join(LIBS, f'a{i}')
    os.makedirs(d)
    with zipfile.ZipFile(aar) as z:
        names = z.namelist()
        for n in names:
            if n == 'classes.jar' or n == 'AndroidManifest.xml' or n.startswith('res/'):
                z.extract(n, d)
    man = os.path.join(d, 'AndroidManifest.xml')
    pkg = '?'
    children = 0
    if os.path.exists(man):
        src = open(man, encoding='utf-8', errors='replace').read()
        m = re.search(r'package="([^"]+)"', src)
        pkg = m.group(1) if m else '?'
        app = re.search(r'<application[^>]*>(.*?)</application>', src, re.S)
        if app and app.group(1).strip():
            body = app.group(1)
            body = body.replace('${applicationId}', APP_ID)
            app_children.append(f'<!-- {os.path.basename(aar)} ({pkg}) -->\n' + body)
            children = body.count('<activity') + body.count('<service') + body.count('<receiver') + body.count('<provider') + body.count('<meta-data')
    has_res = os.path.isdir(os.path.join(d, 'res')) and any(os.scandir(os.path.join(d, 'res')))
    lib_dirs.append((d, pkg, has_res, os.path.basename(aar), children))

# Manifest aplikasi + anak-anak <application> dari pustaka
appman = open(os.path.join(PROJ, 'app/src/main/AndroidManifest.xml'), encoding='utf-8').read()
merged = appman.replace('</application>', '\n' + '\n'.join(app_children) + '\n    </application>')
open(os.path.join(OUT, 'AndroidManifest.xml'), 'w', encoding='utf-8').write(merged)

with open(os.path.join(OUT, 'libs.txt'), 'w') as f:
    for d, pkg, has_res, name, ch in lib_dirs:
        f.write(f'{d}|{pkg}|{1 if has_res else 0}|{name}|{ch}\n')
    for j in jars:
        f.write(f'JAR|{j}\n')

print(f'{len(lib_dirs)} AAR, {len(jars)} JAR')
for d, pkg, has_res, name, ch in lib_dirs:
    print(f'  {name:55s} pkg={pkg:45s} res={"Y" if has_res else "-"} komponen={ch}')
