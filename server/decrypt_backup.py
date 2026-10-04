#!/usr/bin/env python3
"""
Decrypt a backup downloaded from the personal WA-backup server.

The app uploads: [6B magic 'WAB\\x01\\x00\\x00'][16B IV][AES-256-CBC(PKCS7) ciphertext]
with the key = PBKDF2-HMAC-SHA256(token, salt="wa-backup-personal-v1", 120000, 32B).

Usage:
    python3 decrypt_backup.py input.bin output_file --token "$WA_BACKUP_TOKEN"

If the decrypted file is a WhatsApp .crypt## backup, you still need the WhatsApp
backup passphrase (the one you set inside WhatsApp's Chat backup settings) plus a
tool like agent/seabass crypt14 parser to read messages. Double protection by design.
"""

import argparse
import hashlib
import sys
from pathlib import Path

try:
    from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
except ImportError:
    sys.exit("pip install cryptography")

MAGIC = b"WAB\x01\x00\x00"
SALT = b"wa-backup-personal-v1"
ITERATIONS = 120_000


def derive_key(token: str) -> bytes:
    return hashlib.pbkdf2_hmac("sha256", token.encode(), SALT, ITERATIONS, dklen=32)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("input")
    ap.add_argument("output")
    ap.add_argument("--token", required=True, help="same auth token as in the app")
    args = ap.parse_args()

    data = Path(args.input).read_bytes()
    if not data.startswith(MAGIC):
        sys.exit("Not a WA-backup encrypted file (bad magic header)")
    body = data[len(MAGIC):]
    iv, ct = body[:16], body[16:]
    if len(ct) == 0 or len(ct) % 16 != 0:
        sys.exit("Ciphertext looks truncated")

    key = derive_key(args.token)
    dec = Cipher(algorithms.AES(key), modes.CBC(iv)).decryptor()
    padded = dec.update(ct) + dec.finalize()

    # strip PKCS7 padding
    pad = padded[-1]
    if pad < 1 or pad > 16 or padded[-pad:] != bytes([pad]) * pad:
        sys.exit("Bad padding — wrong token?")
    Path(args.output).write_bytes(padded[:-pad])
    print(f"Wrote {args.output} ({len(padded) - pad} bytes)")


if __name__ == "__main__":
    main()
