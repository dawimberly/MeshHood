"""MeshHood end-to-end encryption (matches Crypto.kt on the phone).

AES-256-GCM with a key derived from a shared neighborhood passphrase
(SHA-256 of the passphrase). Wire format:

    [ 12-byte random nonce ][ ciphertext + 16-byte auth tag ]

Requires:  python -m pip install cryptography
"""

import hashlib
import os

from cryptography.hazmat.primitives.ciphers.aead import AESGCM

# Must match NEIGHBORHOOD_PASSPHRASE in Crypto.kt
NEIGHBORHOOD_PASSPHRASE = "meshhood-demo-key"

NONCE_LENGTH = 12

_KEY = hashlib.sha256(NEIGHBORHOOD_PASSPHRASE.encode("utf-8")).digest()
_AES = AESGCM(_KEY)


def encrypt(plaintext: str) -> bytes:
    return encrypt_with_key(_KEY, plaintext)


def decrypt(data: bytes) -> str:
    """Returns decrypted text, or a placeholder if it can't be decrypted."""
    try:
        if len(data) <= NONCE_LENGTH:
            return "[unencrypted or invalid]"
        return decrypt_with_key(_KEY, data)
    except Exception:
        return "[could not decrypt — wrong key]"


def encrypt_with_key(key: bytes, plaintext: str) -> bytes:
    """AES-256-GCM with an arbitrary 32-byte key (used for per-pair DM keys)."""
    aes = AESGCM(key)
    nonce = os.urandom(NONCE_LENGTH)
    return nonce + aes.encrypt(nonce, plaintext.encode("utf-8"), None)


def decrypt_with_key(key: bytes, data: bytes) -> str:
    aes = AESGCM(key)
    nonce = data[:NONCE_LENGTH]
    return aes.decrypt(nonce, data[NONCE_LENGTH:], None).decode("utf-8")
