"""Offline checks for transfer integrity, restore scope and accessibility preservation."""
import contextlib
import hashlib
import io
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import setup
import tv
import adb_closed

class FakeDevice:
    max_chunk_size=4096
    def __init__(self,digest):self.digest=digest;self.commands=[]
    def shell(self,command,**kwargs):
        self.commands.append(command)
        if 'md5sum' in command:return self.digest+'  /data/local/tmp/helper.apk.part\n'
        if 'echo chunk-ok' in command:return 'chunk-ok\n'
        if 'echo moved-ok' in command:return 'moved-ok\n'
        return ''

class SafetyTests(unittest.TestCase):
    def test_merge_keeps_other_accessibility_services(self):
        self.assertEqual(setup.merge_services('reader/.Service:other/.Service',setup.HOME_SERVICE),
                         'reader/.Service:other/.Service:'+setup.HOME_SERVICE)
        self.assertEqual(setup.merge_services(setup.HOME_SERVICE,setup.HOME_SERVICE),setup.HOME_SERVICE)
        self.assertEqual(setup.merge_services('null',setup.HOME_SERVICE),setup.HOME_SERVICE)

    def test_restore_rejects_unrecordable_core_packages(self):
        with self.assertRaises(ValueError):
            setup.restore_plan({'disabled':{'com.android.systemui':0}})

    def test_restore_preserves_original_state_and_stops_maintenance_last(self):
        ledger={'disabled':{setup.PACKAGES[0]:0,setup.PACKAGES[1]:3},
                'settings':{'enabled_accessibility_services':'reader/.Service','accessibility_enabled':'1'},
                'uninstall_block':False,'maintenance_permission_added':True}
        plan=setup.restore_plan(ledger)
        self.assertIn('pm default-state --user 0 '+setup.PACKAGES[0],plan)
        self.assertIn('pm disable-user --user 0 '+setup.PACKAGES[1],plan)
        self.assertIn('settings put secure enabled_accessibility_services reader/.Service',plan)
        self.assertIn('am stopservice',plan[-2]);self.assertIn('pm revoke',plan[-1])

    def test_missing_settings_restore_is_delete_not_literal_null(self):
        self.assertEqual(setup.restore_plan({'settings':{'enabled_accessibility_services':'null'}}),
                         ['settings delete secure enabled_accessibility_services'])

    def test_restore_stops_session_even_if_permission_was_already_granted(self):
        self.assertEqual(setup.restore_plan({'maintenance_started':True}),
                         ['am stopservice -n local.tv.adbcontrol/.AdbSessionService'])

    def test_enabled_state_is_user_zero(self):
        dump='User 0: installed=true enabled=0\nUser 10: installed=true enabled=3'
        self.assertEqual(setup.original_enabled(dump),0)
        with self.assertRaises(ValueError):setup.original_enabled('no user state')

    def test_push_rejects_shell_injection_and_traversal(self):
        for path in ['/tmp/x;id','/tmp/../x',"/tmp/a'b",'relative.apk','/tmp/']:
            self.assertFalse(tv.safe_remote(path),path)
        self.assertTrue(tv.safe_remote('/data/local/tmp/helper.apk'))

    def test_corrupt_push_never_replaces_destination(self):
        with tempfile.TemporaryDirectory() as folder:
            p=Path(folder)/'input';p.write_bytes(b'test bytes')
            dev=FakeDevice('0'*32)
            with self.assertRaises(RuntimeError):tv.push(dev,p,'/data/local/tmp/helper.apk')
            self.assertFalse(any('mv -f' in x for x in dev.commands))

    def test_chunked_push_checks_md5_then_moves(self):
        with tempfile.TemporaryDirectory() as folder:
            data=b'x'*10000;p=Path(folder)/'input';p.write_bytes(data)
            dev=FakeDevice(hashlib.md5(data).hexdigest())
            with contextlib.redirect_stdout(io.StringIO()):tv.push(dev,p,'/data/local/tmp/helper.apk')
            self.assertGreater(sum('echo chunk-ok' in c for c in dev.commands),1)
            digest=next(i for i,c in enumerate(dev.commands) if 'md5sum' in c)
            move=next(i for i,c in enumerate(dev.commands) if 'mv -f' in c)
            self.assertLess(digest,move)

    def test_timeout_is_not_closed(self):
        with patch.object(adb_closed,'host',return_value='example.invalid'),\
             patch.object(adb_closed.socket,'create_connection',side_effect=TimeoutError),\
             contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(adb_closed.main(),2)

    def test_explicit_refusal_is_closed(self):
        with patch.object(adb_closed,'host',return_value='example.invalid'),\
             patch.object(adb_closed.socket,'create_connection',side_effect=ConnectionRefusedError),\
             contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(adb_closed.main(),0)

if __name__=='__main__':unittest.main()
