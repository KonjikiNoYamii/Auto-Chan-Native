#!/usr/bin/env python3
"""Silica Assistant - wireless input server for laptop.
Run: python3 silica-server.py
Then connect from the Android app (wireless mode).
"""
import socket
import subprocess
import os
import threading

HOST = '0.0.0.0'
PORT = 9999
DISCOVERY_PORT = 9998

os.environ['DISPLAY'] = ':0'


def handle_client(conn):
    shell = subprocess.Popen(
        ['bash'], stdin=subprocess.PIPE, text=True, bufsize=1,
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL
    )
    buf = b''
    with conn:
        conn.settimeout(None)
        while True:
            try:
                data = conn.recv(4096)
                if not data:
                    break
                buf += data
                while b'\n' in buf:
                    line, buf = buf.split(b'\n', 1)
                    cmd = line.decode().strip()
                    if cmd:
                        shell.stdin.write(cmd + '\n')
                        shell.stdin.flush()
            except Exception:
                break
    shell.terminate()


def discovery_listener():
    """Respond to UDP discovery requests from Android."""
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.bind(('', DISCOVERY_PORT))
    sock.settimeout(1)
    while True:
        try:
            data, addr = sock.recvfrom(1024)
            if data.strip() == b'SILICA_DISCOVER':
                sock.sendto(b'SILICA_SERVER', addr)
        except socket.timeout:
            continue
        except:
            break


def main():
    t = threading.Thread(target=discovery_listener, daemon=True)
    t.start()

    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    s.bind((HOST, PORT))
    s.listen()
    print(f"silica-server: listening on {HOST}:{PORT}")
    while True:
        conn, addr = s.accept()
        print(f"silica-server: client connected: {addr}")
        threading.Thread(target=handle_client, args=(conn,), daemon=True).start()


if __name__ == '__main__':
    main()
