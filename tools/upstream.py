#!/usr/bin/env python3
"""Fetch the launcher source at the tested revision; never modify a sibling working checkout."""
import subprocess
from common import ROOT

COMMIT='30626a8e4714f86bf837384b109b5c73ee736917'
DEST=ROOT/'.deps/cleanyourtv'

def main():
    DEST.parent.mkdir(exist_ok=True)
    fresh = not DEST.exists()
    if fresh:
        subprocess.run(['git','clone','--no-checkout','https://github.com/Gathin23/cleanyourtv.git',str(DEST)],check=True)
    dirty='' if fresh else subprocess.run(['git','-C',str(DEST),'status','--porcelain'],capture_output=True,text=True,check=True).stdout.strip()
    if dirty: raise SystemExit('Upstream checkout has changes; preserve them before fetching another revision.')
    subprocess.run(['git','-C',str(DEST),'checkout','--detach',COMMIT],check=True)
    print('Pinned source ready:',DEST)

if __name__=='__main__': main()
