#!/usr/bin/env python3
"""Only ConnectionRefused proves closure; timeouts and unreachable networks are inconclusive."""
import socket
from common import host

def main():
    target=host()
    try:
        with socket.create_connection((target,5555),timeout=5):pass
    except ConnectionRefusedError:
        print('CLOSED: the TV explicitly refused port 5555.');return 0
    except OSError:
        print('INCONCLUSIVE: could not reach the port. A timeout/privacy block does not prove closure.');return 2
    print('OPEN: turn debugging off, stop the maintenance session and check again.');return 1

if __name__=='__main__':raise SystemExit(main())
