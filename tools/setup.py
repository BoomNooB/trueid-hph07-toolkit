#!/usr/bin/env python3
"""Back up, apply narrow HPH07 changes, or preview/restore the recorded original values."""
import argparse
import datetime
import json
import re
import shlex
from common import ROOT, STATE, host, private_state
from tv import connect, push

PACKAGES=('com.google.android.feedback','com.google.android.tv.bugreportsender')
HOME_SERVICE='com.gathin.tvhome/.HomeKeyService'
GUARD='CLASSPATH=/data/local/tmp/tv-package-guard.jar app_process /system/bin local.tv.tools.PackageGuard '

def save(ledger):
    private_state()
    temporary=STATE/'ledger.json.tmp';temporary.write_text(json.dumps(ledger,indent=2)+'\n')
    temporary.replace(STATE/'ledger.json')

def require_backup():
    path=STATE/'ledger.json'
    if not path.exists():raise RuntimeError('Run tools/setup.py backup before changing anything.')
    d=json.loads(path.read_text())
    if d['host']!=host():raise RuntimeError('Backup belongs to a different TV_HOST; use a separate TV_STATE_DIR.')
    return d

def shell(dev,command):
    output=dev.shell(command,read_timeout_s=45).strip()
    if re.search(r'(^|\n)(Error:|Failure|Exception|Security exception)',output):raise RuntimeError(output)
    return output

def original_enabled(dump):
    match=re.search(r'^\s*User 0:.*\benabled=(\d+)\b',dump,re.M)
    if not match:raise ValueError('Could not determine original user0 enabled state')
    return int(match.group(1))

def merge_services(old,new):
    items=[] if old in ('','null',None) else old.split(':')
    if new not in items:items.append(new)
    return ':'.join(items)

def restore_plan(d):
    plan=[]
    for package,old in d.get('disabled',{}).items():
        if package not in PACKAGES:raise ValueError('Unexpected package in restore ledger')
        if old not in (0,1,2,3,4):raise ValueError('Invalid original enabled state')
        verb={0:'default-state',1:'enable',2:'disable',3:'disable-user',4:'disable-until-used'}[old]
        plan.append(f'pm {verb} --user 0 {package}')
    for key,old in d.get('settings',{}).items():
        if key not in ('enabled_accessibility_services','accessibility_enabled'):raise ValueError('Unexpected settings key')
        plan.append('settings delete secure '+key if old in ('null',None) else
                    'settings put secure '+key+' '+shlex.quote(old))
    if 'uninstall_block' in d:plan.append(GUARD+str(d['uninstall_block']).lower())
    if d.get('maintenance_started') or d.get('maintenance_permission_added'):
        plan.append('am stopservice -n local.tv.adbcontrol/.AdbSessionService')
    if d.get('maintenance_permission_added'):
        plan.append('pm revoke --user 0 local.tv.adbcontrol android.permission.WRITE_SECURE_SETTINGS')
    return plan

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('action',choices=['backup','maintenance','home','debloat','restore'])
    p.add_argument('--apply',action='store_true',help='Execute the displayed restore plan (restore defaults to preview)')
    a=p.parse_args();private_state()
    ledger=None if a.action=='backup' else require_backup()
    if a.action=='restore' and not a.apply:
        print('\n'.join(restore_plan(ledger)))
        print('Preview only. --apply executes this plan; see docs/restore.md for property and ADB closure.');return
    dev=connect()
    try:
        if shell(dev,'getprop ro.product.model')!='HPH07' or shell(dev,'getprop ro.build.version.sdk')!='28':
            raise RuntimeError('This setup is scoped to HPH07 Android9, not a generic TV debloater.')
        if a.action=='backup':
            folder=private_state()/'backups'/datetime.datetime.now().strftime('%Y%m%d-%H%M%S')
            folder.mkdir(parents=True)
            for name,command in {'packages':'pm list packages -f','disabled':'pm list packages -d',
                'getprop':'getprop','settings-global':'settings list global','settings-secure':'settings list secure',
                'settings-system':'settings list system','usb':'dumpsys usb','connectivity':'dumpsys connectivity'}.items():
                (folder/(name+'.txt')).write_text(shell(dev,command)+'\n')
            if not (STATE/'ledger.json').exists():
                original={pkg:original_enabled(shell(dev,'dumpsys package '+pkg)) for pkg in PACKAGES}
                save({'host':host(),'backup':str(folder.relative_to(STATE)),
                      'original_enabled':original,'disabled':{},'settings':{}})
            else:require_backup() # Never overwrite the first originals on rerun.
            print('Private backup:',folder);return
        if a.action=='maintenance':
            ledger['maintenance_started']=True;save(ledger)
            dump=shell(dev,'dumpsys package local.tv.adbcontrol')
            if not re.search(r'android.permission.WRITE_SECURE_SETTINGS:\s*granted=true',dump):
                ledger['maintenance_permission_added']=True;save(ledger)
                shell(dev,'pm grant --user 0 local.tv.adbcontrol android.permission.WRITE_SECURE_SETTINGS')
            print(shell(dev,'am start-foreground-service -n local.tv.adbcontrol/.AdbSessionService -a start'))
        elif a.action=='home':
            if not shell(dev,'pm path com.gathin.tvhome').startswith('package:'):raise RuntimeError('Install the launcher first')
            push(dev,ROOT/'build/package-guard/package-guard.jar','/data/local/tmp/tv-package-guard.jar')
            value=shell(dev,GUARD+'get')
            match=re.search(r'before=(true|false)',value)
            if not match:raise RuntimeError('Cannot read original uninstall protection')
            if 'uninstall_block' not in ledger:ledger['uninstall_block']=match[1]=='true';save(ledger)
            for key in ['enabled_accessibility_services','accessibility_enabled']:
                if key not in ledger['settings']:
                    ledger['settings'][key]=shell(dev,'settings get secure '+key);save(ledger)
            print(shell(dev,GUARD+'true'))
            current=shell(dev,'settings get secure enabled_accessibility_services')
            shell(dev,'settings put secure enabled_accessibility_services '+shlex.quote(merge_services(current,HOME_SERVICE)))
            shell(dev,'settings put secure accessibility_enabled 1')
            shell(dev,'am start -n com.gathin.tvhome/.MainActivity')
            print('Test PHYSICAL Home and held volume. Stock TrueID stays enabled; native HOME resolver stays stock.')
        elif a.action=='debloat':
            for package in PACKAGES:
                if package not in ledger['disabled']:
                    ledger['disabled'][package]=ledger['original_enabled'][package];save(ledger)
                print(shell(dev,'pm disable-user --user 0 '+package))
                disabled=set(shell(dev,'pm list packages -d').splitlines())
                if 'package:'+package not in disabled:raise RuntimeError('Disable was not applied; stop rather than retry.')
                # Capture after each single-package change. Protected windows can return an empty image.
                data=dev.shell('screencap -p',decode=False,read_timeout_s=30)
                if data.startswith(b'\x89PNG'):(STATE/(package+'-after.png')).write_bytes(data)
                else:print('No usable screenshot; check the real screen before more changes.')
            print('Only the two reporting packages were disabled. Reboot and compare the disabled list.')
        else:
            if 'uninstall_block' in ledger:
                push(dev,ROOT/'build/package-guard/package-guard.jar','/data/local/tmp/tv-package-guard.jar')
            for command in restore_plan(ledger):print(command,'\n',shell(dev,command))
            print('Recorded changes restored. APKs and property unlock remain. Close ADB through Android UI.')
    finally:dev.close()

if __name__=='__main__':main()
