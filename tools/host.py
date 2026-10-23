#!/usr/bin/env python3
"""Serve only locally built helper APKs; accept Bridge registration from TV_HOST only."""
import argparse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import ipaddress
import json
import os
import re
from common import ROOT, host, private_state

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--bind',required=True,help='This computer LAN IPv4 address; same as Bridge --host-ip')
    a=p.parse_args();ipaddress.IPv4Address(a.bind)
    tv=ipaddress.IPv4Address(host());state=private_state()
    routes={'/tv-bridge.apk':ROOT/'build/bridge/bridge.apk',
            '/adb-control.apk':ROOT/'build/adb-control/adb-control.apk'}
    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            if self.path=='/':
                data=b'<h1>Local TV toolkit</h1><a href="/tv-bridge.apk">TV Bridge APK</a><br><a href="/adb-control.apk">ADB Control APK</a>'
                mime='text/html; charset=utf-8'
            elif self.path in routes and routes[self.path].is_file():
                data=routes[self.path].read_bytes();mime='application/vnd.android.package-archive'
            else: self.send_error(404);return
            self.send_response(200);self.send_header('Content-Type',mime)
            self.send_header('Content-Length',str(len(data)));self.send_header('Cache-Control','no-store')
            if self.path in routes:
                self.send_header('Content-Disposition','attachment; filename="'+self.path[1:]+'"')
            self.end_headers();self.wfile.write(data)
        def do_POST(self):
            if self.path!='/register' or ipaddress.ip_address(self.client_address[0])!=tv:
                self.send_error(403);return
            try:
                length=int(self.headers.get('Content-Length','0'))
                if not 0<length<=2048: raise ValueError('Invalid length')
                d=json.loads(self.rfile.read(length))
                if d.get('package')!='local.tv.bridge' or d.get('port')!=18766 or not re.fullmatch(r'[a-f0-9-]{72}',d.get('token','')):
                    raise ValueError('Invalid registration')
                record={k:d[k] for k in ['token','port','package']};record['host']=str(tv)
                target=state/'bridge-connection.json'
                target.write_text(json.dumps(record));target.chmod(0o600)
            except (ValueError,TypeError,OSError): self.send_error(400);return
            self.send_response(200);self.send_header('Content-Length','2');self.end_headers();self.wfile.write(b'OK')
            print('Bridge registered. Token saved privately; not printed.',flush=True)
        def log_message(self,*args): pass
    print(f'Local APK page: http://{a.bind}:18765/ (Ctrl-C stops)',flush=True)
    ThreadingHTTPServer((a.bind,18765),Handler).serve_forever()

if __name__=='__main__': main()
