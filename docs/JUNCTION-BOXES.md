# Junction boxes — Santa Barbara County

MeshHood shows bundled LoRa relay / junction box sites on the in-app map. Pins are strategic placement targets for the 5-node SB County deployment plan (urban gateways, ridge backbone, trailhead relays).

## Locations

| ID | Name | Lat | Lon | Tier | Status |
|----|------|-----|-----|------|--------|
| goleta-urban | Goleta urban gateway | 34.4358 | -119.8276 | urban | active |
| sb-urban | Santa Barbara urban | 34.4208 | -119.6982 | urban | planned |
| la-cumbre-ridge | La Cumbre / Camino Cielo ridge west | 34.471 | -119.72 | ridge | planned |
| gibraltar-ridge | Camino Cielo ridge east / Gibraltar | 34.504 | -119.682 | ridge | planned |
| inspiration-trailhead | Inspiration Point trailhead | 34.458 | -119.687 | trailhead | planned |
| cold-spring-trailhead | Cold Spring trailhead | 34.457 | -119.715 | trailhead | planned |
| gaviota-pass | Gaviota Pass / Refugio corridor | 34.472 | -120.09 | ridge | planned |

Source of truth: `app/src/main/assets/junction_boxes.json`

## Map pin legend

- **Purple** — urban gateway
- **Amber** — ridge backbone
- **Teal** — trailhead relay

Tower/antenna pins are distinct from mesh peer pins (azure), feed pins (red/green/orange), and emergency facility pins (blue cross).

## Import into Google My Maps

1. Open [Google My Maps](https://www.google.com/maps/d/).
2. Create a new map.
3. **Add layer → Import** and upload `tools/junction_boxes.kml` from this repo.
4. Style layers by tier if desired (urban / ridge / trailhead).
5. Share or download offline for field planning.

## Manual test checklist

- [ ] Open **Menu → Map** — map loads without crashing.
- [ ] Junction chip shows **Junction boxes (7)** (or current count).
- [ ] Seven tower pins visible across Goleta, SB, front country, and Gaviota pass.
- [ ] Urban pins are purple-tinted; ridge amber; trailhead teal.
- [ ] Tap a junction pin — dialog shows name, tier, status, notes, distance (when GPS available).
- [ ] **Navigate in Google Maps** opens turn-by-turn navigation.
- [ ] **Open in Google Maps** opens the pin in the Maps app.
- [ ] **Nearest junction box** highlights closest site and opens its dialog.
- [ ] Peer, feed/agency/emergency, and blue-cross facility pins still render correctly.
- [ ] Offline maps card mentions junction pins for LoRa relay sites.

## Automated tests

```bash
./gradlew assembleConsumerDebug testConsumerDebugUnitTest
```

- `JunctionBoxStoreTest` — JSON parse, count ≥ 5, SB County lat/lon bounds
- `JunctionBoxStoreTest.distanceMeters_ordersBoxesByProximity` — nearest-box distance ordering
