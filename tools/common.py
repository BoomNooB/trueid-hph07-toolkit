"""Shared configuration. Importing this module never contacts a TV or creates keys."""
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
STATE = Path(os.environ.get('TV_STATE_DIR', ROOT/'.state')).expanduser().resolve()

def private_state():
    os.umask(0o077)
    STATE.mkdir(parents=True, exist_ok=True, mode=0o700)
    return STATE

def host():
    value = os.environ.get('TV_HOST', '').strip()
    if not value:
        raise ValueError('Set TV_HOST to your own box address first. No network discovery is performed.')
    return value
