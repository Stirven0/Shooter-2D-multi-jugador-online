#!/usr/bin/env python3
"""Bridge: Hermes MCP stdio transport ↔ Shooter client TCP MCP server."""
import socket
import sys

def main():
    host = sys.argv[1] if len(sys.argv) > 1 else "localhost"
    port = int(sys.argv[2]) if len(sys.argv) > 2 else 4567

    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.connect((host, port))
    sock.settimeout(30)

    buf = b""

    while True:
        # Read from stdin (Hermes → bridge)
        ready = sys.stdin.buffer.read1(65536)
        if not ready:
            break

        # Forward to TCP
        sock.sendall(ready)

        # Read response from TCP
        while b"\n" not in buf:
            chunk = sock.recv(65536)
            if not chunk:
                return
            buf += chunk

        line, buf = buf.split(b"\n", 1)
        # Write to stdout (bridge → Hermes)
        sys.stdout.buffer.write(line + b"\n")
        sys.stdout.buffer.flush()


if __name__ == "__main__":
    main()
