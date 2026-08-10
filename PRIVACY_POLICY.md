# Privacy Policy for Compass & Offline Maps

_Last updated: 2026-08-10_

Compass & Offline Maps ("the app") is developed by Bryan Hogan. This policy explains what data the app accesses and, just as importantly, what it does not do with it.

## Summary

The app does not collect, store, or transmit any personal data to the developer or to any third party. There is no analytics, no advertising, and no tracking of any kind built into the app.

## Permissions and how they're used

- **Location (fine/coarse)** — used only to show your current position on the compass, level, and map screens, and to compute magnetic declination (true vs. magnetic north) and a reverse-geocoded address for display. Location data stays on your device; the app does not upload it anywhere or store a history of it.
- **Internet / network state** — used only to fetch map tiles from OpenStreetMap's tile servers when you view or download the map. No usage data, identifiers, or analytics are sent by the app itself.

## Third-party services

- **OpenStreetMap** — map tiles are loaded from OpenStreetMap's tile servers (`tile.openstreetmap.org`) via the open-source osmdroid library. As with loading any map imagery, these requests inherently expose your device's IP address and the coordinates of the map area you're viewing to OpenStreetMap's infrastructure. This happens only when the map screen is open or when you explicitly download an area for offline use. See [OpenStreetMap's own privacy policy](https://osmfoundation.org/wiki/Privacy_Policy) for how they handle this.
- **Device reverse-geocoding** — the app asks the Android operating system to convert coordinates into a readable address (via the standard `Geocoder` API). Depending on your device manufacturer, this lookup may be performed on-device or by a service the OS itself calls out to; the app has no visibility into or control over that, and does not add any of its own geocoding service.

No other third-party SDKs (no analytics, no advertising, no crash reporting services) are included in the app.

## Offline map data

Map tiles you choose to download for offline use are stored locally in the app's private storage on your device. This data never leaves your device and is deleted if you clear the app's data/storage or uninstall the app, or if you use the in-app "Clear offline data" option.

## Crash reports

If the app crashes, a stack trace is written to the app's private local storage and shown to you the next time you open the app, so you can decide what to do with it. It is never automatically transmitted anywhere.

## Children's privacy

The app does not knowingly collect any information from anyone, including children, because it does not collect information at all.

## Changes to this policy

If this policy changes, the updated version will be posted at this same location with a revised "last updated" date.

## Contact

Questions about this policy can be sent to: bryanjhogan@gmail.com
