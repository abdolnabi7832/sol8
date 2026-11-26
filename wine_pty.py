#!/usr/bin/env python3
"""Run a Windows/Wine console process inside a real Linux PTY to prevent WinError 6 stdio crashes."""

import os
import pty
import select
import subprocess
import sys


def main() -> int:
    if len(sys.argv) < 2:
        print("Usage: wine_pty.py <cmd> [args...]", file=sys.stderr)
        return 2

    master, slave = pty.openpty()
    proc = subprocess.Popen(
        sys.argv[1:],
        stdin=slave,
        stdout=slave,
        stderr=slave,
        close_fds=True,
    )
    os.close(slave)

    while True:
        try:
            r, _, _ = select.select([master], [], [], 1.0)
            if r:
                data = os.read(master, 4096)
                if not data:
                    break
                sys.stdout.buffer.write(data)
                sys.stdout.buffer.flush()
        except OSError:
            break

        if proc.poll() is not None:
            while True:
                try:
                    r, _, _ = select.select([master], [], [], 0.05)
                    if not r:
                        break
                    data = os.read(master, 4096)
                    if not data:
                        break
                    sys.stdout.buffer.write(data)
                    sys.stdout.buffer.flush()
                except OSError:
                    break
            break

    os.close(master)
    rc = proc.wait()
    print(f"[wine_pty] process exited rc={rc}", flush=True)
    return rc


if __name__ == "__main__":
    raise SystemExit(main())
