"""The optional public tunnel origin serves only the selected APK."""
from http.server import ThreadingHTTPServer
from pathlib import Path
import sys
import tempfile
import threading
import unittest
import urllib.error
import urllib.request

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
from transfer import handler_for

class TransferTests(unittest.TestCase):
    def setUp(self):
        self.folder=tempfile.TemporaryDirectory()
        self.apk=Path(self.folder.name)/'bridge.apk';self.apk.write_bytes(b'APK fixture bytes')
        self.server=ThreadingHTTPServer(('127.0.0.1',0),handler_for(self.apk,'/tv-bridge.apk'))
        self.thread=threading.Thread(target=self.server.serve_forever,daemon=True);self.thread.start()
        self.base='http://127.0.0.1:'+str(self.server.server_port)
    def tearDown(self):
        self.server.shutdown();self.server.server_close();self.thread.join();self.folder.cleanup()
    def test_exact_apk_payload_and_mime(self):
        with urllib.request.urlopen(self.base+'/tv-bridge.apk') as r:
            self.assertEqual(r.read(),self.apk.read_bytes())
            self.assertEqual(r.headers['Content-Type'],'application/vnd.android.package-archive')
    def test_private_and_diagnostic_routes_are_absent(self):
        for route in ['/','/register','/status','/screen.jpg','/diagnostics','/.state/adbkey','/../bridge.apk']:
            with self.subTest(route=route):
                with self.assertRaises(urllib.error.HTTPError) as caught:urllib.request.urlopen(self.base+route)
                self.assertEqual(caught.exception.code,404)
    def test_registration_cannot_write_state_through_tunnel(self):
        request=urllib.request.Request(self.base+'/register',data=b'{}')
        with self.assertRaises(urllib.error.HTTPError) as caught:urllib.request.urlopen(request)
        self.assertEqual(caught.exception.code,405)

if __name__=='__main__':unittest.main()
