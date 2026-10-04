#!/usr/bin/env python3
"""PC side puller (wa-auto mode) -- phone e server chara direct chat collection.

Kothay cholbe: tomar PC te, USB cable diye phone connect kora obosthay.

Ki kore:
  1. `adb` diye phone er WhatsApp backup folder theke file gulo pull kore
     (msgstore-*.crypt14 / .db.tar.gz -- WhatsApp "Chat backup" button e
     chaaple ei file gulo hoy; root laghe na).
  2. Protita file er sathe sathe WhatsApp Web (linked device) theke raw
     message stream-o dhorte pare, jodi tumi ekbar browser e login kore
     Chrome/Edge remote debugging chalu kore dao (ekbar-i lagbe, session
     saved thake). Eta optional -- `--no-web` dile shudhu file pull hobe.
  3. Sob kuchu ./wa_chats/<tarikh>/ folder e jma hoy. Pore konodin server
     ba upload er dorkar nei.

Zoruri (PC te ekbar):
  - Android platform-tools (adb) install, phone te USB debugging ON.
  - Phone theke app e "Auto mode" ON koro (app khali permission nibe,
    upload bondho rakhbe) ar WhatsApp Settings > Chats > Chat backup >
    "Back up to device" eche -- daily automatic backup.
  - Optional web archive: google-chrome --remote-debugging-port=9222
    diye Chrome khule WhatsApp Web e QR scan kore login koro.

Usage:
  python3 pc_puller.py            # ekbar run (pull + optional web snapshot)
  python3 pc_puller.py --watch    # protok 5 minik cholte thake
  python3 pc_puller.py --no-web   # shudhu adb file pull
"""

import argparse
import datetime as dt
import glob
import hashlib
import json
import os
import re
import shutil
import socket
import subprocess
import sys
import time
import urllib.request

OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "wa_chats")
CRYPT_RE = re.compile(r"^msgstore.*\.(crypt\d+|db\.tar\.gz)$", re.I)


def log(msg):
    print(f"[{dt.datetime.now():%H:%M:%S}] {msg}", flush=True)


# ---------------------------------------------------------------- adb pulls
def adb_available():
    return shutil.which("adb") is not None


def adb_serials():
    try:
        out = subprocess.run(["adb", "devices"], capture_output=True, text=True,
                             timeout=10).stdout
    except Exception:
        return []
    serials = []
    for line in out.splitlines()[1:]:
        parts = line.split()
        if len(parts) >= 2 and parts[1] == "device":
            serials.append(parts[0])
    return serials


WHATSAPP_BACKUP_DIRS = [
    "/sdcard/WhatsApp/Databases",
    "/sdcard/Android/media/com.whatsapp/WhatsApp/Databases",
]


def pull_backup_files(serial):
    """Pull msgstore.cryptXX files from the phone into today's folder."""
    day_dir = os.path.join(OUT_DIR, dt.date.today().isoformat())
    os.makedirs(day_dir, exist_ok=True)
    got = 0
    base_cmd = ["adb", "-s", serial]
    for rdir in WHATSAPP_BACKUP_DIRS:
        listing = subprocess.run(base_cmd + ["shell", "ls", rdir],
                                 capture_output=True, text=True, timeout=20)
        names = [n.strip() for n in listing.stdout.splitlines() if CRYPT_RE.match(n.strip())]
        for name in names:
            dest = os.path.join(day_dir, name)
            if os.path.exists(dest):
                continue  # already pulled
            r = subprocess.run(base_cmd + ["pull", f"{rdir}/{name}", dest],
                               capture_output=True, text=True, timeout=600)
            if r.returncode == 0 and os.path.getsize(dest) > 0:
                sha = hashlib.sha256(open(dest, "rb").read()).hexdigest()[:16]
                log(f"PULLED {name} ({os.path.getsize(dest)} bytes, sha={sha})")
                got += 1
            else:
                log(f"FAILED to pull {name}: {r.stderr.strip()}")
    return got


# ------------------------------------------------------- optional web stream
CDP_PORT = 9222


def cdp_ws_url():
    """Find the WhatsApp Web page target on a remote-debugging Chrome."""
    try:
        data = json.load(urllib.request.urlopen(f"http://127.0.0.1:{CDP_PORT}/json", timeout=3))
    except Exception:
        return None
    for t in data:
        if "web.whatsapp.com" in (t.get("url", "") or "") and t.get("webSocketDebuggerUrl"):
            return t["webSocketDebuggerUrl"]
    return None


def web_snapshot():
    """Lightweight check that WA Web is alive + save a visible-chat snapshot.

    Full Store extraction needs a websocket client (pip install websocket-client).
    If it's installed we do a proper Store export of recent chats/messages.
    """
    ws_url = cdp_ws_url()
    if not ws_url:
        return False
    try:
        import websocket  # pip install websocket-client
    except ImportError:
        log("websocket-client not installed -> skipping web snapshot (pip install websocket-client)")
        return False

    def send(ws, mid, method, params=None):
        ws.send(json.dumps({"id": mid, "method": method, "params": params or {}}))
        while True:
            msg = json.loads(ws.recv())
            if msg.get("id") == mid:
                return msg

    js = """(() => {
      const out = {chats: [], messages: []};
      try {
        const store = window.Store || null;
        if (store && store.Chat && store.Chat.getModelsArray) {
          out.chats = store.Chat.getModelsArray().map(c => ({id: c.id && c.id._serialized, name: c.name, t: c.t}));
        }
        if (store && store.Msg && store.Msg.getModelsArray) {
          out.messages = store.Msg.getModelsArray().slice(-2000).map(m => ({
            id: m.id && m.id._serialized, body: m.body, t: m.t,
            from: m.from && m.from._serialized, to: m.to && m.to._serialized, type: m.type}));
        }
      } catch (e) { out.error = String(e); }
      return JSON.stringify(out);
    })()"""

    ws = websocket.create_connection(ws_url, timeout=15)
    try:
        send(ws, 1, "Runtime.enable")
        resp = send(ws, 2, "Runtime.evaluate", {"expression": js, "returnByValue": True})
        raw = resp.get("result", {}).get("result", {}).get("value")
        if not raw:
            return False
        data = json.loads(raw)
        day_dir = os.path.join(OUT_DIR, dt.date.today().isoformat())
        os.makedirs(day_dir, exist_ok=True)
        stamp = dt.datetime.now().strftime("%H%M%S")
        path = os.path.join(day_dir, f"wa_web_{stamp}.json")
        with open(path, "w", encoding="utf-8") as fh:
            json.dump(data, fh, ensure_ascii=False, indent=1)
        log(f"WEB SNAPSHOT -> {path} ({len(data.get('messages', []))} msgs)")
        return True
    finally:
        ws.close()


# ------------------------------------------------------- internet mode (no USB)
def load_server_config():
    """Read server_url / token from ./puller_config.json or env vars."""
    cfg = {}
    path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "puller_config.json")
    if os.path.exists(path):
        with open(path, encoding="utf-8") as fh:
            cfg = json.load(fh)
    url = os.environ.get("WA_SERVER_URL") or cfg.get("server_url") or ""
    token = os.environ.get("WA_TOKEN") or cfg.get("token") or ""
    return url.rstrip("/"), token


def http_get_json(url, token=None):
    req = urllib.request.Request(url)
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.load(resp)


def pull_from_server(url, token):
    """List backups on the server and download any we don't have yet. No USB needed."""
    day_dir = os.path.join(OUT_DIR, dt.date.today().isoformat())
    os.makedirs(day_dir, exist_ok=True)
    listing = http_get_json(f"{url}/api/list", token)
    got = 0
    for entry in listing.get("backups", []):
        name = entry["file"]
        dest = os.path.join(day_dir, name)
        if os.path.exists(dest) and os.path.getsize(dest) == entry.get("size", -1):
            continue  # already have it, same size
        req = urllib.request.Request(f"{url}/api/download/{entry['device_id']}/{name}")
        req.add_header("Authorization", f"Bearer {token}")
        with urllib.request.urlopen(req, timeout=600) as resp:
            tmp = dest + ".part"
            with open(tmp, "wb") as fh:
                while True:
                    chunk = resp.read(1 << 20)
                    if not chunk:
                        break
                    fh.write(chunk)
        os.replace(tmp, dest)
        log(f"DOWNLOADED {name} ({os.path.getsize(dest)} bytes)")
        got += 1
    return got


# --------------------------------------------------------------------- main
def run_once(do_web=True, do_adb=True, server=None):
    total = 0
    if server:
        url, token = server
        try:
            total += pull_from_server(url, token)
        except Exception as e:
            log(f"server pull failed: {e}")
    if do_adb:
        serials = adb_serials()
        if not serials and not server:
            log("No adb device found. Check USB cable + 'adb devices' (authorize popup!).")
        for s in serials:
            total += pull_backup_files(s)
    web_ok = False
    if do_web:
        try:
            web_ok = web_snapshot()
        except Exception as e:
            log(f"web snapshot failed: {e}")
    return total, web_ok


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--watch", action="store_true", help="run every 5 minutes")
    ap.add_argument("--interval", type=int, default=300, help="watch interval seconds")
    ap.add_argument("--no-web", action="store_true", help="skip WhatsApp Web snapshot")
    ap.add_argument("--no-adb", action="store_true",
                    help="internet mode: never touch USB/adb, only pull from server")
    ap.add_argument("--server", default=None, help="server URL (else puller_config.json / WA_SERVER_URL)")
    ap.add_argument("--token", default=None, help="auth token (else puller_config.json / WA_TOKEN)")
    args = ap.parse_args()

    os.makedirs(OUT_DIR, exist_ok=True)
    log(f"Output folder: {OUT_DIR}")

    url, token = load_server_config()
    if args.server:
        url = args.server.rstrip("/")
    if args.token:
        token = args.token
    server = (url, token) if url and token else None
    if server:
        log(f"Internet mode ON -> pulling from {url} (no USB needed)")
    elif not args.no_adb:
        if not adb_available():
            log("WARNING: adb not found in PATH. Install Android platform-tools.")

    if args.no_adb and not server:
        log("ERROR: --no-adb needs a server (see puller_config.json). Exiting.")
        sys.exit(1)

    while True:
        n, _ = run_once(do_web=not args.no_web, do_adb=not args.no_adb, server=server)
        log(f"Cycle done: {n} new file(s).")
        if not args.watch:
            break
        time.sleep(args.interval)


if __name__ == "__main__":
    main()
