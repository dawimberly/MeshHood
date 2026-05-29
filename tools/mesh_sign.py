"""Ed25519 signing identity (matches SignKeys.kt on the phone).

Used to sign "kudos" (Good Neighbor endorsements) so reputation can't be faked.
Raw 32-byte verify keys are exchanged over the mesh (base64), byte-compatible
with the phone via the standard Ed25519 raw encoding.
"""

import base64

from cryptography.hazmat.primitives.asymmetric.ed25519 import (
    Ed25519PrivateKey,
    Ed25519PublicKey,
)
from cryptography.hazmat.primitives.serialization import Encoding, PublicFormat

_priv = Ed25519PrivateKey.generate()

# name -> peer's base64 verify key, learned from key handshakes.
peer_verify_keys: dict[str, str] = {}


def my_verify_b64() -> str:
    raw = _priv.public_key().public_bytes(Encoding.Raw, PublicFormat.Raw)
    return base64.b64encode(raw).decode("ascii")


def sign(message: str) -> str:
    return base64.b64encode(_priv.sign(message.encode("utf-8"))).decode("ascii")


def verify(message: str, signature_b64: str, peer_verify_b64: str) -> bool:
    try:
        pub = Ed25519PublicKey.from_public_bytes(base64.b64decode(peer_verify_b64))
        pub.verify(base64.b64decode(signature_b64), message.encode("utf-8"))
        return True
    except Exception:
        return False


def kudos_payload(giver: str, helper: str, ts: int) -> str:
    return f"kudos|{giver}|{helper}|{ts}"


class Signer:
    """A standalone Ed25519 identity (for simulating other neighbors)."""

    def __init__(self) -> None:
        self._priv = Ed25519PrivateKey.generate()

    def verify_b64(self) -> str:
        raw = self._priv.public_key().public_bytes(Encoding.Raw, PublicFormat.Raw)
        return base64.b64encode(raw).decode("ascii")

    def sign(self, message: str) -> str:
        return base64.b64encode(self._priv.sign(message.encode("utf-8"))).decode("ascii")
