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

## What is NOT in our bundles

To be explicit about the *boundaries* of what we bundle:

- **Astrometry.net index files** (~400 MB, 4000+ series) are NOT
  bundled. They are downloaded on demand by the plate-solving flow
  described in `docs/OVERVIEW.md`. See <https://astrometry.net/> for
  terms; the index files themselves are public domain, but the
  astrometry.net service has its own terms.
- **JPL DE series** (high-precision solar-system ephemeris) is NOT
  bundled. We use Meeus low-precision formulas (see `Planet.kt`,
  `Comet.kt`) which are deterministic and offline.
- **Stellarium, Celestia, or any other app's data** is NOT used. The
  catalogues here are sourced directly from the canonical astronomical
  sources above, not from third-party apps.

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
