# Mapsicle

A localized, offline-friendly OpenStreetMap viewer for Android, built with
[MapLibre Native](https://maplibre.org/maplibre-native/) and
[BRouter](https://brouter.de/brouter-web/) for routing.

Application ID: `com.padelle.mapsicle` · Min SDK 23 (Android 6.0) · Target SDK 36

## Features

- **Map**: MapLibre vector map on OpenFreeMap Positron. No API key, no account:
  55 layers instead of the 160 of MapTiler Streets, and a 25 KB style instead of
  167 KB.
- **Search**: geocoding via Photon, debounced, served from a small thread pool
  with an on-disk HTTP cache. Results are shown as soon as they arrive and then
  refined, instead of being thrown away and re-fetched.
- **Routing**: turn-by-turn guidance with distance, duration and maneuver
  instructions computed from BRouter.
- **Localization**: full Italian and English support for the UI, map labels,
  search results and turn instructions. Follows the system locale.
- **Position**: the puck follows you continuously while the app is in the
  foreground, with a direction cone while you move (MapLibre `LocationComponent`
  in compass render mode). The camera locks onto you during guidance and
  releases as soon as you touch the map; the "my location" button re-arms it.
- **Startup geolocation**: if location permission is already granted, the app
  opens centered on where you are. It never prompts for permission on launch.
  It first paints the last fix the system already knows, so the map moves
  immediately, then refines on the live fix.
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
./gradlew :app:assembleRelease -PversionCode=1004 -PversionName=0.4.0
```

## Tests

```bash
./gradlew :app:testDebugUnitTest
```

20 unit tests covering geoprojection and distance math, route parsing,
locale resolution, search term localization and style localization.

## Releases

Pushing a `v*` tag builds a signed APK with GitHub Actions and publishes it as
a release asset:

```bash
git tag -a v0.4.0 -m "Mapsicle v0.4.0"
git push origin v0.4.0
```

`versionName` comes from the tag and `versionCode` is derived from it
(`0.4.0` → `1004`), so it always increases and an install over a previous
release is accepted.

See `.github/workflows/release.yml`. The required repository secrets are
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`.

Because every release is signed with the same key and uses the same
application ID, each new version replaces the installed one in place — no
uninstall, and app data is preserved.

## Web prototype

`pc-test.html` is a standalone browser prototype of the same ideas. It reads its
MapTiler key from a local file that is intentionally **not** tracked. The
Android app no longer needs a key at all.

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
  StyleLanguage.kt     map style localization
```

## Attribution

Maps are © [OpenStreetMap](https://www.openstreetmap.org/copyright)
contributors. Map data is available under the
[ODbL](https://opendatacommons.org/licenses/odbl/). The basemap is provided by
[OpenFreeMap](https://openfreemap.org/).
Please keep the attribution visible if you redistribute this app.
