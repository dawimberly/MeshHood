# Architecture

## Overview

MeshHood is a single Android app with a foreground service that runs three parallel transports. All payloads share one JSON message protocol and one encryption layer.

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│  BLE GATT   │     │ WiFi Direct │     │  LAN/mDNS   │
└──────┬──────┘     └──────┬──────┘     └──────┬──────┘
       │                   │                   │
       └───────────────────┼───────────────────┘
                           ▼
                  ┌─────────────────┐
                  │   MeshService   │
                  │  decrypt/parse  │
                  │  relay (TTL)    │
                  │  persistence    │
                  └────────┬────────┘
                           │
         ┌─────────────────┼─────────────────┐
         ▼                 ▼                 ▼
   MainActivity      Coordinator        SignKeys
   (feed UI)      (needs/offers)     (Ed25519)
```

## Message flow

1. Outgoing JSON envelope → `Crypto.encrypt()` → fan-out on all active transports.
2. Incoming bytes → decrypt → dedupe by `id` → handle by `type` → optional relay if `ttl > 0`.
3. Direct messages use an inner JSON blob encrypted with a per-pair X25519 key; relays forward without opening.

## Message types

| Type | Purpose |
|------|---------|
| `broadcast` | Public Area message (`channel` + optional `geo` snapshot) |
| `dm` | Direct message (plain or X25519-sealed) |
| `key` | X25519 + Ed25519 public key handshake |
| `kudos` | Signed reputation credit |
| `profile` | Signed skills/shares/certs |
| `photothumb` | Signed profile photo thumbnail (SHA-256 hash) |
| `photovouch` | Signed neighbor attestation for a profile photo |
| `status` / `vouch` | Capacity claims and neighbor attestation |
| `groupcreate` / `groupjoin` / `groupmsg` / … | Community overlay |
| `crew` / `crewjoin` | Help-call coordination |

## Area feed vs comms channel

Geography and messaging are **separate layers**:

| Layer | Role |
|-------|------|
| **Geo** (`MeshZone`, `GeoLocator`, `ZoneContext`) | Profile anchor (state, nation) + rolling GPS ZIP; drives default feed and local sort order |
| **Comms** (`MessageChannel`, envelope `channel`) | Viewer hint on each message — mesh relays do not filter by ZIP |
| **UI** | **Area ▼** = public scopes; **Chats** = `dm:*` only |

Broadcast envelopes include:

- `channel` — e.g. `zone:postal:87110`, `everyone`, or a group id (legacy field: `zoneScope`)
- `geo` — optional `{ lat, lon, postal, ts }` sender snapshot at send time

## Groups overlay

Groups are **optional**. The mesh works with zero admins. Founders become admins; admins can verify credentials, pin announcements, and promote co-admins — they cannot delete or censor arbitrary speech.

## Python tools

The `tools/` directory implements the same crypto and envelope format as the phone, enabling PC ↔ device integration tests over BLE without a second phone.
