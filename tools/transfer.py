#!/usr/bin/env python3
"""Optional tunnel origin: loopback server exposing one built APK and no diagnostics/state."""
import argparse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from common import ROOT

def handler_for(apk, route):
    apk=Path(apk)
    class Transfer(BaseHTTPRequestHandler):
        def do_GET(self):
            if self.path!=route:
                self.send_error(404);return
            try:payload=apk.read_bytes()
            except OSError:self.send_error(404);return
            self.send_response(200)
            self.send_header('Content-Type','application/vnd.android.package-archive')
            self.send_header('Content-Disposition','attachment; filename="'+route[1:]+'"')
            self.send_header('Content-Length',str(len(payload)))
            self.send_header('Cache-Control','no-store')
            self.end_headers();self.wfile.write(payload)
        def do_POST(self):self.send_error(405)
        def log_message(self,*args):pass
    return Transfer

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('app',choices=['bridge','adb-control'])
    p.add_argument('--port',type=int,default=18767)
    a=p.parse_args()
    apk=ROOT/'build'/a.app/(a.app+'.apk')
    if not apk.is_file():p.error('Build the selected helper first.')
    route='/tv-bridge.apk' if a.app=='bridge' else '/adb-control.apk'
    print(f'APK-only origin: http://127.0.0.1:{a.port}{route}; keep alive until verified installation.',flush=True)
    server=ThreadingHTTPServer(('127.0.0.1',a.port),handler_for(apk,route))
    try:server.serve_forever()
    except KeyboardInterrupt:pass
    finally:server.server_close()

if __name__=='__main__':main()
