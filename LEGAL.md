# Legal Notices — Mapsicle

## License

Mapsicle is free software, released under the **MIT License**.

- Full text: [LICENSE](LICENSE)
- In short: you may use, study, copy, modify, merge, publish, distribute,
  sublicense and sell copies of Mapsicle. If you distribute the app or modified
  versions of it, you must keep the copyright notice and the license text.
- There is **no warranty** — see the MIT license, sections "THE SOFTWARE IS
  PROVIDED AS IS". Use it at your own risk.
- The same text is bundled in the app: tap the `© OpenStreetMap contributors`
  line at the bottom of the panel.

The map data is **not** covered by the MIT license: see the attributions below.

## Third-party components

Mapsicle bundles the following third-party software. All of it is permissive
(BSD-2-Clause, Apache-2.0, OFL-1.1) and compatible with the MIT license of
Mapsicle. The authoritative version list lives in the Gradle build files and in
`app/build/outputs/sdk-dependencies/` at build time.

| Component | Version | License | Notes |
|---|---|---|---|
| [MapLibre Native Android SDK](https://github.com/maplibre/maplibre-native) (`org.maplibre.gl:android-sdk-opengl`) | 13.3.1 | BSD-2-Clause | Map rendering. Bundles `libmaplibre.so` for arm64-v8a, armeabi-v7a, x86, x86_64. No Mapbox access token is used and no Mapbox service is contacted. |
| MapLibre Android SDK Turf / GeoJSON / gestures | 6.0.1 / 6.0.1 / 0.0.4 | BSD-2-Clause | Geometry helpers and gestures, pulled in by the SDK. |
| [OkHttp](https://square.github.io/okhttp/) and Okio | 4.12.0 / 3.6.0 | Apache-2.0 | All network requests, including map tiles. © Square, Inc. |
| [Timber](https://github.com/JakeWharton/timber) | 5.0.1 | Apache-2.0 | Logging facade pulled in transitively by MapLibre. Mapsicle plants no log tree, so nothing is written. © Jake Wharton. |
| [Gson](https://github.com/google/gson) | 2.10.1 | Apache-2.0 | Pulled in transitively by MapLibre. © Google Inc. |
| Kotlin standard library | 2.2.10 | Apache-2.0 | © JetBrains s.r.o. and Kotlin contributors. |
| [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) | 1.6.4 | Apache-2.0 | Pulled in transitively by the MapLibre SDK. |
| AndroidX (core, activity, fragment, lifecycle, profileinstaller, startup, savedstate, viewpager, tracing, …) | 1.13.1 / 2.6.2 / … | Apache-2.0 | Android support libraries. © Android Open Source Project. |
| Noto Sans Regular / Italic / Bold | as served | SIL Open Font License 1.1 | Map label glyphs. Not bundled: fetched as glyph files from the tile server. © Google LLC. |

Build-time only (not shipped in the APK): the Android Gradle Plugin and the
Android SDK from Google, Gradle from Gradle Inc. (Apache-2.0), and the JDK
(Temurin, GPL-2.0-with-classpath-exception) used in CI.

The full license texts of everything above — MIT, Apache-2.0, BSD-2-Clause —
**ship inside the APK** (`app/src/main/assets/licenses.txt`) and are readable
from the app by tapping the credit line at the bottom of the panel. That is
what Apache-2.0 §4(a) and §4(d) require: the license travels with the binary,
not only with its source. Keep the asset in the app if you fork Mapsicle.

## Map data and attribution

- Map data is © [OpenStreetMap](https://www.openstreetmap.org/copyright)
  contributors, licensed under the
  [Open Database License (ODbL 1.0)](https://opendatacommons.org/licenses/odbl/).
- The vector tiles follow the
  [OpenMapTiles](https://openmaptiles.org/) schema (also ODbL) and are served
  by [OpenFreeMap](https://openfreemap.org/), which hosts them without an API
  key.
- The visible attribution *"OpenFreeMap · © OpenMapTiles · Data from
  OpenStreetMap"* is rendered by the attribution control inside the app, as the
  ODbL requires. **If you redistribute Mapsicle, keep that attribution visible.**
- The line `© OpenStreetMap contributors` is also always on screen, at the
  bottom of the panel, collapsed or not, and opens the app's licenses on tap.
  This is the one line to keep if you fork the app: it is the ODbL credit, and
  it is also how the license texts stay reachable.

## Third-party services

Mapsicle contacts, at runtime, three public community services — OpenFreeMap
(OpenMapTiles), Photon (komoot) and BRouter — using their own terms and privacy
policies. They are described in [PRIVACY.md](PRIVACY.md). Mapsicle is **not
affiliated with, endorsed by or certified by** any of them, nor by the
OpenStreetMap Foundation.

## Trademarks

OpenStreetMap, OpenFreeMap, OpenMapTiles, Photon, komoot, BRouter, MapLibre,
Noto and Android are the property of their respective owners and are used here
only to describe the services, data and software the app depends on. Their use
does not imply any endorsement, sponsorship or affiliation.

## Changes to the app's map requests

Mapsicle identifies itself to the tile server with a User-Agent of the form
`Mapsicle/<version> (Android; com.padelle.mapsicle)`, uses no API key, and
limits itself to what a map viewer needs. If you fork the app, please keep that
identification intact: it is what allows the community infrastructure to answer
a user it can help.

## Contact

Maintainer: padelle2603 — https://github.com/padelle2603/mapsicle
