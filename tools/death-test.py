"""Waits for a player to join, then runs the death-sync check through RCON.

Sequence: give the player money, read the QShop wallet NBT, kill them, read it
again and print the bridge's own log lines from the running server.
"""
import re
import socket
import struct
import time
from pathlib import Path

SERVER_DIR = Path(r"D:\Last One 0.4.0.0 beta-5 服务端")
SERVER_LOG = Path(r"D:\projects\confluence_currency_combat\build\tmp\server-live.log")
TYPE_AUTH, TYPE_COMMAND = 3, 2
WAIT_MINUTES = 60


def settings():
    password, port = "", 25595
    for line in (SERVER_DIR / "server.properties").read_text(encoding="utf-8").splitlines():
        if line.startswith("rcon.password="):
            password = line.split("=", 1)[1]
        elif line.startswith("rcon.port="):
            port = int(line.split("=", 1)[1])
    return password, port


def _recv_exact(sock, count):
    buf = b""
    while len(buf) < count:
        chunk = sock.recv(count - len(buf))
        if not chunk:
            raise ConnectionError("rcon connection closed")
        buf += chunk
    return buf


class Rcon:
    def __init__(self):
        self.password, self.port = settings()
        self.sock = socket.create_connection(("127.0.0.1", self.port), timeout=15)
        self._send(1, TYPE_AUTH, self.password)

    def _send(self, req_id, req_type, body):
        payload = struct.pack("<ii", req_id, req_type) + body.encode("utf-8") + b"\x00\x00"
        self.sock.sendall(struct.pack("<i", len(payload)) + payload)
        length = struct.unpack("<i", _recv_exact(self.sock, 4))[0]
        return _recv_exact(self.sock, length)[8:-2].decode("utf-8", "replace")

    def cmd(self, command, req_id=2):
        return self._send(req_id, TYPE_COMMAND, command)


def online(rcon):
    response = rcon.cmd("list")
    match = re.search(r"players online:(.*)", response)
    return [n.strip() for n in match.group(1).split(",") if n.strip()] if match else []


def bridge_lines():
    if not SERVER_LOG.is_file():
        return []
    text = SERVER_LOG.read_bytes().decode("gbk", "ignore")
    return [l for l in text.splitlines()
            if "死亡：" in l or "外部变动对账" in l or "已拦截钱币掉落" in l or "存钱罐计入策略" in l]


def main():
    deadline = time.time() + WAIT_MINUTES * 60
    name = None
    while time.time() < deadline:
        try:
            rcon = Rcon()
            found = online(rcon)
            if found:
                name = found[0]
                break
        except Exception as exc:  # transient: server still starting, rcon busy, ...
            print(f"HEARTBEAT retry: {exc}", flush=True)
        time.sleep(10)
    if name is None:
        print("NO_PLAYER_JOINED")
        return

    print(f"PLAYER={name}", flush=True)
    print("RESPAWN RULE:", rcon.cmd("gamerule doImmediateRespawn true"), flush=True)
    print("GIVE:", rcon.cmd(f"qshop currency give {name} coins 5000000"), flush=True)
    print("CAPS_BEFORE:", rcon.cmd(f"data get entity {name} ForgeCaps"), flush=True)
    print("KILL:", rcon.cmd(f"kill {name}"), flush=True)
    time.sleep(6)
    print("CAPS_AFTER:", rcon.cmd(f"data get entity {name} ForgeCaps"), flush=True)
    print("--- bridge log ---", flush=True)
    for line in bridge_lines()[-12:]:
        print(line, flush=True)
    print("DONE", flush=True)


if __name__ == "__main__":
    main()
