# Mapsicle

An OpenStreetMap map for Android with the places on it, search and turn-by-turn
directions. No API key, no account, no advertising, no tracking. It is not on
the Play Store: you install the APK from the releases page.

**Android 6.0 or newer** · about 43 MB · Italian and English · light and dark
theme straight from the system.

- **Users** — [Install](#install-in-30-seconds) · [What you can do](#what-you-can-do) ·
  [Permissions](#permissions-and-why) · [What it does not do](#what-it-does-not-do) ·
  [Privacy](#privacy-in-three-lines) · [Troubleshooting](#if-something-does-not-work)
- **Developers** — [Building](#building-locally) · [Tests](#tests) ·
  [Releases](#releases) · [Project structure](#project-structure)

Application ID `com.padelle.mapsicle` · min SDK 23 (Android 6.0) · target SDK 36
· MIT licensed.

## Install in 30 seconds

Grab the APK from the releases page and install it:

1. Open <https://github.com/padelle2603/mapsicle/releases/latest> and download
   the `mapsicle-v<version>.apk` file at the bottom of the release.
2. Your browser will ask whether it may install unknown apps. Allow it for the
   browser you are using, just for this download.
3. Open the downloaded file and confirm the installation.

Android warns you about installing from outside the Play Store. That warning is
expected: this app is not distributed through Google.

**Updating.** Every release is signed with the same key and uses the same
application ID, so a new version installs straight on top of the old one: no
uninstall, your settings and app data stay where they are. The app also checks
once at start whether a newer release exists and offers it, so normally you do
not come back to this page. One exception: `v1.2.0` was published twice under
the same version number, the second time with the places, the Google Maps button
and this update check, so if you installed the first `v1.2.0` the app will not
offer you anything newer. Take the APK from the releases page.

## What you can do

**Move around the map.** The classic OpenStreetMap colours: green parks, blue
water, green woodland, and the whole road network. Pan with a finger, pinch to
zoom, rotate with two fingers. The area you have already looked at keeps
working with no connection, because the tiles are cached on the device.

**Find a place.** Type at least three characters in the **From** or **To**
field and the suggestions appear as you type: streets, towns, addresses and
landmarks, with the names in their own language. They are ranked around your
position when you have granted the location permission, so "pizzeria" finds the
one near you and "Roma" still finds Rome.

**Tap a place on the map.** Zoom in far enough and the restaurants, shops,
hotels, pharmacies, parks, museums, schools, parking and the other places worth
going to appear with their own icon, and their name at the next zoom level. Tap
one and a card opens with its name, its kind of place and how far away it is.
Only places worth going to are drawn: no rubbish bins, gates or telephone poles.
The 55 kinds of place are grouped into 14 categories, so a bank and a pharmacy
are both "services" and an ice-cream parlour and a steakhouse are both "food and
drinks". Icons appear from zoom 16, names from zoom 17.

**Get there.** Fill in **From** and **To**, pick **Walk**, **Bike** or **Car**,
tap **Calculate** and the route is drawn on the map with its distance, its
duration and the list of steps. Tap **Accept** to start following it: the
camera stays on you, the next step is shown with its distance, a progress bar
counts how much of the route is behind you, and the instructions are written in
Italian or English. You are told when you have arrived, within 25 m of the
destination. **Stop navigation** or the button in the panel header clears
everything again.

**Know where you are.** The blue dot follows you while the app is in the
foreground. The button at the bottom right centres the map on you; touching the
map releases the camera so you can look around without it chasing you, and the
button takes it back. If the permission is already granted when you open the
app, the map starts centred on you, using the last position the system knows so
that it moves immediately, and refines on the live one as soon as it arrives.

**Send a place to Google Maps.** From the card of a tapped place, **Open in
Google Maps** hands the place over to Google Maps, which knows the opening
hours, the reviews, the phone number and the website that the map itself does
not carry. If Google Maps is not installed, the same link opens in your browser.

**Stay current.** At every start the app asks GitHub, in one anonymous request,
whether a newer release of Mapsicle exists. If it does, a dialog offers it: the
download opens in your browser. If it does not, or the network or GitHub's rate
limit says no, nothing happens at all. No account, no token, no device
identifier, and nothing is remembered about the answer, so **Not now** closes
the dialog and it will ask again next time.

## Permissions, and why

| Permission | Why |
| --- | --- |
| `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION` | To show the blue dot and to start a route from where you are. Asked for only when you tap the location button or ask for a route from your position, never at start, and never in the background. Denying it leaves everything else working, without the blue dot. |
| `INTERNET`, `ACCESS_NETWORK_STATE` | The map, the suggestions, the routes and the update check. |

Never requested: storage, contacts, camera, microphone, the advertising
identifier. The app keeps no account and no profile of you.

## What it does not do

- **Search and routing need a connection.** They are computed by public
  services, on the device. The map you have already looked at keeps working
  offline, but there is no download of maps for offline use.
- **The map does not carry opening hours, phone numbers, websites or addresses.**
  The tile that draws the map holds the name, the kind of place and the
  coordinates, and nothing else: the tile of central Milan has zero features
  with opening hours out of 7063. That is what the **Open in Google Maps**
  button is for.
- **Places on the map need a high zoom.** Icons from zoom 16, names from zoom
  17. In a whole city at once the map is streets and green areas.
- **Guidance has no voice.** The steps are written on screen; the app never
  speaks and never plays a sound.
- **The app does not notice that you left the route.** It keeps following the
  line of the route and shows the next step of that route, so after a wrong turn
  the arrow keeps pointing at the old one. Stop the guidance and calculate the
  route again from where you actually are.
- **It is not on the Play Store, and it is not on F-Droid.** The APK comes from
  the releases page, which is also the only update channel.
- **There is no account, no advertising, no analytics and no map style picker.**
  One style, the OpenFreeMap Liberty one.

## Privacy in three lines

Four services, all public and free, and none of them needs an account or an API
key: OpenFreeMap for the map, Photon for the search, BRouter for the routes and
the GitHub API for the update check. Your position is rounded to about 111 m
before it is sent, and only after you have granted the permission.

On the device the only thing written to storage is a 128 MB HTTP cache, so the
map you have already looked at opens again without a connection. It is readable
only by the app and goes away when you clear the cache or uninstall. No account,
no identifier, no profile of you anywhere.

The details, including exactly what each request carries, are in
[PRIVACY.md](PRIVACY.md).

## If something does not work

**The map is grey, blank or missing streets.** The style and the tiles are
downloaded on first use: check the connection and reopen the app, or zoom in and
out once to force it. A blank area you have never looked at cannot appear
without a connection.

**"Suggestions unavailable" or "No suggestions".** The geocoder could not be
reached, or nothing matched what you typed. Check the connection, and try at
least three characters, or a larger place name: a street alone often finds
nothing.

**"Route unavailable" or "Select start and destination".** Both fields need a
place, and the routing service has to answer. Free public routing can be slow
or down; if it fails, try again in a moment.

**The update dialog never appears.** It only appears for a version **newer**
than the one you have, so if you are already on the latest there is nothing to
show. `v1.2.0` in particular was published twice under the same version number:
if you installed the first one, take the APK from the releases page. GitHub
also allows 60 anonymous requests an hour per IP address, and past that the
check fails silently without telling you.

**To uninstall.** Long press the icon and uninstall, like any other app. Nothing
of yours is left on any server, because nothing ever was.

## Attribution

Maps are © [OpenStreetMap](https://www.openstreetmap.org/copyright)
contributors. Map data is available under the
[ODbL](https://opendatacommons.org/licenses/odbl/). The basemap is provided by
[OpenFreeMap](https://openfreemap.org/), the search by
[Photon](https://photon.komoot.io/), the routing by [BRouter](https://brouter.de/).
Please keep the attribution visible if you redistribute this app.
The credit line is always on screen at the bottom of the panel; tapping it
opens the app's own licenses (MIT, Apache-2.0, BSD-2-Clause), which also ship
inside the APK.
The full list of bundled third-party components and their licenses is in
[LEGAL.md](LEGAL.md).

## Documents

- [Releases](https://github.com/padelle2603/mapsicle/releases) — the APK
- [MANIFEST.md](MANIFEST.md) — why Mapsicle exists, its mission and principles
- [PRIVACY.md](PRIVACY.md) — what leaves your device, and what stays on it
- [LEGAL.md](LEGAL.md) — license, third-party components, attributions
- [LICENSE](LICENSE) — MIT

*Developed with the assistance of [opencode](https://opencode.ai).*
