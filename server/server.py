#!/usr/bin/env python3
"""
Personal WhatsApp Backup Server
===============================

Receives AES-256-CBC-encrypted backup files from the WA Backup Android app,
verifies the bearer token + SHA-256 checksum, and stores them under
./backups/<device-id>/. The server NEVER sees plaintext chat data.

Endpoints
---------
GET  /api/health    -> {"ok": true}                  (token required)
POST /api/upload    -> stores body as an encrypted blob
                      headers: X-File-Name, X-File-Sha256, X-Encrypted
GET  /api/list      -> lists stored backups          (token required)

Run
---
    export WA_BACKUP_TOKEN="$(python3 -c 'import secrets;print(secrets.token_hex(32))')"
    python3 server.py --port 8443

Put it behind TLS (nginx/caddy reverse proxy, or --tls with a cert) — the app
refuses plain http:// by default.

Stdlib only; no pip packages needed.
"""

import argparse
import hashlib
import hmac
import json
import os
import re
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlparse

BACKUP_DIR = Path(os.environ.get("WA_BACKUP_DIR", "backups"))
MAX_UPLOAD_BYTES = int(os.environ.get("WA_MAX_UPLOAD_MB", "2048")) * 1024 * 1024
TOKEN = os.environ.get("WA_BACKUP_TOKEN", "")

SAFE_NAME_RE = re.compile(r"^[A-Za-z0-9._\- ]{1,120}$")


def constant_time_eq(a: str, b: str) -> bool:
    return hmac.compare_digest(a.encode(), b.encode())


class Handler(BaseHTTPRequestHandler):
    server_version = "WABackupPersonal/1.0"

    # ---- helpers ------------------------------------------------------------

    def _send(self, code: int, payload: dict) -> None:
        body = json.dumps(payload).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _authorized(self) -> bool:
        if not TOKEN:
            self._send(500, {"ok": False, "error": "server has no WA_BACKUP_TOKEN configured"})
            return False
        auth = self.headers.get("Authorization", "")
        if not auth.startswith("Bearer "):
            self._send(401, {"ok": False, "error": "missing bearer token"})
            return False
        supplied = auth[len("Bearer "):].strip()
        if not constant_time_eq(supplied, TOKEN):
            self._send(403, {"ok": False, "error": "bad token"})
            return False
        return True

    def _device_id(self) -> str:
        """Device id = first 16 hex of SHA-256 of the token (stable per-user bucket)."""
        return hashlib.sha256(TOKEN.encode()).hexdigest()[:16]

    def log_message(self, fmt, *args):  # quieter logs, never log bodies
        print(f"[{time.strftime('%Y-%m-%d %H:%M:%S')}] {self.address_string()} {fmt % args}")

    # ---- routes -------------------------------------------------------------

    def do_GET(self):
        path = urlparse(self.path).path
        if path == "/api/health":
            if self._authorized():
                self._send(200, {"ok": True, "service": "wa-backup-personal"})
            return
        if path == "/api/list":
            if self._authorized():
                dev_dir = BACKUP_DIR / self._device_id()
                items = []
                if dev_dir.is_dir():
                    for f in sorted(dev_dir.iterdir()):
                        if not f.is_file() or f.name.endswith(".part"):
                            continue
                        st = f.stat()
                        items.append({
                            "file": f.name,
                            "device_id": self._device_id(),
                            "size": st.st_size,
                            "modified": int(st.st_mtime),
                        })
                self._send(200, {"ok": True, "backups": items})
            return
        if path.startswith("/api/download/"):
            self._handle_download(path)
            return
        self._send(404, {"ok": False, "error": "not found"})

    def _handle_download(self, path):
        """GET /api/download/<device-id>/<filename> -> stream the stored blob."""
        if not self._authorized():
            return
        parts = path.split("/")  # ['', 'api', 'download', device, name...]
        if len(parts) < 5:
            self._send(400, {"ok": False, "error": "bad download path"})
            return
        device, fname = parts[3], "/".join(parts[4:])
        # Only allow this user's own bucket; no traversal.
        if device != self._device_id() or not SAFE_NAME_RE.match(fname):
            self._send(404, {"ok": False, "error": "not found"})
            return
        f = (BACKUP_DIR / device / fname).resolve()
        root = BACKUP_DIR.resolve()
        if not str(f).startswith(str(root / device)) or not f.is_file():
            self._send(404, {"ok": False, "error": "not found"})
            return
        self.send_response(200)
        self.send_header("Content-Type", "application/octet-stream")
        self.send_header("Content-Length", str(f.stat().st_size))
        self.end_headers()
        with open(f, "rb") as fh:
            while True:
                chunk = fh.read(256 * 1024)
                if not chunk:
                    break
                try:
                    self.wfile.write(chunk)
                except (BrokenPipeError, ConnectionResetError):
                    return

    def do_POST(self):
        path = urlparse(self.path).path
        if path != "/api/upload":
            self._send(404, {"ok": False, "error": "not found"})
            return
        if not self._authorized():
            return

        fname = self.headers.get("X-File-Name", "")
        sha_claim = (self.headers.get("X-File-Sha256", "") or "").lower()
        encrypted = self.headers.get("X-Encrypted", "") == "true"

        if not SAFE_NAME_RE.match(fname) or fname in (".", ".."):
            self._send(400, {"ok": False, "error": "bad X-File-Name"})
            return
        if not encrypted:
            # Refuse anything the app didn't end-to-end encrypt.
            self._send(400, {"ok": False, "error": "uploads must be encrypted (X-Encrypted: true)"})
            return

        length = int(self.headers.get("Content-Length", "0") or 0)
        if length <= 0:
            self._send(400, {"ok": False, "error": "empty body"})
            return
        if length > MAX_UPLOAD_BYTES:
            self._send(413, {"ok": False, "error": "too large"})
            return

        dev_dir = BACKUP_DIR / self._device_id()
        dev_dir.mkdir(parents=True, exist_ok=True)
        stamp = time.strftime("%Y%m%d-%H%M%S")
        dest = dev_dir / f"{stamp}_{fname}"
        tmp = dest.with_suffix(dest.suffix + ".part")

        remaining = length
        digest = hashlib.sha256()
        try:
            with open(tmp, "wb") as out:
                while remaining > 0:
                    chunk = self.rfile.read(min(remaining, 256 * 1024))
                    if not chunk:
                        break
                    digest.update(chunk)
                    out.write(chunk)
                    remaining -= len(chunk)
            if remaining > 0:
                raise IOError("client disconnected mid-upload")
        except Exception as e:
            tmp.unlink(missing_ok=True)
            self._send(500, {"ok": False, "error": f"write failed: {e}"})
            return

        actual = digest.hexdigest()
        if sha_claim and sha_claim != actual:
            tmp.unlink(missing_ok=True)
            self._send(422, {"ok": False, "error": "sha256 mismatch", "expected": sha_claim,
                             "actual": actual})
            return

        tmp.rename(dest)
        size = dest.stat().st_size
        print(f"stored {dest} ({size} bytes)")
        self._send(200, {"ok": True, "path": str(dest), "size": size, "sha256": actual})


def main() -> None:
    ap = argparse.ArgumentParser(description="Personal WhatsApp backup receiver")
    ap.add_argument("--port", type=int, default=int(os.environ.get("WA_PORT", "8443")))
    ap.add_argument("--bind", default=os.environ.get("WA_BIND", "0.0.0.0"))
    ap.add_argument("--tls-cert", help="PEM cert for native TLS (or put behind nginx/caddy)")
    ap.add_argument("--tls-key", help="PEM key for native TLS")
    args = ap.parse_args()

    if not TOKEN:
        raise SystemExit(
            "ERROR: set WA_BACKUP_TOKEN first, e.g.\n"
            "  export WA_BACKUP_TOKEN=\"$(python3 -c 'import secrets;print(secrets.token_hex(32))')\"\n"
            "and paste the SAME value into the app's Auth token field."
        )

    BACKUP_DIR.mkdir(parents=True, exist_ok=True)
    httpd = ThreadingHTTPServer((args.bind, args.port), Handler)

    if args.tls_cert and args.tls_key:
        import ssl
        ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        ctx.load_cert_chain(args.tls_cert, args.tls_key)
        ctx.minimum_version = ssl.TLSVersion.TLSv1_2
        httpd.socket = ctx.wrap_socket(httpd.socket, server_side=True)
        print(f"WABackup listening https://{args.bind}:{args.port} (dir={BACKUP_DIR.resolve()})")
    else:
        print(f"WABackup listening http://{args.bind}:{args.port} "
              f"(NO TLS! put behind a reverse proxy) dir={BACKUP_DIR.resolve()}")

    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
