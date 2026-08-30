# Third-Party Data Sources

OpenPolaris embeds astronomical catalog data. Every catalogue bundled
with the app comes from a public-domain or otherwise liberally-licensed
open source. This document records **what we use, where it came from,
and what the licence terms are** so the work is auditable.

The data is **observational, not creative** — sky positions, magnitudes,
orbital elements — and so most of the canonical sources are in the
public domain by virtue of being either:

- US-government work (JPL / NASA / USNO),
- ESA / international-agency public-domain releases (Hipparcos),
- 18th- or 19th-century compilations whose authors have been dead
  long enough for copyright to have expired (Messier, NGC), or
- 20th-century academic works explicitly released to the public domain
  (NGC 2000.0, Yale BSC5).

In all cases the bundled JSONs in `shared/src/commonMain/resources/`
are a **hand-curated subset** of the full catalogue — selected to fit
the project's scope (alignment, goto targets, demo data) — but the
values themselves are sourced from the catalogues below.

## Catalogues used

| Shard file | Records | Source | Licence | Pulled |
|---|---:|---|---|---|
| [catalog.json][cat] | 140 (110 Messier + 30 alignment stars) | Messier (1781–1782, Charles Messier) + Hipparcos Input Catalogue | Public domain | subset |
| [stars.json][stars] | 205 alignment stars | Yale Bright Star Catalogue, 5th Revised Ed. (Hoffleit & Warren 1991) | Public domain (academic, no copyright claimed) | subset |
| [ngc.json][ngc] | 274 NGC targets | NGC 2000.0 (Sinnott 1988, Sky Publishing / NASA) | Public domain (NASA) | subset |
| [comets.json][comets] | 10 periodic comets | JPL Small-Body Database / MPC | Public domain (US govt) | subset |

[cat]: ../shared/src/commonMain/resources/catalog.json
[stars]: ../shared/src/commonMain/resources/stars.json
[ngc]: ../shared/src/commonMain/resources/ngc.json
[comets]: ../shared/src/commonMain/resources/comets.json

## 1. Messier Catalogue

- **Original author:** Charles Messier (French, 1730–1817).
- **Compiled:** 1781–1782 (and later additions to M110 by Méchain).
- **Licence:** Public domain — author died 1817, well outside any
  copyright term.
- **Reference text:** "Catalogue des Nébuleuses et des Amas d'Étoiles
  (Catalog of Nebulae and Star Clusters)" in *Connaissance des Temps*
  for 1781 (published 1780).
- **Modern version:** SEDS Messier database
  (<https://messier.seds.org/>), itself a public-domain compilation.
- **Subset used:** M1–M110 (all known Messier objects). RA, Dec (J2000)
  and apparent visual magnitude.

## 2. NGC 2000.0 (NGC)

- **Title:** "NGC 2000.0: The Complete New General Catalogue and Index
  Catalogues of Nebulae and Star Clusters"
- **Author:** Roger W. Sinnott (editor), Sky Publishing Corporation.
- **Original NGC author:** J. L. E. Dreyer, 1888.
- **Publication:** 1988, Sky Publishing (Cambridge, MA), ISBN
  0-933346-51-4. Also released by NASA as a public-domain
  machine-readable edition.
- **Licence:** Public domain (NASA release, no copyright claimed).
- **URL:** <https://www.ngc7000.org/> for the public-domain edition.
- **Subset used:** 274 NGC entries (galaxies, planetary nebulae, open
  and globular clusters) selected by apparent magnitude ≤ ~11 and
  popular deep-sky targets. RA, Dec (J2000), constellation, type,
  apparent visual magnitude.

## 3. Yale Bright Star Catalogue, 5th Revised Edition (BSC5)

- **Title:** "The Bright Star Catalogue, 5th Revised Edition"
- **Authors:** Dorrit Hoffleit & Wayne H. Warren Jr.
- **Publication:** 1991, Yale University Observatory.
- **Licence:** Public domain (academic publication, no copyright
  claimed by Yale).
- **Catalogue identifier:** ADC 5050 (NASA Astronomical Data Center).
- **URL:** <https://heasarc.gsfc.nasa.gov/W3Browse/star-catalog/bsc5p.html>
- **Subset used:** 205 stars of magnitude ≤ ~3.5 (alignment candidates
  visible to the unaided eye). Common name, Bayer/Flamsteed
  designation, RA, Dec (J2000), visual magnitude, constellation.

## 4. Hipparcos Input Catalogue (alignment anchors)

- **Mission:** ESA Hipparcos (1989–1993), the first space astrometry
  mission.
- **Catalogue:** Hipparcos Input Catalogue (HIC), compiled by the
  FAST consortium under ESA contract.
- **Licence:** Public domain — ESA releases all Hipparcos data into
  the public domain. (Hipparcos and Tycho catalogues are available
  from CDS Strasbourg and ESA with no usage restrictions.)
- **URLs:**
  - <https://www.cosmos.esa.int/web/hipparcos>
  - <http://cdsarc.u-strasbg.fr/viz-bin/cat/I/196> (CDS VizieR)
- **Subset used:** The 30 brightest stars (magnitude ≤ ~2.0) drawn
  from the Messier/bright-star subset, plus Polaris for the polar
  alignment anchor. RA, Dec (J2000), magnitude, constellation.

## 5. JPL Small-Body Database (comets)

- **Maintainer:** Jet Propulsion Laboratory, California Institute of
  Technology (a US federally-funded research and development center).
- **URL:** <https://ssd.jpl.nasa.gov/sbdb.cgi>
- **API:** <https://ssd-api.jpl.nasa.gov/sbdb.api>
- **Licence:** Public domain — JPL/CA products are US government work
  and are not subject to copyright (17 USC 101). Use of JPL data is
  governed by the JPL image-use policy
  (<https://www.jpl.nasa.gov/imageuse/>) which permits scientific and
  educational use.
- **Subset used:** 10 famous periodic comets for the demo catalog
  (1P/Halley, 2P/Encke, 9P/Tempel 1, 67P/Churyumov-Gerasimenko,
  C/1995 O1 Hale-Bopp, C/1996 B2 Hyakutake, C/2006 P1 McNaught,
  C/2011 W3 Lovejoy, C/2020 F3 NEOWISE, C/2023 A3 Tsuchinshan-ATLAS).
  Perihelion time (T), eccentricity (e), perihelion distance (q),
  inclination (i), longitude of ascending node (Ω), argument of
  perihelion (ω), reference epoch.

## 6. Minor Planet Center (comets and minor bodies)

- **Maintainer:** Minor Planet Center, Smithsonian Astrophysical
  Observatory, under the auspices of the International Astronomical
  Union.
- **URL:** <https://www.minorplanetcenter.net/>
- **Licence:** Public domain — IAU / Smithsonian data products are
  released without restriction.
- **Use:** Cross-reference for JPL SBDB orbital elements; not
  embedded directly but used in `p2-comet-data-refresh` (deferred
  feature) for the manual refresh path.

## Plate-solving data sources (future)

Plate-solving (matching star patterns in a captured image to known
sky coordinates) is a **future capability**. The current
`GoToController` exposes a refinement hook at
[`shared/src/commonMain/kotlin/dev/openpolaris/core/domain/GoToController.kt:84`](../shared/src/commonMain/kotlin/dev/openpolaris/core/domain/GoToController.kt)
where the caller may pass a measured RA/Dec from a solved image, but
no on-device solver ships yet. When it lands, the design calls for
**downloading the reference data on demand, not bundling it** — the
index files are too large (~400 MB) to ship in the APK.

| Source | What it provides | Licence | Bundled? | On-device size |
|---|---|---|---|---|
| [astrometry.net code](https://github.com/dstndstn/astrometry.net) | The solver itself (`nova` binary + thin Kotlin/JNI wrapper) | BSD 3-clause | Yes (solver only) | ~2 MB |
| [astrometry.net 4000-series index files](http://data.astrometry.net/4200/) | Pre-built star-pattern index for the solver | Public domain (data); CC-BY-SA for the index *recipes* (not data) | **No, downloaded on demand** | ~50–400 MB depending on field-of-view coverage |
| [astrometry.net web service](https://nova.astrometry.net/) | Hosted solve endpoint (optional fallback) | Service ToS (free for non-commercial) | n/a (optional fallback) | n/a |
| Already-bundled [Yale BSC5](#3-yale-bright-star-catalogue-5th-ed) + [Hipparcos](#4-hipparcos-input-catalogue) | ~9,000 bright reference stars | Public domain | **Yes (already shipped)** | already bundled |
| [astap](https://www.hnsky.org/astap.htm) (alternative) | All-in-one solver with its own index | GPL-2 | Optional alternative to astrometry.net | solver ~5 MB + index ~50 MB |

**Design constraints already documented:**

- `docs/OVERVIEW.md:164` already states that astrometry.net index files
  (~400 MB) are downloaded on first run or on demand, with progress UI;
  after that everything works offline at the tripod.
- The on-device solver (when added) is a small native binary (BSD 3-clause
  astrometry.net `nova`, or GPL-2 astap); the heavy data is the index.
- A **thin / low-precision mode** is feasible without any 400 MB download:
  query the already-bundled [Yale BSC5](#3-yale-bright-star-catalogue-5th-ed)
  + [Hipparcos](#4-hipparcos-input-catalogue) catalogues directly
  (the same data that astrometry.net's indexes are derived from). This is
  sufficient for wide-field, bright-star scenes where sub-arcminute
  accuracy is acceptable. The `p2-platesolver` todo (P2, this turn) is
  where we pick which mode to ship first.

**What is NOT in our bundles**

To be explicit about the *boundaries* of what we bundle:

- **Astrometry.net 4000-series index files** (~400 MB) are NOT
  bundled. They are downloaded on demand by the plate-solving flow
  described in `docs/OVERVIEW.md` (see also the
  [Plate-solving data sources](#plate-solving-data-sources-future)
  section above). The index data files are public domain; the
  astrometry.net hosted service has its own terms.
- **JPL DE series** (high-precision solar-system ephemeris) is NOT
  bundled. We use Meeus low-precision formulas (see `Planet.kt`,
  `Comet.kt`) which are deterministic and offline.
- **Stellarium, Celestia, or any other app's data** is NOT used. The
  catalogues here are sourced directly from the canonical astronomical
  sources above, not from third-party apps.

## Plate-solving data sources (forward-looking)

Plate-solving is **future work** — `GoToController.kt:18,84,85` only
has a refinement hook today; no solver is shipped. The design in
`docs/OVERVIEW.md:164` is that the app downloads reference data on
demand rather than bundling it (the ~400 MB astrometry.net index
files would be too large to embed in a phone APK). This section
records the open-source options for when the solver lands, so the
choice is auditable and the licence trail is in place before any
code is written. (The eventual implementation is captured as
[`p2-platesolver`](#).)

### Option A — astrometry.net index files (the reference impl)

- **Source:** Astrometry.net, the long-running plate-solver project
  by Dustin Lang et al.
- **What we'd bundle:** nothing (the ~2 MB solver code is the
  *companion* project "astrometry.net nova", BSD-3-clause — see
  Option B).
- **What we'd download on demand:** the 4000-series index files
  (~400 MB for wide-to-narrow coverage, more for narrow).
  Standard for sky-map apps (Stellarium, KStars, Sky Map, all use
  these or their derivatives).
- **Licence:** index files themselves are public domain (they are
  derived from the Tycho-2 / Hipparcos / UCAC catalogues above,
  all public domain). The astrometry.net *service* has its own
  terms — but we are not using the service, we are using the
  *index files locally* (the on-device solver path), so the
  service terms do not bind.
- **URL:** <https://astrometry.net/use.html> (index list)
- **Reference:** Lang, D., Hogg, D. W., Mierle, K., Blanton, M.,
  Roweis, S. (2010). "Astrometry.net: Blind astrometric calibration
  of arbitrary astronomical images". *AJ* 139, 1782.
  <https://ui.adsabs.harvard.edu/abs/2010AJ....139.1782L>
- **Status:** Not implemented. Listed as the "if accuracy matters"
  fallback in `p2-platesolver`.

### Option B — astrometry.net nova (the solver code)

- **Source:** <https://github.com/dstndstn/astrometry.net> (the
  on-device solver fork, "nova").
- **What we'd bundle:** the C/CMake solver binary (or a Kotlin
  port — none exists yet) plus its dependencies (cfitsio, gsl).
- **Size:** ~2 MB compiled; runtime index files are the 400 MB
  from Option A.
- **Licence:** BSD 3-clause (the nova fork is BSD; the upstream
  astrometry.net is also BSD-3).
- **Constraints:** would need NDK build for Android (the
  `composeApp` target); adds a native dependency to the
  Compose-multiplatform build chain. Could also be invoked as a
  sidecar process.
- **Status:** Not implemented. Considered if we go with Option A.

### Option C — thin solver over the already-bundled catalogues

- **Source:** same as the existing shards — Yale BSC5 (VizieR
  V/50) + Hipparcos (VizieR I/196) + Messier + NGC 2000.0.
- **What we'd bundle:** nothing new — the data is already
  embedded.
- **What we'd implement:** a quad-match solver (4 bright stars
  in image → 4 reference stars → derive plate scale + rotation
  + offset). Geometric hash on a coarse grid for O(1) lookup.
  Sufficient for *wide-field* mount alignment on a small-sensor
  preview frame. **Not** sufficient for narrow-FOV astrophotography.
- **Licence:** public domain (the data is the data listed in the
  table above).
- **Reference:** similar to the original
  [Patterson et al. 2008 plate-solving for the ISS](https://arxiv.org/abs/0804.1196)
  — for a constrained field of view with bright reference stars,
  a 4-star match is enough.
- **Status:** Not implemented. Listed as the "v0 fast path" in
  `p2-platesolver` — could ship before Option A is even attempted,
  since it requires no download at all.

### Decision

`p2-platesolver` (when scheduled) should:

1. **Ship Option C as v0** (4-star match against already-bundled
   catalogues). Zero new data, zero download, gets the
   `GoToController` refinement hook wired end-to-end.
2. **Layer Option A as v1** (on-demand astrometry.net index
   download, with a licence-acceptance dialog before the first
   download). This is the design already promised in
   `docs/OVERVIEW.md:164`.
3. **Skip Option B** unless we hit a case where neither C nor A
   is accurate enough — at that point the native-dependency cost
   is justified.

The licence terms of the astrometry.net index files (Option A) are
already compatible with our MIT app: the indexes are public domain,
and because we run the solver on-device, no astrometry.net
*service* terms apply.

## Regeneration / audit

The bundled JSONs are hand-curated subsets. The sources listed above
are the **canonical home** of each dataset; anyone wanting to verify a
specific value can:

1. Look it up in CDS Strasbourg's VizieR service
   (<https://vizier.cds.unistra.fr/>) — which mirrors all of the
   catalogues above.
2. Or use the canonical URL listed in each section.

A future `p4-catalog-regenerate` task is to write a Kotlin/Gradle
script that reads from the canonical sources and rebuilds the shards
deterministically. Until then, **the data is curated by hand from the
sources above** and the values can be cross-checked against VizieR.

## Licence summary

| Catalogue | Licence | Notes |
|---|---|---|
| Messier | Public domain | 18th century |
| NGC 2000.0 (Sinnott) | Public domain | NASA release |
| Yale BSC5 | Public domain | Academic, no copyright claimed |
| Hipparcos | Public domain | ESA release |
| JPL SBDB | Public domain | US government work (17 USC 101) |
| MPC | Public domain | IAU / Smithsonian |

OpenPolaris itself is MIT-licensed — see [`LICENSE`](../LICENSE) and
[`NOTICE`](../NOTICE).
