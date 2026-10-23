"""Pair with the user's TV and send explicit remote commands over stdin."""
import asyncio
import json
import os
from pathlib import Path

from androidtvremote2 import AndroidTVRemote, InvalidAuth

from common import private_state, host
ROOT = private_state()


async def main():
    os.umask(0o077)
    remote = AndroidTVRemote(
        "Mac TV assistance", str(ROOT / "remote-cert.pem"),
        str(ROOT / "remote-key.pem"), host(), enable_voice=False,
    )
    await remote.async_generate_cert_if_missing()
    try:
        await asyncio.wait_for(remote.async_connect(), timeout=15)
    except InvalidAuth:
        await asyncio.wait_for(remote.async_start_pairing(), timeout=15)
        print("PAIRING: Read the six-character code on the TV.", flush=True)
        code = await asyncio.to_thread(input, "Pairing code: ")
        await asyncio.wait_for(remote.async_finish_pairing(code.strip()), timeout=15)
        await asyncio.wait_for(remote.async_connect(), timeout=15)
    remote.add_is_available_updated_callback(
        lambda available: print("REMOTE_AVAILABLE=" + str(available), flush=True)
    )
    remote.add_current_app_updated_callback(
        lambda app: print("CURRENT_APP=" + str(app), flush=True)
    )
    remote.keep_reconnecting()
    print("CONNECTED; current_app=" + str(remote.current_app), flush=True)
    print("Commands: status, key NAME, text TEXT, launch URI, quit", flush=True)
    try:
        while True:
            line = await asyncio.to_thread(input, "> ")
            action, _, value = line.strip().partition(" ")
            if action == "quit":
                break
            if action == "status":
                print(json.dumps({"current_app": remote.current_app, "is_on": remote.is_on, "voice": remote.is_voice_enabled}), flush=True)
            elif action == "key" and value:
                remote.send_key_command(value)
            elif action == "tap" and value:
                remote.send_key_command(value, "START_LONG")
                await asyncio.sleep(0.08)
                remote.send_key_command(value, "END_LONG")
            elif action == "text" and value:
                remote.send_text(value)
            elif action == "launch" and value:
                remote.send_launch_app_command(value)
            elif action:
                print("Unknown command", flush=True)
            await asyncio.sleep(0.3)
    finally:
        remote.disconnect()


if __name__ == "__main__":
    asyncio.run(main())
