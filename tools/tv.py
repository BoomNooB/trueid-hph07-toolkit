#!/usr/bin/env python3
"""Authenticated Python ADB client. TV_HOST is required; private state is never tracked."""
import argparse
import base64
import hashlib
import re
import shlex
import time
from pathlib import Path
from common import host, private_state

def connect():
    from adb_shell.adb_device import AdbDeviceTcp
    from adb_shell.auth.keygen import keygen
    from adb_shell.auth.sign_pythonrsa import PythonRSASigner
    key = private_state()/'adbkey'
    if not key.exists(): keygen(str(key))
    key.chmod(0o600)
    signer = PythonRSASigner.FromRSAKeyPath(str(key))
    fingerprint = hashlib.md5(base64.b64decode(Path(str(key)+'.pub').read_text().split()[0])).hexdigest()
    dev = AdbDeviceTcp(host(),5555,default_transport_timeout_s=10)
    try:
        if not dev.connect(rsa_keys=[signer],auth_timeout_s=60,read_timeout_s=65,
            auth_callback=lambda _: print('Accept this computer on the TV. ADB key MD5: '+fingerprint)):
            raise RuntimeError('ADB authorization was not completed')
        return dev
    except BaseException:
        dev.close(); raise

def safe_remote(path):
    return bool(re.fullmatch(r'/[A-Za-z0-9._/-]+',path)) and '..' not in path and not path.endswith('/')

def push(dev, local, remote):
    """Chunked transfer adapted from cleanyourtv/b64push.py, MIT; see licenses/."""
    if not safe_remote(remote): raise ValueError('Use a plain absolute remote path, without ..')
    data = Path(local).read_bytes()
    expected = hashlib.md5(data).hexdigest()
    b64, part = remote+'.b64.part',remote+'.part'
    q = shlex.quote
    chunk = min(48*1024,dev.max_chunk_size*3//4-128)
    chunk -= chunk%3
    if chunk <= 0: raise RuntimeError('ADB payload limit is too small')
    try:
        dev.shell(f'rm -f {q(b64)} {q(part)} && : > {q(b64)}')
        for offset in range(0,len(data),chunk):
            encoded = base64.b64encode(data[offset:offset+chunk]).decode()
            if 'chunk-ok' not in dev.shell(f"echo '{encoded}' >> {q(b64)} && echo chunk-ok",read_timeout_s=60):
                raise RuntimeError('Chunk transfer failed')
        result = dev.shell(f'base64 -d < {q(b64)} > {q(part)} && md5sum {q(part)}',read_timeout_s=120)
        if not result.split() or result.split()[0] != expected:
            raise RuntimeError('MD5 mismatch: destination was not replaced')
        if 'moved-ok' not in dev.shell(f'mv -f {q(part)} {q(remote)} && echo moved-ok'):
            raise RuntimeError('Could not replace destination')
        print('Verified MD5 transfer:',remote)
    finally:
        try: dev.shell(f'rm -f {q(b64)} {q(part)}')
        except Exception: pass

def main():
    p = argparse.ArgumentParser(description=__doc__)
    sub = p.add_subparsers(dest='action',required=True)
    s=sub.add_parser('shell');s.add_argument('command')
    s=sub.add_parser('screenshot');s.add_argument('output',type=Path)
    s=sub.add_parser('push');s.add_argument('local');s.add_argument('remote')
    s=sub.add_parser('pull');s.add_argument('remote');s.add_argument('local',type=Path)
    a=p.parse_args(); dev=connect()
    try:
        if a.action=='shell': print(dev.shell(a.command,read_timeout_s=30),end='')
        elif a.action=='push': push(dev,a.local,a.remote)
        elif a.action=='pull':
            a.local.parent.mkdir(parents=True,exist_ok=True)
            dev.pull(a.remote,str(a.local),read_timeout_s=300)
        else:
            time.sleep(1)
            data=dev.shell('screencap -p',decode=False,read_timeout_s=30)
            if not data: raise RuntimeError('Empty screenshot; protected video may not be capturable.')
            if not data.startswith(b'\x89PNG\r\n\x1a\n'): raise RuntimeError('Capture was not a PNG')
            a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_bytes(data)
            print('Saved',a.output)
    finally: dev.close()

if __name__=='__main__': main()
