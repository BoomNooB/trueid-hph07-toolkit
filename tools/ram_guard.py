#!/usr/bin/env python3
"""Long-running watcher: force-stops chosen packages whenever they aren't the foreground
app, instead of waiting for Android's own cache eviction under memory pressure. Meant to
run continuously somewhere that's on 24/7 (a home server, a Raspberry Pi, cron on a
laptop) — not on the TV itself, and not as a one-shot.

Why force-stop and not killBackgroundProcesses: some apps pin themselves with a silent
foreground service (no visible notification) specifically so Android's normal background
killer leaves them alone. TrueID TV is one: it runs AppRemoteService (phone-as-remote
pairing) and TvSettingsMenuService continuously, which makes killBackgroundProcesses a
no-op against it. am force-stop (an ADB shell / "shell" UID operation, not available to a
normal installed app) overrides that; an ordinary cached app with no such pin would also
just be left to Android's own eviction, so running this against one costs nothing but
reclaims its RAM sooner.

Usage:
    TV_HOST=192.168.1.50 python tools/ram_guard.py com.truedmp.idtv com.doonung.dtv

Measured on one HPH07 (2GB RAM, idle elsewhere): TrueID TV itself used about 88MB of
private RAM while backgrounded, but the system's free+available RAM rose by roughly
240MB after force-stopping it — background services pull in extra cached pages beyond
their own PSS, so the real-world win is usually bigger than a single app's own footprint
suggests. Numbers vary by device and what else is installed; measure your own with
`tools/tv.py 'dumpsys meminfo <package>'` and `tools/tv.py 'cat /proc/meminfo'` before
and after.

A systemd unit for running this continuously on a Linux box you already leave on:

    [Unit]
    Description=RAM guard for the TV (ram_guard.py)
    After=network-online.target
    Wants=network-online.target

    [Service]
    Type=simple
    Environment=TV_HOST=192.168.1.50
    Environment=TV_STATE_DIR=/opt/tv-ram-guard/.state
    WorkingDirectory=/opt/tv-ram-guard
    ExecStart=/opt/tv-ram-guard/.venv/bin/python /opt/tv-ram-guard/tools/ram_guard.py com.truedmp.idtv
    Restart=always
    RestartSec=10

    [Install]
    WantedBy=multi-user.target
"""
import argparse
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from common import host, private_state  # noqa: E402

POLL_S = 15
START_GRACE_S = 25     # a fresh cold start can briefly show a different mCurrentFocus
CONFIRM_POLLS = 2       # must read "not foreground" this many polls in a row before killing


def connect():
    from adb_shell.adb_device import AdbDeviceTcp
    from adb_shell.auth.keygen import keygen
    from adb_shell.auth.sign_pythonrsa import PythonRSASigner
    key = private_state() / 'adbkey'
    if not key.exists():
        keygen(str(key))
    key.chmod(0o600)
    signer = PythonRSASigner.FromRSAKeyPath(str(key))
    dev = AdbDeviceTcp(host(), 5555, default_transport_timeout_s=20)
    dev.connect(rsa_keys=[signer], auth_timeout_s=20)
    return dev


class Guard:
    def __init__(self, pkg):
        self.pkg = pkg
        self.away_streak = 0
        self.tracked_pid = None
        self.pid_seen_at = 0.0
        self.kills = 0

    def poll(self, dev, focus_line):
        pid = dev.shell(f"pidof {self.pkg}").strip()
        now = time.monotonic()
        if pid != self.tracked_pid:
            self.tracked_pid, self.pid_seen_at, self.away_streak = pid, now, 0
        if not pid:
            self.away_streak = 0
            return
        if self.pkg in focus_line:
            self.away_streak = 0
            return
        if now - self.pid_seen_at < START_GRACE_S:
            return
        self.away_streak += 1
        if self.away_streak >= CONFIRM_POLLS:
            dev.shell(f"am force-stop {self.pkg}")
            self.kills += 1
            print(f"force-stopped {self.pkg} (was pid {pid}); kills={self.kills}", flush=True)
            self.tracked_pid, self.away_streak = None, 0


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('packages', nargs='+', help='Package names to keep reclaimed, e.g. com.truedmp.idtv')
    args = parser.parse_args()

    device = None
    guards = {pkg: Guard(pkg) for pkg in args.packages}
    print(f"Watching: {', '.join(args.packages)} (poll every {POLL_S}s)", flush=True)
    while True:
        try:
            if device is None:
                device = connect()
                print("connected", flush=True)
            focus_line = device.shell("dumpsys window windows 2>/dev/null | grep mCurrentFocus")
            for guard in guards.values():
                guard.poll(device, focus_line)
        except Exception as e:
            print("connection issue, will retry:", e, flush=True)
            device = None
        time.sleep(POLL_S)


if __name__ == '__main__':
    main()
