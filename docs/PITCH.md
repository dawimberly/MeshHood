# MeshHood pitch

Use these for investors, government partners, and patent counsel. Adjust names and numbers for the audience.

---

## Full pitch (~1999 characters)

In 2005, people died in their attics during Katrina because rescuers were blocks away and had no idea they were there. In 2021, Texans froze because neighbors did not know neighbors needed help. The same story repeats every disaster — not because we lacked technology, but because we lacked **infrastructure that survives when towers fail**.

We built it.

**MeshHood** is a self-healing communication network that works when everything else dies. It runs on the phones people already have. When cell towers fail it routes through WiFi. When WiFi fails it drops to Bluetooth mesh. When everything fails it keeps going — hopping phone to phone, block to block, neighborhood to neighborhood. No configuration. No cloud kill switch. It activates when infrastructure degrades.

Think Nextdoor — but the neighborhood owns it. No servers. No data harvest. No company between you and your neighbor. Every phone with the app becomes a node. The denser the crowd, the stronger the network. In a disaster people cluster. That clustering is exactly when our mesh is most powerful.

Every neighborhood gets its own **local AI** — runs on-device, never requires the cloud. It surfaces who has a generator, medical training, passable roads, or urgent needs.

We are proposing a **public–private partnership**: government provides emergency policy to open WiFi and cell access during declared disasters; we provide the mesh layer that connects everything else. Together that is what FEMA, DHS, and FirstNet have been trying to solve for twenty years.

The technology exists. The integration does not. Until now.

---

## One-liner

Sovereign emergency communication that degrades Bluetooth → WiFi → cell automatically and stays useful when all three are partial or gone.

---

## Revenue (199 characters)

Government contracts, city licensing, premium AI features, and LoRa hardware sales. Keep the emergency layer free forever. That is your moral foundation and your best marketing.

---

## Revenue (expanded)

| Stream | Notes |
|--------|--------|
| **Federal contracts** | FEMA, DHS, DARPA, FirstNet — resilient comms mandates |
| **Municipal licensing** | Annual per-city platform fee (hurricane / freeze / wildfire belts) |
| **Consumer freemium** | Base mesh + emergency free; premium AI, family status, business continuity |
| **Hardware** | LoRa bridge nodes for rural / rooftop range extension |
| **Partners** | Insurance and utilities — anything that reduces disaster loss |

**Rule:** Never paywall the emergency layer.

---

## Katrina stress test (talking points)

- Cell and 911 failed within hours; mesh keeps local coordination alive.
- Attic traps: a phone broadcasts location/status; hops via neighbors; reaches coordinators.
- Superdome-scale density = ideal mesh environment.
- First responders with the app become high-value bridge nodes.
- Goal is not stopping the storm — it is **connected self-rescue in the first 72 hours**.

---

## Multi-radio bridge (30 seconds)

Person A has WiFi only. Person B has Bluetooth only. Person C has cell. Any phone with **two radios** in range becomes an automatic bridge — no user configuration. The network routes on the best available path, not the radio each person started with.

**Patent angle:** Autonomous multi-radio mesh with dynamic transport selection and emergency activation. See [PATENT-BRIEF.md](PATENT-BRIEF.md).

---

## What is built vs roadmap

| Today (prototype) | Roadmap |
|-------------------|---------|
| BLE + WiFi Direct + LAN mesh | Cellular as optional uplink transport |
| Multi-hop relay, encrypted DMs | Licensed IoT / smart-home relay nodes |
| Profiles, photos, neighbor vouch | Automatic emergency activation policy |
| Area hierarchy + emergency SOS | FirstNet / WEA integration partnerships |

Repo: https://github.com/dawimberly/MeshHood
