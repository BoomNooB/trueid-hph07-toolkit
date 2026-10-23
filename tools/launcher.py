#!/usr/bin/env python3
"""Build the pinned launcher with the shared HPH07 config; optionally install and verify it."""
import argparse
import hashlib
import os
from pathlib import Path
import re
import shutil
import subprocess
from common import ROOT, private_state
from tv import connect, push
from upstream import DEST, COMMIT

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--install',action='store_true',help='Requires a backup and working authenticated ADB')
    a=p.parse_args()
    if not DEST.exists(): p.error('Run python tools/upstream.py first.')
    head=subprocess.check_output(['git','-C',str(DEST),'rev-parse','HEAD'],text=True).strip()
    if head!=COMMIT:p.error('Unexpected upstream revision.')
    config=DEST/'launcher/app/src/main/res/values/config.xml'
    shutil.copyfile(ROOT/'launcher/hph07-config.xml',config)
    subprocess.run(['./gradlew','--no-daemon','--console=plain',':app:assembleRelease'],cwd=DEST/'launcher',check=True)
    apk=DEST/'launcher/app/build/outputs/apk/release/app-release.apk'
    artifact=ROOT/'build/launcher.apk';artifact.parent.mkdir(exist_ok=True);shutil.copyfile(apk,artifact)
    digest=hashlib.sha256(artifact.read_bytes()).hexdigest();print('Built launcher SHA256:',digest)
    if not a.install:return
    from setup import require_backup
    ledger=require_backup()
    dev=connect()
    try:
        if dev.shell('getprop ro.product.model').strip()!='HPH07':raise RuntimeError('Scoped to HPH07')
        push(dev,artifact,'/data/local/tmp/hph07-launcher.apk')
        error=None
        try:
            result=dev.shell('pm install -r /data/local/tmp/hph07-launcher.apk',read_timeout_s=120)
            if 'Success' not in result:raise RuntimeError(result)
        except Exception as exc:
            error=exc
            print('Install reply interrupted; reconnect and verify the actual APK before retrying.')
            dev.close();dev=connect()
        paths=dev.shell('pm path com.gathin.tvhome').splitlines()
        path=next((x[8:] for x in paths if x.endswith('/base.apk') and x.startswith('package:')),None)
        if not path or not re.fullmatch(r'/data/app/[A-Za-z0-9_./=+~-]+/base.apk',path):
            raise RuntimeError('Installed launcher path could not be verified') from error
        installed=private_state()/'launcher-installed.apk'
        dev.pull(path,str(installed),read_timeout_s=300)
        if hashlib.sha256(installed.read_bytes()).hexdigest()!=digest:
            raise RuntimeError('Installed APK differs; do not retry installs blindly.')
        print('Installed APK hash matches. Now run tools/setup.py home, then test the real remote.')
    finally:dev.close()

if __name__=='__main__':main()
