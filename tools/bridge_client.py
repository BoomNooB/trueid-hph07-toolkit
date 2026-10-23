"""Read-only authenticated bridge client; never prints the connection token."""
import json
import os
import sys
import urllib.parse
import urllib.request
from pathlib import Path

from common import private_state
ROOT = private_state()
os.umask(0o077)
connection = json.loads((ROOT / 'bridge-connection.json').read_text())
endpoint = sys.argv[1]
if not endpoint.startswith(('/', 'http:','https:')):
    endpoint = '/' + endpoint
parsed = urllib.parse.urlsplit(endpoint)
if parsed.scheme or parsed.netloc or not parsed.path.startswith('/'):
    raise SystemExit('Use a local bridge endpoint, e.g. components?package=com.gathin.tvhome')
query = urllib.parse.urlencode(urllib.parse.parse_qsl(parsed.query) + [('token', connection['token'])])
url = f"http://{connection['host']}:{connection['port']}{parsed.path}?{query}"
with urllib.request.urlopen(url, timeout=15) as response:
    payload = response.read()
if len(sys.argv) > 2:
    output = ROOT / sys.argv[2]
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(payload)
    print(f'Saved {output} ({len(payload)} bytes)')
else:
    print(payload.decode())
