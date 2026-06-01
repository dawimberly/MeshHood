# Privacy & safety — location

## Mutual location only

Routine map pins use **pairwise mutual consent**. Your coordinates are not shown mesh-wide by default.

1. Open **Contacts** → pick a neighbor → **Share location** (sends a signed `locoffer`).
2. They tap **Accept** (`locaccept`) — both devices add the pair to their mutual set and exchange signed `locshare` updates.
3. Either party can **Stop sharing** (`lochide` + local revoke).

Receivers store `locshare` only when the sender is in their local mutual set. Flooded packets may still relay; non-mutual peers ignore them for the map.

## Emergency override

**Emergency** broadcasts always attach live GPS (and ICE when configured), regardless of mutual prefs.

When coordinates are available, the SOS message includes a Google Maps link (`Open in Maps: https://…`). Tapping the link or the **Open in Google Maps** button on an emergency card opens the location in the Google Maps app. If you have already downloaded offline map tiles for that area in Google Maps, the pin works without cell data within the downloaded region.

## Travel

On a significant area move (state / ZIP / locality change), pending offers are cleared and cached peer pins for non-mutual peers are dropped. Mutual pairs stay until revoked.
