"""Launches the user's PCL2 Forge client headlessly and auto-joins a local server.

Builds the classpath and argument list from the merged version json, uses an
offline test account (so the real player data stays untouched) and appends
--quickPlayMultiplayer to skip the title screen.
"""
import hashlib
import json
import subprocess
import sys
import time
import uuid
from pathlib import Path

ROOT = Path(r"D:\Minecraft")
VERSION = "Last One"
VERSION_DIR = ROOT / "versions" / VERSION
NATIVES = VERSION_DIR / f"{VERSION}-natives"
JAVA = Path(r"C:\Program Files\Java\jdk-17\bin\java.exe")
USERNAME = sys.argv[2] if len(sys.argv) > 2 else "qstestbot"
ADDRESS = sys.argv[1] if len(sys.argv) > 1 else "127.0.0.1:25535"
LOG = Path(r"D:\projects\confluence_currency_combat\build\tmp\client-live.log")


def allowed(rules):
    if not rules:
        return True
    if any("features" in rule for rule in rules):
        # Feature-gated arguments (quick-play variants) are not something this launcher enables.
        return False
    result = False
    for rule in rules:
        if rule.get("action") == "allow" and rule.get("os", {}).get("name", "windows") == "windows":
            result = True
        elif rule.get("action") == "disallow" and rule.get("os", {}).get("name", "windows") == "windows":
            result = False
    return result


def flatten(value):
    return [value] if isinstance(value, str) else list(value)


def main():
    data = json.loads((VERSION_DIR / f"{VERSION}.json").read_text(encoding="utf-8"))
    classpath = []
    for lib in data["libraries"]:
        if not allowed(lib.get("rules")):
            continue
        artifact = lib.get("downloads", {}).get("artifact")
        if artifact:
            classpath.append(str(ROOT / "libraries" / artifact["path"]))

    digest = bytearray(hashlib.md5(f"OfflinePlayer:{USERNAME}".encode()).digest())
    digest[6] = (digest[6] & 0x0F) | 0x30
    digest[8] = (digest[8] & 0x3F) | 0x80
    offline_uuid = str(uuid.UUID(bytes=bytes(digest)))

    values = {
        "natives_directory": str(NATIVES),
        "launcher_name": "pcl2",
        "launcher_version": "test",
        "classpath": ";".join(classpath),
        "classpath_separator": ";",
        "library_directory": str(ROOT / "libraries"),
        "auth_player_name": USERNAME,
        "version_name": VERSION,
        "game_directory": str(VERSION_DIR),
        "assets_root": str(ROOT / "assets"),
        "assets_index_name": data["assetIndex"]["id"],
        "auth_uuid": offline_uuid.replace("-", ""),
        "auth_access_token": "0",
        "user_type": "legacy",
        "version_type": data.get("type", "release"),
        "resolution_width": "854",
        "resolution_height": "480",
        "clientid": "0",
        "auth_xuid": "0",
    }

    def expand(arg):
        for key, value in values.items():
            arg = arg.replace("${" + key + "}", value)
        return arg

    def collect(entries):
        out = []
        for entry in entries:
            if isinstance(entry, str):
                out.append(expand(entry))
            else:
                if entry.get("rules") and not allowed(entry["rules"]):
                    continue
                out.extend(expand(a) for a in flatten(entry["value"]))
        return out

    command = [str(JAVA), "-Xmx4G", "-Xms1G"]
    command += collect(data["arguments"]["jvm"])
    command.append(data["mainClass"])
    command += collect(data["arguments"]["game"])
    command += ["--quickPlayMultiplayer", ADDRESS]

    print("libraries:", len(classpath))
    print("client java:", command[0], "| address:", ADDRESS, "| user:", USERNAME)
    with LOG.open("wb") as log:
        proc = subprocess.Popen(command, cwd=str(VERSION_DIR), stdout=log, stderr=subprocess.STDOUT)
    print(f"client pid {proc.pid}, log {LOG}", flush=True)
    for _ in range(150):
        if proc.poll() is not None:
            print("CLIENT_EXITED", proc.returncode)
            return
        time.sleep(1)
    print("CLIENT_RUNNING")


if __name__ == "__main__":
    main()
