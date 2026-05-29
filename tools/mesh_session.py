"""High-level MeshHood session helpers shared by the PC tools.

Layers:
  - Transport: every message is wrapped with the shared neighborhood key
    (mesh_crypto) so only the neighborhood can see traffic.
  - Private DM: from/to/text are sealed inside `body` with a per-pair X25519
    key (mesh_keys). Relay nodes in the middle see only an opaque blob + id +
    ttl — not the sender, recipient, or text.

Use `build_*` to create wire bytes to write to the phone, and `handle` to turn
incoming wire bytes into a dict (storing peer keys, opening sealed DMs, and
de-duplicating relayed echoes by message id).
"""

import base64
import json
import time

import time

import mesh_crypto
import mesh_keys
import mesh_proto
import mesh_sign

_seen_ids: set[str] = set()


def build_key_msg() -> bytes:
    env = mesh_proto.build_key_announcement(
        mesh_keys.my_public_b64(), spub=mesh_sign.my_verify_b64()
    )
    return mesh_crypto.encrypt(env)


def build_kudos(helper: str, giver: str = mesh_proto.PC_NAME) -> bytes:
    ts = int(time.time() * 1000)
    sig = mesh_sign.sign(mesh_sign.kudos_payload(giver, helper, ts))
    return mesh_crypto.encrypt(mesh_proto.build_kudos(giver, helper, ts, sig))


def build_broadcast(text: str) -> bytes:
    return mesh_crypto.encrypt(mesh_proto.build_broadcast(text))


def build_status(cap: str, sender: str = mesh_proto.PC_NAME,
                 signer: "mesh_sign.Signer | None" = None) -> bytes:
    ts = int(time.time() * 1000)
    payload = f"status|{sender}|{cap}|{ts}"
    sig = signer.sign(payload) if signer else mesh_sign.sign(payload)
    return mesh_crypto.encrypt(mesh_proto.build_status(sender, cap, ts, sig))


def build_vouch(subject: str, voucher: str = mesh_proto.PC_NAME,
                signer: "mesh_sign.Signer | None" = None) -> bytes:
    ts = int(time.time() * 1000)
    payload = f"vouch|{voucher}|{subject}|{ts}"
    sig = signer.sign(payload) if signer else mesh_sign.sign(payload)
    return mesh_crypto.encrypt(mesh_proto.build_vouch(voucher, subject, ts, sig))


def build_profile(skills: list, shares: list, certs: list,
                  sender: str = mesh_proto.PC_NAME,
                  signer: "mesh_sign.Signer | None" = None) -> bytes:
    ts = int(time.time() * 1000)
    payload = (f"profile|{sender}|{ts}|" + ",".join(skills) + "|"
               + ",".join(shares) + "|" + ",".join(certs))
    sig = signer.sign(payload) if signer else mesh_sign.sign(payload)
    return mesh_crypto.encrypt(
        mesh_proto.build_profile(sender, ts, skills, shares, certs, sig)
    )


def build_emergency(text: str, ice: dict | None = None,
                    sender: str = mesh_proto.PC_NAME) -> bytes:
    return mesh_crypto.encrypt(mesh_proto.build_emergency(sender, text, ice))


def build_crew(task: str, sender: str = mesh_proto.PC_NAME) -> bytes:
    return mesh_crypto.encrypt(mesh_proto.build_crew(sender, task))


def build_crewjoin(task: str, sender: str = mesh_proto.PC_NAME) -> bytes:
    return mesh_crypto.encrypt(mesh_proto.build_crewjoin(sender, task))


def group_create_payload(gid: str, name: str, founder: str, ts: int) -> str:
    return f"groupcreate|{gid}|{name}|{founder}|{ts}"


def group_join_payload(gid: str, member: str, ts: int) -> str:
    return f"groupjoin|{gid}|{member}|{ts}"


def group_pin_payload(gid: str, admin: str, text: str, ts: int) -> str:
    return f"grouppin|{gid}|{admin}|{text}|{ts}"


def group_verify_payload(gid: str, admin: str, subject: str, cert: str, ts: int) -> str:
    return f"groupverify|{gid}|{admin}|{subject}|{cert}|{ts}"


def group_admin_payload(gid: str, admin: str, target: str, ts: int) -> str:
    return f"groupadmin|{gid}|{admin}|{target}|{ts}"


def build_groupcreate(gid: str, name: str, founder: str = mesh_proto.PC_NAME,
                      signer: "mesh_sign.Signer | None" = None) -> bytes:
    ts = int(time.time() * 1000)
    payload = group_create_payload(gid, name, founder, ts)
    sig = signer.sign(payload) if signer else mesh_sign.sign(payload)
    return mesh_crypto.encrypt(mesh_proto.build_groupcreate(gid, name, founder, ts, sig))


def build_groupjoin(gid: str, member: str = mesh_proto.PC_NAME,
                    signer: "mesh_sign.Signer | None" = None) -> bytes:
    ts = int(time.time() * 1000)
    payload = group_join_payload(gid, member, ts)
    sig = signer.sign(payload) if signer else mesh_sign.sign(payload)
    return mesh_crypto.encrypt(mesh_proto.build_groupjoin(gid, member, ts, sig))


def build_grouppin(gid: str, text: str, admin: str = mesh_proto.PC_NAME,
                   signer: "mesh_sign.Signer | None" = None) -> bytes:
    ts = int(time.time() * 1000)
    payload = group_pin_payload(gid, admin, text, ts)
    sig = signer.sign(payload) if signer else mesh_sign.sign(payload)
    return mesh_crypto.encrypt(mesh_proto.build_grouppin(gid, admin, text, ts, sig))


def build_groupverify(gid: str, subject: str, cert: str, admin: str = mesh_proto.PC_NAME,
                      signer: "mesh_sign.Signer | None" = None) -> bytes:
    ts = int(time.time() * 1000)
    payload = group_verify_payload(gid, admin, subject, cert, ts)
    sig = signer.sign(payload) if signer else mesh_sign.sign(payload)
    return mesh_crypto.encrypt(
        mesh_proto.build_groupverify(gid, admin, subject, cert, ts, sig)
    )


def build_dm(text: str, to: str) -> tuple[bytes, bool]:
    """Returns (wire_bytes, is_private). Private if we know the peer's key."""
    pub = mesh_keys.peer_keys.get(to)
    if pub:
        key = mesh_keys.shared_key(pub)
        inner = json.dumps({
            "from": mesh_proto.PC_NAME,
            "to": to,
            "text": text,
            "ts": int(time.time() * 1000),
        })
        body = base64.b64encode(mesh_crypto.encrypt_with_key(key, inner)).decode("ascii")
        return mesh_crypto.encrypt(mesh_proto.build_sealed_dm(body)), True
    # No key yet for this peer — falls back to a non-private DM.
    return mesh_crypto.encrypt(mesh_proto.build_plain_dm(text, to)), False


def have_key_for(name: str) -> bool:
    return name in mesh_keys.peer_keys


def _open_sealed(body_b64: str) -> dict | None:
    """Try every known peer key to open a sealed DM body."""
    try:
        data = base64.b64decode(body_b64)
    except Exception:
        return None
    for name, pub in mesh_keys.peer_keys.items():
        try:
            key = mesh_keys.shared_key(pub)
            plain = mesh_crypto.decrypt_with_key(key, data)
        except Exception:
            continue
        try:
            inner = json.loads(plain)
            return {"from": inner.get("from", name), "to": inner.get("to", ""),
                    "text": inner.get("text", plain)}
        except Exception:
            return {"from": name, "to": "", "text": plain}
    return None


def handle(data: bytes) -> dict:
    """Decrypt + parse an incoming wire payload.

    Stores peer keys, opens sealed DMs addressed to us, and drops duplicate
    relayed echoes (by id). Returns a dict with at least: type, from, text.
    """
    raw = mesh_crypto.decrypt(bytes(data))
    m = mesh_proto.parse(raw)

    mid = m.get("id", "")
    if mid:
        if mid in _seen_ids:
            m["duplicate"] = True
            return m
        _seen_ids.add(mid)

    if m["type"] == "key":
        if m["pub"] and m["from"]:
            mesh_keys.peer_keys[m["from"]] = m["pub"]
        if m.get("spub") and m["from"]:
            mesh_sign.peer_verify_keys[m["from"]] = m["spub"]
        return m

    if m["type"] == "dm" and m.get("enc") == "x25519":
        opened = _open_sealed(m.get("body", ""))
        if opened is not None:
            m["from"] = opened["from"]
            m["to"] = opened["to"]
            m["text"] = opened["text"]
            m["opened"] = True
        else:
            m["text"] = "[sealed DM — not for us / no key]"
            m["opened"] = False

    return m


def describe(m: dict) -> str:
    if m.get("type") == "key":
        return f"[handshake] received public key from {m['from']}"
    if m.get("type") == "dm":
        if m.get("enc") == "x25519":
            lock = " (private)" if m.get("opened") else " (sealed, not for us)"
            return f"(DM{lock}) {m.get('from','?')}: {m['text']}"
        return f"(DM) {m['from']} -> {m['to']}: {m['text']}"
    return f"{m['from']}: {m['text']}"
