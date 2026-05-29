"""Per-device X25519 identity keys (matches DeviceKeys.kt on the phone).

Each run generates a fresh X25519 key pair. Public keys are exchanged over the
mesh as raw 32-byte values (base64). The per-pair AES key for a private direct
message is:

    shared = ECDH(myPrivate, peerPublic)
    dmKey  = SHA-256(shared)

This is byte-compatible with the phone: both sides use raw 32-byte public keys
and SHA-256 of the raw ECDH secret.

Requires:  python -m pip install cryptography
"""

import base64
import hashlib

from cryptography.hazmat.primitives.asymmetric.x25519 import (
    X25519PrivateKey,
    X25519PublicKey,
)
from cryptography.hazmat.primitives.serialization import Encoding, PublicFormat

_priv = X25519PrivateKey.generate()

# name -> peer's base64 public key, populated as we receive "key" handshakes.
peer_keys: dict[str, str] = {}


def my_public_b64() -> str:
    raw = _priv.public_key().public_bytes(Encoding.Raw, PublicFormat.Raw)
    return base64.b64encode(raw).decode("ascii")


def shared_key(peer_pub_b64: str) -> bytes:
    raw = base64.b64decode(peer_pub_b64)
    peer = X25519PublicKey.from_public_bytes(raw)
    shared = _priv.exchange(peer)
    return hashlib.sha256(shared).digest()
