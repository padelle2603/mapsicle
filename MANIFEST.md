# Manifesto — Mapsicle

This document explains why Mapsicle exists, what its mission is and where it is
heading. It does not describe features or regulations: for those consult
[README.md](README.md), [LEGAL.md](LEGAL.md) and [PRIVACY.md](PRIVACY.md).

## 1. Why Mapsicle exists

The map is the last piece of software that still asks for a key.

Every other tool on a phone became free: notes, photos, music, calendars, code.
The map did not. To look at a street you sign up, you accept a subscription, you
hand over an email address, and then you are given a key that works only inside
one application, on one platform, tied to a quota you did not choose and cannot
see. The map is built by millions of volunteers and belongs to everyone, yet
the common way to *see* it is through a commercial intermediary that decides
how it looks, how much of it you get, and what it knows about you.

And the result is a second fragmentation. The place you looked at yesterday,
the route you planned for the weekend, the neighbourhood you annotated, the
custom style you spent an evening tuning: they all live inside one app, in
one account, on one vendor's servers. Move to another phone, another platform,
or another provider, and the map starts again from a blank world. Meanwhile the
data underneath — the part actually contributed by people — is open, free and
yours.

Mapsicle was born for this: not to replace OpenStreetMap, but to make it
directly usable, as a thin and honest layer over open data. A map that starts
without a key, without an account and without asking who you are; that shows
the map as the community drew it, in your own language; that turns a search
into a route and a route into guidance, on your own device.

## 2. Mission

Mapsicle's mission is to offer **the map itself, directly**: a free Android
viewer for OpenStreetMap that requires no API key, no account and no
registration, and that works the moment you install it.

Mapsicle is also **a navigator, not a map reader**. The point of a map on a
phone is to get somewhere: so a place is typed or tapped, the route is computed,
and the phone tells you where to turn, how far, and for how long — with the
instructions in the language you are actually reading.

The idea is simple: **the map belongs to everyone, so getting it should not
require an account.** No sign-up wall, no e-mail, no quota, no vendor lock-in.
The infrastructure underneath is community infrastructure — OpenFreeMap for the
tiles, Photon for the search, BRouter for the routes — used as it was meant to
be used: politely, anonymously, within reason.

## 3. Vision

Mapsicle's vision is that **an open map needs no gatekeeper in front of it**.

Not a replacement for the big map providers, who do valuable work and have
every right to be paid for it. Rather, the *possibility* of the alternative: an
app so small and so honest that keeping it alive costs nothing, and so complete
that nobody has to think about which one to use. The map is a public good; the
client for it should be one too.

In this vision, Mapsicle is the layer that lets open data be *used* rather than
merely admired. It is deliberately modest: no proprietary basemap, no "AI"
layer of suggestions, no engagement metrics, no streak, no social layer, no
upsell. What remains is the thing users actually came for — the map, your
position, and the way there — plus the details that make a difference in daily
use: an interface in Italian and English, labels on the map in your language
too, an offline-friendly cache so the map you already walked still appears, and
a position dot that does not jump around at the wrong times.

Long term, Mapsicle wants to be a reference: a small, auditable, installable
answer to the question *"can the whole map client be free software, built on
free infrastructure, with no key and no account?"* — one that can be forked,
read, trusted and replaced by anyone, because the whole of it is a few thousand
lines of readable Kotlin.

## 4. Who develops it

Mapsicle is a **personal** project, written and maintained by a single
independent developer. It is not a company, it has no funding, it is not
distributed commercially and it earns nothing from it.

There are no affiliations with OpenStreetMap Foundation, OpenFreeMap, komoot,
BRouter, OpenMapTiles or MapLibre: Mapsicle is independent and remains so. Its
independence is also a guarantee of neutrality — there is no service to promote,
no content to push, no company to please.

Being a private project, Mapsicle grows at the pace and by the criteria of the
person who develops it, for one purpose only: to be useful. A tool made
available to anyone who shares the same needs.

## 5. Guiding principles

- **No keys, no accounts**: the app must be installable and fully usable without
  an API key, a registration, an e-mail address or a payment. If a feature can
  only exist behind a key, it does not belong in Mapsicle.
- **Privacy by design**: no telemetry, no analytics, no crash reporting, no
  advertising, no account, no backend. What the app knows, it knows because you
  told it to.
- **Community infrastructure, used politely**: the free services Mapsicle
  depends on are a gift, not a resource to exploit. Requests carry a real,
  identifying User-Agent, are cached so the same tile is not re-fetched, and
  are limited to what a map viewer genuinely needs.
- **Openness of the data, respect for its terms**: the map is © OpenStreetMap
  contributors and licensed under the ODbL. The attribution stays visible in
  the app, the license travels with the source, and every third-party component
  is listed in [LEGAL.md](LEGAL.md).
- **The user's freedom, the user's language**: the app follows the system
  language, works in Italian and English, degrades gracefully with no permission
  and with no network, and never requires the user to accept anything to see
  what they already have.
- **Understandable code**: small, plain Kotlin, no analytics SDK, no remote
  configuration, no hidden behaviour. An app that asks for your position should
  be readable in one sitting.

## 6. Conclusion

Mapsicle is a free, key-free, account-free map and navigator for Android, built
on OpenStreetMap and on free community services. It was born from a private
need, it grew as an independent project, and it lives by the principles that
define it: openness, privacy, neutrality and the user's freedom.

For the technical details, the data handling and the legal analysis, see the
dedicated documents:

- [README.md](README.md) — features and build
- [PRIVACY.md](PRIVACY.md) — where your data is and how it is handled
- [LEGAL.md](LEGAL.md) — license, third-party components and attributions
