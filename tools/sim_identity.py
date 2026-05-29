"""Persistent simulated-neighbor crypto identity (survives PC script restarts)."""

from __future__ import annotations

import base64
import json
from pathlib import Path

from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from cryptography.hazmat.primitives.asymmetric.x25519 import X25519PrivateKey
from cryptography.hazmat.primitives.serialization import Encoding, NoEncryption, PrivateFormat, PublicFormat

_DIR = Path(__file__).parent / "sim_identities"


def _b64(raw: bytes) -> str:
    return base64.b64encode(raw).decode("ascii")


def _from_b64(raw_b64: str) -> bytes:
    return base64.b64decode(raw_b64)


def load_or_create(name: str) -> tuple[X25519PrivateKey, Ed25519PrivateKey]:
    """Return stable X25519 + Ed25519 keys for a simulated neighbor name."""
    _DIR.mkdir(exist_ok=True)
    path = _DIR / f"{name.lower()}.json"
    if path.exists():
        data = json.loads(path.read_text(encoding="utf-8"))
        x = X25519PrivateKey.from_private_bytes(_from_b64(data["x25519"]))
        ed = Ed25519PrivateKey.from_private_bytes(_from_b64(data["ed25519"]))
        return x, ed

    x = X25519PrivateKey.generate()
    ed = Ed25519PrivateKey.generate()
    path.write_text(
        json.dumps(
            {
                "name": name,
                "x25519": _b64(x.private_bytes(Encoding.Raw, PrivateFormat.Raw, NoEncryption())),
                "ed25519": _b64(ed.private_bytes(Encoding.Raw, PrivateFormat.Raw, NoEncryption())),
            },
            indent=2,
        ),
        encoding="utf-8",
    )
    return x, ed


def xpub_b64(x: X25519PrivateKey) -> str:
    return _b64(x.public_key().public_bytes(Encoding.Raw, PublicFormat.Raw))


def vpub_b64(ed: Ed25519PrivateKey) -> str:
    return _b64(ed.public_key().public_bytes(Encoding.Raw, PublicFormat.Raw))
