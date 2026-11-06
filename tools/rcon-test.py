"""Minimal RCON client used only for the local death-sync test.

Reads the password and port from the server's own server.properties so the
credential never has to be printed or passed on a command line.
"""
import socket
import struct
import sys
from pathlib import Path

SERVER_DIR = Path(r"D:\Last One 0.4.0.0 beta-5 服务端")
TYPE_AUTH, TYPE_COMMAND = 3, 2


def settings():
    password, port = "", 25595
    for line in (SERVER_DIR / "server.properties").read_text(encoding="utf-8").splitlines():
        if line.startswith("rcon.password="):
            password = line.split("=", 1)[1]
        elif line.startswith("rcon.port="):
            port = int(line.split("=", 1)[1])
    return password, port


def send(sock, req_id, req_type, body):
    payload = struct.pack("<ii", req_id, req_type) + body.encode("utf-8") + b"\x00\x00"
    sock.sendall(struct.pack("<i", len(payload)) + payload)
    length = struct.unpack("<i", recv_exact(sock, 4))[0]
    data = recv_exact(sock, length)
    return struct.unpack("<ii", data[:8])[1], data[8:-2].decode("utf-8", "replace")


def recv_exact(sock, count):
    buf = b""
    while len(buf) < count:
        chunk = sock.recv(count - len(buf))
        if not chunk:
            raise ConnectionError("rcon connection closed")
        buf += chunk
    return buf


def main():
    password, port = settings()
    commands = sys.argv[1:]
    with socket.create_connection(("127.0.0.1", port), timeout=10) as sock:
        send(sock, 1, TYPE_AUTH, password)
        for index, command in enumerate(commands):
            _, response = send(sock, 2 + index, TYPE_COMMAND, command)
            print(f"$ {command}\n{response}\n")


if __name__ == "__main__":
    main()
