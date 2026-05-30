# MeshHood UI Design — Nextdoor warmth + mesh infrastructure

> **One sentence:** Nextdoor makes you feel like a neighbor. MeshHood makes you feel safe. The UI needs both feelings at once.

Use the current dark MeshHood prototype as the **foundation**. Extend it — do not rebuild from scratch.

Reference screenshots: MeshHood prototype (dark feed) + Nextdoor (community structure). **No ads. No vanity metrics. No light theme.**

---

## From Nextdoor — keep

| Element | MeshHood implementation |
|---------|-------------------------|
| Neighborhood name at top | **Local · pecos flats ▼** — hyperlocal identity, prominent header |
| Community feed structure | Neighbor posts in scrollable feed (card styling over time) |
| Profile avatar | Top-right; tap for profile / ICE |
| Post / share | Composer: *What's happening in your area?* |

## From Nextdoor — kill

- All advertising and sponsored content
- **For Sale** tab → **Resources** (who has what / who needs what)
- White consumer-social theme (MeshHood dark stays)
- Like/comment counts and vanity engagement metrics

## From MeshHood — keep everything

- Dark theme, teal/cyan accents
- Transport status (BLE, WiFi Direct, WiFi LAN) — visual dot indicators
- Neighbor count + mesh signal strength
- Local area selector + encrypted lock badge
- ICE medical cards in feed — distinct visual treatment
- **Emergency** button — red, permanent, always reachable
- Everyone / broadcast scope controls
- Chats counter + per-message transport context
- Groups, Directory, map, signed trust

---

## Layout (target)

```
┌─────────────────────────────────────┐
│ Local · pecos flats ▼    💬  👤  ⋮  │  ← locality hero (Nextdoor)
│ ● BLE  ● P2P  ● LAN    ▂▄▆█  2 nb   │  ← mesh infrastructure layer
├─────────────────────────────────────┤
│ 👤  What's happening in your area?  │  ← warm composer (Nextdoor tone)
├─────────────────────────────────────┤
│                                     │
│  Feed (neighbor / system / ICE)     │
│                                     │
├─────────────────────────────────────┤
│  Home   Nearby   Resources   Alert  │  ← bottom nav
├─────────────────────────────────────┤
│ Everyone          Resources status  │
│ [ message.................... ] ➤   │
│ 🚨 Emergency — Need Help              │
└─────────────────────────────────────┘
```

### Bottom navigation

| Tab | Action |
|-----|--------|
| **Home** | Area feed (default) |
| **Nearby** | Map of nodes sharing location |
| **Resources** | Coordinator — offers, needs, matches |
| **Alert** | Focus / confirm emergency broadcast |

---

## Feed visual language

| Kind | Treatment |
|------|-----------|
| **Neighbor message** | Normal text, primary color |
| **Your message** | Teal accent |
| **System** (Group, Status, Photo, Profile, Admin…) | Dimmed, smaller feel |
| **ICE / medical** | Amber-tinted background block |
| **Emergency** | Red accent, bold |

---

## Design principles

1. **Emergency infrastructure, not a social app** — every element purposeful
2. **No monetization noise** — pure neighbor signal
3. **Clarity under pressure** — readable at 2 AM with no cell service
4. **Make the mesh visible** — transport dots + signal bars build trust
5. **Resources during disaster** — generators, meds, rides, skills — the killer tab

---

## Cursor prompt (paste with screenshots)

```
This is the current MeshHood UI prototype. Use it as the design foundation and build on it. Do not rebuild from scratch.

Preserve: dark theme, transport status, neighbor count, locality selector, ICE in feed, emergency button, encrypted badge, chats, transport tags on messages.

Refine: visual transport dots (BLE / WiFi Direct / WiFi LAN), feed separation (system vs neighbor vs ICE), mesh signal bars, Resources tab, top composer tone, bottom nav (Home / Nearby / Resources / Alert).

Blend Nextdoor's neighborhood warmth and feed structure onto MeshHood's dark utilitarian infrastructure. No ads. No decorative fluff.
```

See also: [ARCHITECTURE.md](ARCHITECTURE.md), [PITCH.md](PITCH.md).
