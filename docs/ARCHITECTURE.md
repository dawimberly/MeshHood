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
| `broadcast` | Public neighborhood chat |
| `dm` | Direct message (plain or X25519-sealed) |
| `key` | X25519 + Ed25519 public key handshake |
| `kudos` | Signed reputation credit |
| `profile` | Signed skills/shares/certs |
| `status` / `vouch` | Capacity claims and neighbor attestation |
| `groupcreate` / `groupjoin` / `groupmsg` / … | Community overlay |
| `crew` / `crewjoin` | Help-call coordination |

## Groups overlay

Groups are **optional**. The mesh works with zero admins. Founders become admins; admins can verify credentials, pin announcements, and promote co-admins — they cannot delete or censor arbitrary speech.

## Python tools

The `tools/` directory implements the same crypto and envelope format as the phone, enabling PC ↔ device integration tests over BLE without a second phone.
