package dev.openpolaris.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.openpolaris.core.astro.AstroMath
import dev.openpolaris.core.astro.AstroObject
import dev.openpolaris.core.astro.Planet
import dev.openpolaris.core.astro.TonightSummary
import dev.openpolaris.core.domain.format2
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * "What's up tonight" call-out.
 *
 * Renders a one-shot summary of the sky for the observer's current
 * location, recomputed on a slow 60 s timer (cheap pure function) and
 * whenever the location text fields or the catalog/comets change. Each
 * row that has known RA/Dec exposes a "Slew" button that pre-fills
 * [AppViewModel.gotoRa] / [gotoDec] and fires [AppViewModel.goto].
 *
 * The pane is intentionally read-mostly: the only inputs are the
 * observer lat/lng text fields and a free-text catalog search.
 */
@Composable
fun TonightPane(vm: AppViewModel, modifier: Modifier = Modifier) {
    val lat = vm.latDeg.toDoubleOrNull()
    val lng = vm.lngEastDeg.toDoubleOrNull()
    // We re-run the summary whenever any of these change. The summary
    // is a pure function so recomputing on every recomposition is fine
    // for the small fixed catalog and the handful of comets.
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000)
            tick += 1
        }
    }
    val summary: TonightSummary? = if (lat != null && lng != null && abs(lat) <= 90.0) {
        TonightSummary.of(
            jd = AstroMath.julianDateNow(),
            latDeg = lat,
            lngEastDeg = lng,
            catalog = vm.tonightCatalog,
            comets = vm.tonightComets,
        )
    } else null

    Card(modifier = modifier.padding(8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Tonight", style = MaterialTheme.typography.headlineSmall)
            Text(
                "What is above the horizon right now. Tap Slew to point the mount.",
                style = MaterialTheme.typography.bodySmall,
            )

            // -- Observer location -----------------------------------------
            SectionCard("Observer") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = vm.latDeg,
                        onValueChange = vm::updateLat,
                        label = { Text("Latitude °") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = vm.lngEastDeg,
                        onValueChange = vm::updateLng,
                        label = { Text("Longitude °E") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (summary == null) {
                Text(
                    "Enter a valid latitude (–90…90) and longitude to see tonight's sky.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                return@Card
            }
            // tick is consumed so the LaunchedEffect re-fires recompositions
            @Suppress("UNUSED_EXPRESSION") tick

            // -- Sun + twilight --------------------------------------------
            SectionCard("Sun") {
                val s = summary.sun
                KeyValueRow("Alt", "${s.altitudeDeg.format2()}°  Az ${s.azimuthDeg.format2()}°")
                KeyValueRow("RA", AstroMath.formatRaHours(s.raDeg))
                KeyValueRow("Dec", AstroMath.formatDecDMS(s.decDeg))
            }

            SectionCard("Twilight") {
                val tw = summary.twilight
                KeyValueRow("Sunset", jdToClock(tw.sunsetJd, tw.isSunUp, "below horizon"))
                KeyValueRow("Civil dusk", jdToClock(tw.civilDuskJd, tw.isSunUp, "—"))
                KeyValueRow("Nautical dusk", jdToClock(tw.nauticalDuskJd, tw.isSunUp, "—"))
                KeyValueRow("Astro dusk", jdToClock(tw.astroDuskJd, tw.isSunUp, "—"))
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                KeyValueRow("Astro dawn", jdToClock(tw.astroDawnJd, tw.isSunUp, "—"))
                KeyValueRow("Nautical dawn", jdToClock(tw.nauticalDawnJd, tw.isSunUp, "—"))
                KeyValueRow("Civil dawn", jdToClock(tw.civilDawnJd, tw.isSunUp, "—"))
                KeyValueRow("Sunrise", jdToClock(tw.sunriseJd, tw.isSunUp, "above horizon"))
            }

            // -- Moon -------------------------------------------------------
            SectionCard("Moon") {
                val m = summary.moon
                KeyValueRow("Alt", "${m.altitudeDeg.format2()}°  Az ${m.azimuthDeg.format2()}°")
                KeyValueRow("RA", AstroMath.formatRaHours(m.raDeg))
                KeyValueRow("Dec", AstroMath.formatDecDMS(m.decDeg))
            }

            // -- Planets ----------------------------------------------------
            SectionCard("Planets") {
                if (summary.planets.isEmpty()) {
                    Text("No planet data.", style = MaterialTheme.typography.bodySmall)
                } else {
                    summary.planets.forEach { p ->
                        PlanetRow(planet = p.planet, altDeg = p.altitudeDeg, azDeg = p.azimuthDeg,
                            raDeg = p.raDeg, decDeg = p.decDeg,
                            mag = p.magnitudeApprox,
                            onSlew = {
                                val obj = vm.tonightCatalog.objects.firstOrNull { it.type == dev.openpolaris.core.astro.ObjectType.PLANET && it.designation == p.planet.name }
                                if (obj != null) vm.slewToObject(obj) else {
                                    // Fall back: bypass catalog, build ephemeral object.
                                    vm.slewToObject(
                                        dev.openpolaris.core.astro.AstroObject(
                                            designation = p.planet.name,
                                            name = p.planet.name.lowercase().replaceFirstChar { it.uppercase() },
                                            type = dev.openpolaris.core.astro.ObjectType.PLANET,
                                            raDeg = p.raDeg, decDeg = p.decDeg,
                                        )
                                    )
                                }
                            })
                    }
                }
            }

            // -- Comets -----------------------------------------------------
            SectionCard("Comets") {
                if (summary.comets.isEmpty()) {
                    Text("No comets above the horizon.", style = MaterialTheme.typography.bodySmall)
                } else {
                    summary.comets.forEach { c ->
                        KeyValueRow(
                            label = "${c.designation}  ${c.name}".trim(),
                            value = "Alt ${c.altitudeDeg.format2()}°  Az ${c.azimuthDeg.format2()}°" +
                                (c.magnitudeApprox?.let { "  mag ${"%.1f".format(it)}" } ?: ""),
                        )
                        TextButton(onClick = {
                            vm.slewToObject(
                                dev.openpolaris.core.astro.AstroObject(
                                    designation = c.designation,
                                    name = c.name,
                                    type = dev.openpolaris.core.astro.ObjectType.COMET,
                                    raDeg = c.raDeg, decDeg = c.decDeg,
                                )
                            )
                        }) { Text("Slew") }
                    }
                }
            }

            // -- Up targets (brightest catalogue objects above horizon) -----
            SectionCard("Up targets (above ${vm.tonightCatalog.objects.size} in catalog)") {
                if (summary.upTargets.isEmpty()) {
                    Text("Nothing from the catalog is above the horizon right now.",
                        style = MaterialTheme.typography.bodySmall)
                } else {
                    summary.upTargets.forEach { t ->
                        KeyValueRow(
                            label = "${t.designation}  ${t.name ?: ""}".trim(),
                            value = "${t.type.name.lowercase().replace('_', ' ')}" +
                                "  Alt ${t.altitudeDeg.format2()}°  Az ${t.azimuthDeg.format2()}°" +
                                (t.magnitude?.let { "  mag ${"%.1f".format(it)}" } ?: "") +
                                (t.constellation?.let { "  in $it" } ?: ""),
                        )
                        TextButton(onClick = {
                            val obj = vm.tonightCatalog.objects.firstOrNull { it.designation == t.designation }
                            if (obj != null) vm.slewToObject(obj) else {
                                vm.slewToObject(
                                    dev.openpolaris.core.astro.AstroObject(
                                        designation = t.designation,
                                        name = t.name,
                                        type = t.type,
                                        raDeg = t.raDeg, decDeg = t.decDeg,
                                    )
                                )
                            }
                        }) { Text("Slew") }
                    }
                }
            }

            // -- Catalog search ---------------------------------------------
            SectionCard("Find in catalog") {
                var q by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = q,
                    onValueChange = { q = it },
                    label = { Text("Name or designation (M, NGC, …)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                val matches: List<AstroObject> = if (q.length < 2) emptyList() else
                    vm.tonightCatalog.objects.filter { o ->
                        o.designation.contains(q, ignoreCase = true) ||
                            (o.name?.contains(q, ignoreCase = true) == true)
                    }.take(15)
                if (q.length >= 2 && matches.isEmpty()) {
                    Text("No match in the bundled catalog.", style = MaterialTheme.typography.bodySmall)
                }
                matches.forEach { o ->
                    KeyValueRow(
                        label = "${o.designation}  ${o.name ?: ""}".trim(),
                        value = "${o.type.name.lowercase().replace('_', ' ')}  " +
                            "RA ${AstroMath.formatRaHours(o.raDeg)}  Dec ${AstroMath.formatDecDMS(o.decDeg)}",
                    )
                    TextButton(onClick = { vm.slewToObject(o) }) { Text("Slew") }
                }
            }
        }
    }
}

/** Inner card with a title. */
@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

/** "Label  value" row. */
@Composable
private fun KeyValueRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PlanetRow(
    planet: Planet,
    altDeg: Double, azDeg: Double,
    raDeg: Double, decDeg: Double,
    mag: Double?,
    onSlew: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                planet.name.lowercase().replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Alt ${altDeg.format2()}°  Az ${azDeg.format2()}°" +
                    (mag?.let { "  mag ${"%.1f".format(it)}" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        TextButton(onClick = onSlew) { Text("Slew") }
    }
    Text(
        "RA ${AstroMath.formatRaHours(raDeg)}  Dec ${AstroMath.formatDecDMS(decDeg)}",
        style = MaterialTheme.typography.bodySmall,
    )
}

/**
 * Pretty-print a JD event time relative to now. The Tonight summary is
 * for "right now", so we show "in 2h 14m" / "in 32m" / "12m ago" for
 * upcoming/past events, or [fallback] if the event is null/circumpolar.
 */
private fun jdToClock(jd: Double?, isSunUp: Boolean, fallback: String): String {
    if (jd == null) return fallback
    val now = AstroMath.julianDateNow()
    val deltaSec = (jd - now) * 86400.0
    val sign = if (deltaSec >= 0) "in " else ""
    val suffix = if (deltaSec < 0) " ago" else ""
    val secs = kotlin.math.abs(deltaSec).roundToInt()
    val h = secs / 3600
    val m = (secs % 3600) / 60
    val mm = m.toString().padStart(2, '0')
    return when {
        h > 0 -> "${sign}${h}h ${mm}m${suffix}"
        else -> "${sign}${m}m${suffix}"
    }
    // Suppress unused isSunUp warning for now; the value is implicit in the JD being null for circumpolar.
    @Suppress("UNUSED_EXPRESSION") isSunUp
}
