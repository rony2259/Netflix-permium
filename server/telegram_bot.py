#!/usr/bin/env python3
"""
Telegram forwarder for the WA-backup server (PC te chhat ropekh, Telegram e pawya).

Reads blobs uploaded by the phone app (same format as decrypt_backup.py:
[WAB\x01 magic][IV][AES-256-CBC]), decrypts them with your token, and sends
the original backup file to YOUR Telegram chat via a bot.

Usage:
    pip install requests cryptography
    export WA_BACKUP_TOKEN="same token as in the app"
    export TG_BOT_TOKEN="123456:ABC... (from @BotFather)"
    export TG_CHAT_ID="your numeric chat id (@userbotinfo or getupdates helps)"
    python3 telegram_bot.py                # one-shot: forward anything new
    python3 telegram_bot.py --watch        # keep running, forward every upload
    python3 telegram_bot.py --server http://127.0.0.1:8443/api/list   # pull from server

Notes:
 - Only .crypt## / .zip / .txt files are forwarded; anything else is skipped.
 - Files > 50 MB are split into parts (Telegram bot upload limit is 50 MB).
 - The bot token & chat id are secrets — keep this machine yours.
"""

import argparse
import hashlib
import os
import sys
import time
from pathlib import Path

try:
    import requests
except ImportError:
    sys.exit("pip install requests")
try:
    from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
except ImportError:
    sys.exit("pip install cryptography")

MAGIC = b"WAB\x01\x00\x00"
SALT = b"wa-backup-personal-v1"
ITERATIONS = 120_000
TG_LIMIT = 49 * 1024 * 1024  # stay under 50 MB
ALLOWED_EXT = {".crypt12", ".crypt13", ".crypt14", ".crypt15", ".crypt16",
               ".zip", ".txt", ".db", ".bin"}
STATE_FILE = Path(__file__).with_name(".telegram_forwarded.json")


def derive_key(token: str) -> bytes:
    return hashlib.pbkdf2_hmac("sha256", token.encode(), SALT, ITERATIONS, dklen=32)


def decrypt_blob(data: bytes, token: str) -> bytes:
    if not data.startswith(MAGIC):
        raise ValueError("not a WA-backup encrypted blob")
    body = data[len(MAGIC):]
    iv, ct = body[:16], body[16:]
    key = derive_key(token)
    dec = Cipher(algorithms.AES(key), modes.CBC(iv)).decryptor()
    padded = dec.update(ct) + dec.finalize()
    pad = padded[-1]
    if pad < 1 or pad > 16 or padded[-pad:] != bytes([pad]) * pad:
        raise ValueError("bad padding — wrong token?")
    return padded[:-pad]


class Telegram:
    def __init__(self, bot_token: str, chat_id: str):
        self.base = f"https://api.telegram.org/bot{bot_token}"
        self.chat_id = chat_id

    def send_document(self, path: Path, caption: str) -> None:
        with path.open("rb") as fh:
            r = requests.post(f"{self.base}/sendDocument",
                              data={"chat_id": self.chat_id, "caption": caption},
                              files={"document": (path.name, fh)},
                              timeout=300)
        if r.status_code != 200 or not r.json().get("ok"):
            raise RuntimeError(f"telegram send failed: {r.text}")

    def send_message(self, text: str) -> None:
        requests.post(f"{self.base}/sendMessage",
                      data={"chat_id": self.chat_id, "text": text}, timeout=30)


def load_state() -> dict:
    import json
    if STATE_FILE.exists():
        try:
            return json.loads(STATE_FILE.read_text())
        except Exception:
            return {}
    return {}


def save_state(state: dict) -> None:
    import json
    STATE_FILE.write_text(json.dumps(state, indent=0))


def forward_file(tg: Telegram, src: Path, token: str, state: dict) -> bool:
    """Decrypt src and push to Telegram unless already forwarded. Returns True on send."""
    st = src.stat()
    fid = f"{src}:{st.st_size}:{int(st.st_mtime)}"
    sha = hashlib.sha256(src.read_bytes()).hexdigest()
    if state.get(sha) == st.st_size:
        print(f"skip (already forwarded): {src.name}")
        return False

    data = src.read_bytes()
    try:
        plain = decrypt_blob(data, token)
    except ValueError as e:
        print(f"skip {src.name}: {e}", file=sys.stderr)
        return False

    tmpdir = Path(os.environ.get("TMPDIR", "/tmp"))
    out = tmpdir / f"tg_{src.stem}.bin"
    if len(plain) <= TG_LIMIT:
        out.write_bytes(plain)
        tg.send_document(out, f"WA backup: {src.name}")
        print(f"forwarded: {src.name} ({len(plain)} bytes)")
    else:
        n = (len(plain) + TG_LIMIT - 1) // TG_LIMIT
        for i in range(n):
            part = tmpdir / f"tg_{src.stem}.part{i+1}of{n}"
            part.write_bytes(plain[i * TG_LIMIT:(i + 1) * TG_LIMIT])
            tg.send_document(part, f"WA backup: {src.name} [{i+1}/{n}]")
            part.unlink(missing_ok=True)
            time.sleep(1)
        print(f"forwarded in {n} parts: {src.name} ({len(plain)} bytes)")
    out.unlink(missing_ok=True)
    state[sha] = st.st_size
    save_state(state)
    return True


def scan_dir(tg: Telegram, directory: Path, token: str, state: dict) -> None:
    for p in sorted(directory.rglob("*")):
        if not p.is_file() or p.suffix.lower() in ("", ".part", ".json"):
            continue
        if p.suffix.lower() not in ALLOWED_EXT and "backup" not in p.name.lower():
            continue
        try:
            forward_file(tg, p, token, state)
        except Exception as e:
            print(f"error on {p.name}: {e}", file=sys.stderr)


def pull_from_server(tg: Telegram, list_url: str, token: str, state: dict) -> None:
    import json
    r = requests.get(list_url, headers={"Authorization": f"Bearer {token}"}, timeout=60)
    r.raise_for_status()
    items = r.json().get("backups", [])
    base = list_url.rsplit("/api/", 1)[0]
    tmpdir = Path(os.environ.get("TMPDIR", "/tmp"))
    for it in items:
        name, dev, size = it["file"], it["device_id"], it["size"]
        key = f"{dev}/{name}"
        if state.get(key) == size:
            continue
        url = f"{base}/api/download/{dev}/{name}"
        d = requests.get(url, headers={"Authorization": f"Bearer {token}"}, timeout=600)
        d.raise_for_status()
        local = tmpdir / f"srv_{name}"
        local.write_bytes(d.content)
        try:
            forward_file(tg, local, token, state)
        finally:
            local.unlink(missing_ok=True)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--dir", default=str(Path(__file__).with_name("backups")),
                    help="server backup dir to watch (default ./backups next to server)")
    ap.add_argument("--server", help="instead of local dir, pull from e.g. http://HOST:PORT/api/list")
    ap.add_argument("--watch", action="store_true", help="keep polling every interval")
    ap.add_argument("--interval", type=int, default=60, help="watch poll seconds (default 60)")
    args = ap.parse_args()

    token = os.environ.get("WA_BACKUP_TOKEN")
    bot = os.environ.get("TG_BOT_TOKEN")
    chat = os.environ.get("TG_CHAT_ID")
    if not (token and bot and chat):
        raise SystemExit("set WA_BACKUP_TOKEN, TG_BOT_TOKEN, TG_CHAT_ID first")

    tg = Telegram(bot, chat)
    state = load_state()

    def run_once() -> None:
        if args.server:
            pull_from_server(tg, args.server, token, state)
        else:
            scan_dir(tg, Path(args.dir), token, state)

    if args.watch:
        tg.send_message("WA-backup Telegram forwarder চালু ✅")
        while True:
            try:
                run_once()
            except Exception as e:
                print(f"poll error: {e}", file=sys.stderr)
            time.sleep(args.interval)
    else:
        run_once()
        print("done")


if __name__ == "__main__":
    main()
