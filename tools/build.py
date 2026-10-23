#!/usr/bin/env python3
"""Build source-only helper APKs with your installed JDK and Android SDK."""
import argparse
import ipaddress
import json
import os
from pathlib import Path
import shutil
import subprocess
import zipfile
from common import ROOT, private_state

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('app', choices=['bridge','adb-control','package-guard','livetv-shortcut'])
    p.add_argument('--host-ip', help='Computer LAN IP; embedded registration destination for Bridge only')
    p.add_argument('--sdk', default=os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT'))
    p.add_argument('--java-home', default=os.environ.get('JAVA_HOME'))
    p.add_argument('--build-tools', default='35.0.0')
    a = p.parse_args()
    if not a.sdk or not a.java_home:
        p.error('Set ANDROID_HOME and JAVA_HOME (JDK 21), or pass --sdk and --java-home.')
    sdk, jdk = Path(a.sdk).expanduser(), Path(a.java_home).expanduser()
    bt = sdk/'build-tools'/a.build_tools
    android = sdk/'platforms/android-35/android.jar'
    if not android.exists(): p.error('Install platforms;android-35 with sdkmanager.')
    if a.app == 'bridge':
        if not a.host_ip: p.error('Bridge needs --host-ip for the computer running host.py.')
        ipaddress.ip_address(a.host_ip)
    env = {**os.environ, 'JAVA_HOME':str(jdk), 'PATH':str(jdk/'bin')+os.pathsep+os.environ.get('PATH','')}
    def run(*argv):
        subprocess.run([str(x) for x in argv], env=env, check=True)
    source = ROOT/'android'/a.app
    out = ROOT/'build'/a.app
    out.mkdir(parents=True, exist_ok=True)
    classes = out/'classes'
    if classes.exists(): shutil.rmtree(classes)
    classes.mkdir()
    sources = sorted((source/'src').rglob('*.java'))
    if a.app == 'bridge':
        address = '['+a.host_ip+']' if ':' in a.host_ip else a.host_ip
        generated = out/'generated/local/tv/bridge/HostConfig.java'
        generated.parent.mkdir(parents=True, exist_ok=True)
        generated.write_text('package local.tv.bridge;\nfinal class HostConfig { static final String REGISTER_URL = '+
                             json.dumps('http://'+address+':18765/register')+'; }\n')
        sources.append(generated)
    run(jdk/'bin/javac','--release','8','-classpath',android,'-d',classes,*sources)
    jar = out/'classes.jar'
    with zipfile.ZipFile(jar,'w') as z:
        for f in sorted(classes.rglob('*.class')): z.write(f,f.relative_to(classes))
    run(bt/'d8','--min-api','26','--lib',android,'--output',out,jar)
    if a.app == 'package-guard':
        with zipfile.ZipFile(out/'package-guard.jar','w') as z: z.write(out/'classes.dex','classes.dex')
        print('Built',out/'package-guard.jar'); return
    run(bt/'aapt2','compile','--dir',source/'res','-o',out/'resources.zip')
    run(bt/'aapt2','link','-o',out/'unsigned.apk','--manifest',source/'AndroidManifest.xml','-I',android,out/'resources.zip')
    with zipfile.ZipFile(out/'unsigned.apk','a',compression=zipfile.ZIP_DEFLATED) as z:
        z.write(out/'classes.dex','classes.dex')
    run(bt/'zipalign','-f','4',out/'unsigned.apk',out/'aligned.apk')
    key = private_state()/(a.app+'.keystore')
    if not key.exists():
        run(jdk/'bin/keytool','-genkeypair','-keystore',key,'-storepass','android','-keypass','android',
            '-alias','localtv','-dname','CN=Local TV Toolkit','-keyalg','RSA','-keysize','2048','-validity','3650')
        key.chmod(0o600)
    apk = out/(a.app+'.apk')
    run(bt/'apksigner','sign','--ks',key,'--ks-pass','pass:android','--key-pass','pass:android',
        '--v1-signing-enabled','true','--v2-signing-enabled','true','--out',apk,out/'aligned.apk')
    run(bt/'apksigner','verify','--verbose',apk)
    print('Built',apk)

if __name__ == '__main__': main()
