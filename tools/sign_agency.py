#!/usr/bin/env python3
"""Sign a MeshHood agency envelope for dev/testing. Private key stays off-device."""

import argparse
import base64
import json
import secrets
import sys
import time

try:
    from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
except ImportError:
    print("pip install cryptography", file=sys.stderr)
    sys.exit(1)

DEV_PRIV_B64 = "/qYKxWEA2+QNx0m+s+lsQODpEJ8+Vd0XUmPYIcFxsTs="


def payload(agency_id: str, ts: int, text: str) -> bytes:
    return f"agency|{agency_id}|{ts}|{text}".encode()


def main() -> None:
    p = argparse.ArgumentParser(description="Sign a MeshHood agency mesh envelope")
    p.add_argument("text", help="Public alert body")
    p.add_argument("--agency-id", default="demo-county-em")
    p.add_argument("--priv", default=DEV_PRIV_B64, help="Base64 Ed25519 private key (32 bytes)")
    p.add_argument("--ttl", type=int, default=10)
    args = p.parse_args()

    priv = Ed25519PrivateKey.from_private_bytes(base64.b64decode(args.priv))
    ts = int(time.time() * 1000)
    sig = base64.b64encode(priv.sign(payload(args.agency_id, ts, args.text))).decode()

    envelope = {
        "v": 1,
        "type": "agency",
        "id": secrets.token_hex(8),
        "ttl": args.ttl,
        "ts": ts,
        "agencyId": args.agency_id,
        "agencySig": sig,
        "text": args.text,
        "routeClass": "agency-official",
        "channel": "everyone",
    }
    print(json.dumps(envelope, indent=2))


if __name__ == "__main__":
    main()
