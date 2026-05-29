"""MeshHood message envelope (matches MeshService.kt).

Every envelope carries a random `id` and a `ttl` (hop counter) so messages can
be relayed multi-hop across the mesh without looping forever.

Types:
  broadcast : {v,type,id,ttl,from,text,ts}            (readable by all neighbors)
  key       : {v,type,id,ttl,from,pub,ts}             (public-key handshake)
  dm (x25519): {v,type,id,ttl,enc:"x25519",body,ts}   (sealed; no from/to outside)
  dm (plain) : {v,type,id,ttl,from,to,text,ts}        (fallback when no key yet)

For sealed DMs the sender/recipient/text live ENCRYPTED inside `body`, so relay
nodes in the middle learn nothing but "a sealed blob with this id, relay it".
"""

import json
import os
import time

PC_NAME = "PC"
TTL_DEFAULT = 6


def new_id() -> str:
    return os.urandom(8).hex()


def _base(msg_type: str, ttl: int) -> dict:
    return {
        "v": 1,
        "type": msg_type,
        "id": new_id(),
        "ttl": ttl,
        "ts": int(time.time() * 1000),
    }


def build(text: str, to: str = "*", sender: str = PC_NAME, ttl: int = TTL_DEFAULT) -> str:
    """Broadcast (to='*') or cleartext direct message."""
    if to == "*":
        obj = _base("broadcast", ttl)
        obj["from"] = sender
        obj["text"] = text
    else:
        obj = _base("dm", ttl)
        obj["from"] = sender
        obj["to"] = to
        obj["text"] = text
    return json.dumps(obj)


def build_broadcast(text: str, sender: str = PC_NAME, ttl: int = TTL_DEFAULT) -> str:
    return build(text, to="*", sender=sender, ttl=ttl)


def build_sealed_dm(body_b64: str, ttl: int = TTL_DEFAULT) -> str:
    obj = _base("dm", ttl)
    obj["enc"] = "x25519"
    obj["body"] = body_b64
    return json.dumps(obj)


def build_plain_dm(text: str, to: str, sender: str = PC_NAME, ttl: int = TTL_DEFAULT) -> str:
    obj = _base("dm", ttl)
    obj["from"] = sender
    obj["to"] = to
    obj["text"] = text
    return json.dumps(obj)


def build_key_announcement(pub: str, sender: str = PC_NAME, ttl: int = TTL_DEFAULT,
                           spub: str | None = None) -> str:
    obj = _base("key", ttl)
    obj["from"] = sender
    obj["pub"] = pub
    if spub is not None:
        obj["spub"] = spub
    return json.dumps(obj)


def build_kudos(giver: str, helper: str, ts: int, sig: str, ttl: int = TTL_DEFAULT) -> str:
    obj = _base("kudos", ttl)
    obj["from"] = giver
    obj["to"] = helper
    obj["kts"] = ts
    obj["sig"] = sig
    return json.dumps(obj)


def build_status(sender: str, cap: str, ts: int, sig: str, ttl: int = TTL_DEFAULT) -> str:
    obj = _base("status", ttl)
    obj["from"] = sender
    obj["cap"] = cap
    obj["kts"] = ts
    obj["sig"] = sig
    return json.dumps(obj)


def build_vouch(voucher: str, subject: str, ts: int, sig: str, ttl: int = TTL_DEFAULT) -> str:
    obj = _base("vouch", ttl)
    obj["from"] = voucher
    obj["to"] = subject
    obj["kts"] = ts
    obj["sig"] = sig
    return json.dumps(obj)


def build_emergency(sender: str, text: str, ice: dict | None = None,
                    ttl: int = TTL_DEFAULT) -> str:
    obj = _base("broadcast", ttl)
    obj["from"] = sender
    obj["text"] = text
    if ice:
        obj["ice"] = ice
    return json.dumps(obj)


def build_profile(sender: str, ts: int, skills: list, shares: list, certs: list,
                  sig: str, ttl: int = TTL_DEFAULT) -> str:
    obj = _base("profile", ttl)
    obj["from"] = sender
    obj["kts"] = ts
    obj["skills"] = skills
    obj["shares"] = shares
    obj["certs"] = certs
    obj["sig"] = sig
    return json.dumps(obj)


def build_crew(sender: str, task: str, ttl: int = TTL_DEFAULT) -> str:
    obj = _base("crew", ttl)
    obj["from"] = sender
    obj["text"] = task
    return json.dumps(obj)


def build_crewjoin(sender: str, task: str, ttl: int = TTL_DEFAULT) -> str:
    obj = _base("crewjoin", ttl)
    obj["from"] = sender
    obj["text"] = task
    return json.dumps(obj)


def build_groupcreate(gid: str, name: str, founder: str, ts: int, sig: str,
                      ttl: int = TTL_DEFAULT) -> str:
    obj = _base("groupcreate", ttl)
    obj["gid"] = gid
    obj["name"] = name
    obj["from"] = founder
    obj["kts"] = ts
    obj["sig"] = sig
    return json.dumps(obj)


def build_groupjoin(gid: str, member: str, ts: int, sig: str,
                    ttl: int = TTL_DEFAULT) -> str:
    obj = _base("groupjoin", ttl)
    obj["gid"] = gid
    obj["from"] = member
    obj["kts"] = ts
    obj["sig"] = sig
    return json.dumps(obj)


def build_groupadmin(gid: str, admin: str, target: str, ts: int, sig: str,
                     ttl: int = TTL_DEFAULT) -> str:
    obj = _base("groupadmin", ttl)
    obj["gid"] = gid
    obj["from"] = admin
    obj["to"] = target
    obj["kts"] = ts
    obj["sig"] = sig
    return json.dumps(obj)


def build_grouppin(gid: str, admin: str, text: str, ts: int, sig: str,
                   ttl: int = TTL_DEFAULT) -> str:
    obj = _base("grouppin", ttl)
    obj["gid"] = gid
    obj["from"] = admin
    obj["text"] = text
    obj["kts"] = ts
    obj["sig"] = sig
    return json.dumps(obj)


def build_groupverify(gid: str, admin: str, subject: str, cert: str, ts: int, sig: str,
                      ttl: int = TTL_DEFAULT) -> str:
    obj = _base("groupverify", ttl)
    obj["gid"] = gid
    obj["from"] = admin
    obj["to"] = subject
    obj["cert"] = cert
    obj["kts"] = ts
    obj["sig"] = sig
    return json.dumps(obj)


def build_groupmsg(gid: str, sender: str, text: str, ttl: int = TTL_DEFAULT) -> str:
    obj = _base("groupmsg", ttl)
    obj["gid"] = gid
    obj["from"] = sender
    obj["text"] = text
    return json.dumps(obj)


def parse(raw: str) -> dict:
    try:
        obj = json.loads(raw)
    except Exception:
        return {"type": "broadcast", "id": "", "ttl": 0, "from": "Neighbor",
                "to": "*", "text": raw, "enc": "", "pub": "", "body": ""}
    return {
        "type": obj.get("type", "broadcast"),
        "id": obj.get("id", ""),
        "ttl": obj.get("ttl", 0),
        "from": obj.get("from", "Neighbor"),
        "to": obj.get("to", "*"),
        "text": obj.get("text", ""),
        "enc": obj.get("enc", ""),
        "pub": obj.get("pub", ""),
        "spub": obj.get("spub", ""),
        "cap": obj.get("cap", ""),
        "skills": obj.get("skills", []),
        "shares": obj.get("shares", []),
        "certs": obj.get("certs", []),
        "ice": obj.get("ice", None),
        "body": obj.get("body", ""),
        "kts": obj.get("kts", 0),
        "sig": obj.get("sig", ""),
        "gid": obj.get("gid", ""),
        "cert": obj.get("cert", ""),
    }
