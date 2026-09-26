# Mapsicle

A localized, offline-friendly OpenStreetMap viewer for Android, built with
[MapLibre Native](https://maplibre.org/maplibre-native/) and
[BRouter](https://brouter.de/brouter-web/) for routing.

Application ID: `com.padelle.mapsicle` · Min SDK 23 (Android 6.0) · Target SDK 36

License: [MIT](LICENSE) · [Manifesto](MANIFEST.md) · [Legal](LEGAL.md) ·
[Privacy](PRIVACY.md)

## Features

- **Map**: MapLibre vector map on OpenFreeMap Liberty. No API key, no account:
  111 layers instead of the 160 of MapTiler Streets, and a 43 KB style instead
  of 167 KB, with the same colours (green parks, blue water, green woodland).
- **Places on the map**: restaurants, shops, hotels, pharmacies, parks and 50 more
  kinds of place are drawn straight from the tile that already renders the map,
  with their real OpenMapTiles category icons: about 7000 of them per z14 tile
  over Milan, no extra request and nothing new to cache. Icons from z16, names
  from z17, and only named places worth going to: no rubbish bins, gates or
  telephone poles. Tap one for its name, category and distance, then "Directions"
  routes to it from where you are, unless you already typed a start, and
  "Open in Google Maps" hands the place over to Google Maps for the hours and
  the reviews the tile does not carry.
- **Search**: geocoding via Photon, debounced, served from a small thread pool
  with an on-disk HTTP cache. Results are shown as soon as they arrive and then
  refined, instead of being thrown away and re-fetched. Results are ranked
  around your position (radius 16 km, prominence weighted 40%), so "pizzeria"
  finds the one nearby and "Roma" still finds Rome. Duplicates are dropped and
  the list is refilled to five. Position is rounded to ~111 m before being sent
  to Photon, and nothing is sent at all until you grant location permission.
- **Routing**: turn-by-turn guidance with distance, duration and maneuver
  instructions computed from BRouter. One button in the panel header clears the
  whole itinerary, and "Stop navigation" does the same while guiding.
- **Localization**: full Italian and English support for the UI, map labels,
  search results and turn instructions. Follows the system locale.
- **Position**: a solid blue dot follows you continuously while the app is in
  the foreground. The camera locks onto you during guidance and releases as soon
  as you touch the map; the "my location" button re-arms it.
- **Startup geolocation**: if location permission is already granted, the app
  opens centered on where you are. It never prompts for permission on launch.
  It first paints the last fix the system already knows, so the map moves
  immediately, then refines on the live fix.
- **Updates**: on start, one anonymous request to the GitHub API asks whether a
  newer release of the app exists. If it does, a dialog offers the APK; if not,
  or if the network or the rate limit says no, nothing happens at all. No
  account, no token, no device identifier, nothing remembered about the answer.
- **Dark mode**: follows the system theme.

## Building locally

Requires **JDK 21** and the Android SDK (platform 36). No API key needed.

```bash
./gradlew :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

To install over an existing copy without uninstalling:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Signed release builds

Release builds are signed only when a keystore is provided through the
environment, so plain local builds never break:

| Variable | Description |
| --- | --- |
| `KEYSTORE_PATH` | Path to the keystore, absolute or relative to the repo root |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias |
| `KEY_PASSWORD` | Key password |

```bash
KEYSTORE_PATH=/path/to/mapsicle.jks \
KEYSTORE_PASSWORD=... \
KEY_ALIAS=... \
KEY_PASSWORD=... \
./gradlew :app:assembleRelease
```

`KEYSTORE_PATH` may be absolute or relative to the repository root.

Version can be overridden per build:

```bash
./gradlew :app:assembleRelease -PversionCode=1000000 -PversionName=1.0.0
```

## Tests

```bash
./gradlew :app:testDebugUnitTest
```

25 unit tests covering geoprojection and distance math, route parsing,
locale resolution, search term localization and style localization.

## Releases

Pushing a `v*` tag builds a signed APK with GitHub Actions and publishes it as
a release asset named `mapsicle-v<tag>.apk`:

```bash
git tag -a v1.0.0 -m "Mapsicle v1.0.0"
git push origin v1.0.0
```

`versionName` comes from the tag and `versionCode` is derived from all three
of its components as `major*1000000 + minor*1000 + patch` (`1.0.0` → `1000000`).
The build fails if that number would not be higher than the previous
release's, because Android rejects a non-increasing `versionCode` as a
downgrade and a published release cannot be taken back.

Unit tests and lint run as a gate before the build, so a release is never
published from a red build.

See `.github/workflows/release.yml`. The required repository secrets are
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`.

Because every release is signed with the same key and uses the same
application ID, each new version replaces the installed one in place — no
uninstall, and app data is preserved.

## Project structure

```
app/src/main/java/com/padelle/mapsicle/
  MainActivity.kt      UI wiring, map, routing, guidance, permissions
  Geo.kt               projection, distance and bearing math
  Search.kt            Photon geocoding
  Suggestions.kt       debounce controller
  SingleLocation.kt    one-shot position fix, cached-first, with timeout
  Routing.kt           BRouter parsing, instructions, route progress
  Language.kt          locale resolution
  MapStyle.kt           map style localization, places layers and categories
  Poi.kt                Google Maps link for a tapped place
  Update.kt             GitHub release feed and version comparison
```

## Attribution

Maps are © [OpenStreetMap](https://www.openstreetmap.org/copyright)
contributors. Map data is available under the
[ODbL](https://opendatacommons.org/licenses/odbl/). The basemap is provided by
[OpenFreeMap](https://openfreemap.org/).
Please keep the attribution visible if you redistribute this app.
The credit line is always on screen at the bottom of the panel; tapping it
opens the app's own licenses (MIT, Apache-2.0, BSD-2-Clause), which also ship
inside the APK.
The full list of bundled third-party components and their licenses is in
[LEGAL.md](LEGAL.md).

## Documents

- [MANIFEST.md](MANIFEST.md) — why Mapsicle exists, its mission and principles
- [PRIVACY.md](PRIVACY.md) — what leaves your device, and what stays on it
- [LEGAL.md](LEGAL.md) — license, third-party components, attributions
- [LICENSE](LICENSE) — MIT
