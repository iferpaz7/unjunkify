package com.unjunkify.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.unjunkify.ui.state.UiState
import com.unjunkify.data.episodeKey
import com.unjunkify.utils.buildHealthScoreInputs
import com.unjunkify.utils.estimateBgDrainHours

@Composable
fun HealthScreen(
    viewModel: HealthViewModel,
    onRequestUsageAccess: () -> Unit = {},
) {
    val snapshot by viewModel.snapshot.collectAsState()
    val consumers by viewModel.consumers.collectAsState()
    val dismissed by viewModel.dismissedDay().collectAsState()
    val history by viewModel.history.collectAsState()
    val storagePct by viewModel.storageUsedPct.collectAsState()
    val overheat7d by viewModel.overheat7d.collectAsState()

    LaunchedEffect(Unit) { viewModel.refresh() }

    val events = (history as? UiState.Success)?.data.orEmpty()
    // T5: real inputs — drain from the 24h delta + foreground ranker minutes,
    // storage from the T3 aggregate (null = unknown = no penalty), overheat
    // from distinct 7d days ≥40°C loaded by refresh(). No hardcoded 50.
    val inputs = buildHealthScoreInputs(
        storageUsedPctOrNull = storagePct,
        bgDrainHours = estimateBgDrainHours(
            events,
            foregroundMinutesTotal = consumers.sumOf { it.foregroundMinutes },
        ),
        overheat7d = overheat7d,
    )
    val score = viewModel.scoreFor(
        storageUsedPct = inputs.storageUsedPct,
        bgDrainHours = inputs.bgDrainHours,
        overheat7d = inputs.overheat7d,
    )
    val alert = viewModel.activeOverheatAlert(events, dismissed)

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScoreCard(score = score, onRefresh = viewModel::refresh)
        }

        alert?.let { message ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            message,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = {
                            val spike = events.filter { it.tempC >= 42f && it.charging }
                                .maxByOrNull { it.tempC }
                            viewModel.dismissAlert(
                                spike?.let { episodeKey(it.at, it.tempC) } ?: ""
                            )
                        }) { Text("Dismiss") }
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Battery", style = MaterialTheme.typography.titleMedium)
                    val snap = snapshot
                    if (snap == null) {
                        Text("Reading battery…")
                    } else {
                        BatteryLine(
                            icon = Icons.Default.BatteryChargingFull,
                            text = "${snap.levelPct}%${if (snap.charging) " · charging" else ""}",
                        )
                        BatteryLine(
                            icon = Icons.Default.Thermostat,
                            text = "%.1f°C".format(snap.tempC),
                        )
                    }
                    Text(
                        "${events.size} readings in the last 24h",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }

        item {
            Text("Top foreground time (24h, estimate)", style = MaterialTheme.typography.titleMedium)
        }

        if (consumers.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(
                            "Usage access off — battery and temperature still work. " +
                                "Grant usage access in system settings to rank apps by foreground time (estimate).",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onRequestUsageAccess) {
                            Text("Grant usage access")
                        }
                    }
                }
            }
        } else {
            items(consumers, key = { it.packageName }) { consumer ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(consumer.label, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${consumer.foregroundMinutes} min (est.)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreCard(score: Int, onRefresh: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(72.dp)) {
                CircularProgressIndicator(
                    progress = { score / 100f },
                    modifier = Modifier.size(72.dp),
                )
                Text("$score", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Phone health", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Storage, drain and heat combined.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Button(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = null)
            }
        }
    }
}

@Composable
private fun BatteryLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
