# Security

MeshHood is **experimental research software**. It is not certified emergency infrastructure. Do not rely on it as your only way to call for help.

## What we protect

| Layer | Mechanism |
|-------|-----------|
| Transport | AES-256-GCM over a neighborhood shared key (all wire traffic) |
| Direct messages | X25519 ECDH per peer pair; payload sealed inside encrypted envelope |
| Reputation / groups | Ed25519 signatures on kudos, profiles, admin actions |
| Relay privacy | Sealed DMs are blind-forwarded; relays see only opaque blobs |
| ICE card | Stored locally; attached only to **your own** emergency broadcast |

## Known limitations (v1)

- **Neighborhood key** is derived from a demo passphrase baked into the app and Python tools. Anyone with the repo can decrypt neighborhood traffic. Production builds need per-neighborhood provisioning (QR code, HOA invite, etc.).
- **No forward secrecy** on the shared neighborhood key.
- **No authentication** of the mesh itself — a malicious neighbor with the key can inject traffic.
- **BLE/WiFi range** limits who can join your mesh; this is a feature and a boundary.

## Local data

Cryptographic keys and mesh state live in app-private storage (`SharedPreferences`). **Android backup is disabled** (`allowBackup=false`) so keys are not copied to Google cloud backup.

## Reporting

Open a [GitHub Security Advisory](https://github.com/dawimberly/MeshHood/security/advisories/new) or email the maintainer for sensitive reports.
