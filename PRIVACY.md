# Privacy Policy — Mapsicle

_Last updated: 26 September 2026_

> **Disclaimer.** This policy may be updated from time to time. The current
> version is the one published in this document.

This policy describes how Mapsicle handles data when you use it. The short
answer is: **Mapsicle has no account, no server of its own, no analytics, no
telemetry and no crash reporting.** It talks to exactly four public community
services — the map tiles, the geocoder, the routing engine and the release feed of
its own GitHub repository — and it tells you in this document what each of them
receives.

## 1. What Mapsicle is

Mapsicle is a free, offline-friendly OpenStreetMap viewer and navigator for
Android, published as source code and as a downloadable APK. It is a personal,
non-commercial project: there is no company behind it, no funding, no
advertising and no revenue of any kind.

Mapsicle **has no backend of its own**: no account system, no database, no sync
service, nothing listening on the internet. There is no machine operated by the
developer that your data could reach.

## 2. Network access

Mapsicle performs **no tracking, no analytics, no telemetry, no advertising and
no fingerprinting**. It contacts only the four services needed to draw a map,
to search a place, to compute a route and to tell you when a new version is out. Every request is **direct**: Mapsicle
adds no server of its own to the path, and the developer has no way to see,
intercept or record the traffic between your device and those services.

All traffic is **HTTPS only** — cleartext HTTP is disabled in the app.

### 2.1 Map tiles and map style — `tiles.openfreemap.org`

Served by [OpenFreeMap](https://openfreemap.org/), which hosts tiles built with
the [OpenMapTiles](https://openmaptiles.org/) schema. The app requests:

- the map style definition (`/styles/liberty`);
- the vector tiles for the **area currently visible on screen**;
- sprites and glyphs (font files) used to draw the map.

What that service receives is what any map viewer inevitably discloses: the
tiles your screen is showing, plus the request headers `User-Agent`
(`Mapsicle/<version> (Android; com.padelle.mapsicle)`) and `Accept-Language`.
**Your GPS position is never sent here.** The app does not attach a "where is
the user" parameter to tile requests.

### 2.2 Search / geocoding — `photon.komoot.io`

When you type something in the search field, the app sends that **search text**
to the public [Photon](https://photon.komoot.io/) geocoder (run by komoot) to
turn it into places on the map.

If — and only if — you have already granted the location permission **and** the
app has received a position fix, the request also carries that position as a
**ranking hint**, rounded to three decimals (roughly 111 m) and used only to
sort nearby results first. The rounded coordinate is not a precise track of
your movements, and it is never used to filter results: a search for a place far
away from you keeps working exactly the same.

Without the location permission, **no coordinate of any kind is sent**: the app
cannot centre itself on you and does not know where you are.

### 2.3 Places on the map

The restaurants, shops, hotels, pharmacies and parks drawn on the map are read
from the **vector tiles you are already downloading** to draw the basemap. Tapping
one shows its name, its category and how far it is from you, all on your device:
**no request, no identifier and no position is sent** when you tap a place. Asking
for directions to it is a routing request, and follows the next section.

The "Open in Google Maps" button of that card is the one exception, and only
because you tap it: it hands **the name of the place** to the Google Maps app (or
to your browser, if Google Maps is not installed) through a normal
`https://www.google.com/maps/search/?api=1&query=<name>` link. What happens to
the request after that — including whether Google shows it to you as a
search — is covered by [Google's privacy policy](https://policies.google.com/privacy).
**Your position is not part of that link**, and Mapsicle sends no data to Google
itself: the link is opened by the other app, not fetched by this one.

### 2.4 Routing — `brouter.de`

When you compute a route, the app sends to the public
[BRouter](https://brouter.de/) service the **start and destination
coordinates**, and the travel profile you chose (foot, bicycle or car).

If you chose to start the route from "my current position", the start
coordinate is your position at the moment you computed the route, at **full
precision**. This is inherent to routing: a router needs the two points to
connect. The route you get back is then computed and followed **entirely on
your device** — subsequent position updates during guidance are never sent
anywhere.

### 2.5 Update check — `api.github.com`

Once per app start, the app asks the public
[GitHub API](https://docs.github.com/en/rest) for the latest release of its own
source repository. The request is anonymous — no account, no token, no
`Authorization` header — and carries nothing but the IP address that every web
server sees, the standard request headers and a `User-Agent` containing the app
version. It sends **no device identifier, no position, no search text and no
information about what is on your map**.

The answer is used for one thing: if the version it names is newer than the one
you installed, a dialog offers you the APK, and "Not now" simply closes it. The
answer is not stored on your device, and declining changes nothing. GitHub
answers 60 such anonymous requests per hour and per IP address; when that limit
is reached the check fails silently and no dialog appears.

## 3. Data processed on your device

- **Your position**: read from the Android system location service (fused
  provider or GPS), held in memory to move the blue dot, to centre the camera
  and to compute route progress. It is written to no file and discarded when
  the app is closed.
- **Your search and your itinerary**: the current search results, the chosen
  start and destination, and the computed route (including its full coordinate
  polyline) live in memory for as long as the app is running. They are lost
  when the process is killed.

Mapsicle has **no history, no favourites, no saved routes and no account**:
there is nothing to accumulate over time.

## 4. Persistent storage

Mapsicle stores **no personal data on disk**. It has no database, no
preferences file, no account and no sync.

The only thing it writes to storage is a **128 MB HTTP cache** in the
application's private cache directory (`cache/http`), used so that map tiles,
sprites, fonts and repeated requests do not have to be downloaded again. Like
any web cache, it also contains the geocoding, routing and update-check
**responses**, and the request URLs that produced them — so the text you
searched and the coordinates of the route you asked for can be found in that
cache. It is readable only by
Mapsicle itself (and by root or a device backup tool), and Android clears it
when the app's cache is cleared or the app is uninstalled.

Mapsicle sets `android:allowBackup="false"` and declares no backup rules at
all, so nothing it holds is eligible for Android's cloud backup — and since
there is no personal data, no account and no settings to begin with, there is
nothing to transfer to another device either.

## 5. Permissions

Mapsicle declares four permissions. Two are granted automatically at install
time, two are requested only when you use the feature that needs them.

| Permission | Why | When it is requested |
|---|---|---|
| `INTERNET` | the four network services listed above | granted at install, **no dialog** |
| `ACCESS_NETWORK_STATE` | tell "offline" apart from "server error" | granted at install, **no dialog** |
| `ACCESS_FINE_LOCATION` | the blue dot, "centre on me", starting a route from your position, and route guidance | **runtime dialog**, only when you tap "my location", "use GPS as start", or accept a computed route |
| `ACCESS_COARSE_LOCATION` | same features, with a less precise position | same dialog as above |

No permission is requested on launch: the app opens on the map and asks for
nothing. If you already granted location, it simply opens centred on you.

Mapsicle does **not** request, and cannot use: background location, foreground
service, notifications, storage or media files, camera, microphone, contacts,
phone state, Bluetooth, `QUERY_ALL_PACKAGES`, or any advertising identifier.
Location access is only requested while Mapsicle is in the foreground and is
released as soon as you leave the app.

You can revoke location at any time from the system settings
(Settings → Apps → Mapsicle → Permissions). Mapsicle keeps working: the map,
the search and the routing are still available, only the blue dot, the
auto-centring and the "start from my position" option are disabled.

## 6. Third parties

| Service | What it is | What it receives |
|---|---|---|
| [OpenFreeMap](https://openfreemap.org/) / [OpenMapTiles](https://openmaptiles.org/) | free map tile hosting, no account, no API key | the map area you are viewing, IP address, request headers |
| [Photon](https://photon.komoot.io/) (komoot) | free geocoder, no account, no API key | your search text and, only with the location permission granted, a rounded (~111 m) position used to rank results |
| [BRouter](https://brouter.de/) | free routing service, no account, no API key | start and destination coordinates and the travel profile |
| [GitHub](https://github.com/) (`api.github.com`) | the release feed of the public source repository, read once per app start, no account and no token | IP address, `User-Agent` with the app version, nothing else — no device identifier, no position, no search text |
| [OpenStreetMap](https://www.openstreetmap.org/) | the underlying map data (not contacted at runtime) | n/a — the data is downloaded as tiles from OpenFreeMap |

These are public, community-run services. They are contacted directly by your
device, under their own terms and privacy policies, and they keep their usual
server logs (as any web server does). Mapsicle is **not affiliated with,
endorsed by or certified by** any of them.

**No availability guarantee.** These services are offered as they are, by
volunteers, with no service level: they can be slow, rate-limited, overloaded,
moved or discontinued at any time, and a public instance can start refusing
requests without notice. Mapsicle gives **no guarantee that search, tiles or
routing will work at any given moment**, and it cannot do anything about it if
they do not. Nothing in the app works around their limits.

The full list of bundled third-party components and their licenses is in
[LEGAL.md](LEGAL.md), and the same license texts are inside the app: tap the
`© OpenStreetMap contributors` line at the bottom of the panel.

## 7. Children

Mapsicle collects no personal data at all, from anyone, including children.

## 8. Changes to this policy

Any change to this document will be published in this file, with the
"Last updated" date changed accordingly. The version published with the release
you installed is the one that applied to that release.

## 9. Contact

Maintainer: padelle2603 — https://github.com/padelle2603/mapsicle
