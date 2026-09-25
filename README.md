# Mapsicle

A localized, offline-friendly OpenStreetMap viewer for Android, built with
[MapLibre Native](https://maplibre.org/maplibre-native/) and
[OSRM](https://project-osrm.org/) for routing.

Application ID: `com.padelle.mapsicle` · Min SDK 23 (Android 6.0) · Target SDK 36

## Features

- **Map**: MapLibre vector map, defaults to MapTiler Streets with an
  OpenFreeMap Liberty fallback when no key is configured.
- **Search**: geocoding via Photon, with debounce and request throttling.
- **Routing**: turn-by-turn guidance with distance, duration and maneuver
  instructions computed from OSRM.
- **Localization**: full Italian and English support for the UI, map labels,
  search results and turn instructions. Follows the system locale.
- **Guidance**: continuous location updates with a recentered "my location"
  button and an overview button.
- **Dark mode**: follows the system theme.

## Building locally

Requires **JDK 21** and the Android SDK (platform 36).

```bash
export MAPTILER_API_KEY="your-maptiler-key"
./gradlew :app:assembleDebug
```

The API key is read from the `MAPTILER_API_KEY` environment variable at build
time and injected into `BuildConfig`. It is never stored in source control. If
it is empty the app falls back to OpenFreeMap, so the build still succeeds.

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

Version can be overridden per build:

```bash
./gradlew :app:assembleRelease -PversionCode=3 -PversionName=0.3.0
```

## Tests

```bash
./gradlew :app:testDebugUnitTest
```

18 unit tests covering geoprojection and distance math, route parsing,
locale resolution, search term localization and style localization.

## Releases

Pushing a `v*` tag builds a signed APK with GitHub Actions and publishes it as
a release asset:

```bash
git tag -a v0.2.0 -m "Mapsicle v0.2.0"
git push origin v0.2.0
```

See `.github/workflows/release.yml`. The required repository secrets are
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` and
`MAPTILER_API_KEY`.

Because every release is signed with the same key and uses the same
application ID, each new version replaces the installed one in place — no
uninstall, and app data is preserved.

## Web prototype

`pc-test.html` is a standalone browser prototype of the same ideas. It reads
its MapTiler key from a local file that is intentionally **not** tracked:

```js
// pc-test.local.js
window.MAPTILER_API_KEY = "your-maptiler-key";
```

## Project structure

```
app/src/main/java/com/padelle/mapsicle/
  MainActivity.kt      UI wiring, map, routing, guidance, permissions
  Geo.kt               projection, distance and bearing math
  Routing.kt           OSRM parsing and instructions
  Search.kt            Photon geocoding
  Suggestions.kt       debounce/throttle controller
  SingleLocation.kt    one-shot position fix with timeout
  Language.kt          locale resolution
  StyleLanguage.kt     map style localization
```

## Attribution

Maps are © [OpenStreetMap](https://www.openstreetmap.org/copyright)
contributors. Map data is available under the
[ODbL](https://opendatacommons.org/licenses/odbl/). Basemaps are provided by
[MapTiler](https://www.maptiler.com/) or [OpenFreeMap](https://openfreemap.org/).
Please keep the attribution visible if you redistribute this app.
