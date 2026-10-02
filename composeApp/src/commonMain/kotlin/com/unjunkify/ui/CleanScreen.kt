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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.unjunkify.data.JunkSnapshot
import com.unjunkify.ui.state.UiState

@Composable
fun CleanScreen(
    viewModel: CleanViewModel,
    onRequestMediaPermission: () -> Unit = {},
) {
    val results by viewModel.results.collectAsState()
    val selected by viewModel.selected.collectAsState()
    val scanning by viewModel.scanning.collectAsState()
    val imagesDenied by viewModel.imagesPermissionDenied.collectAsState()
    val videoDenied by viewModel.videoPermissionDenied.collectAsState()
    val query by viewModel.query.collectAsState()
    val reclaimed by viewModel.lastReclaimed.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (imagesDenied || videoDenied) {
            val rationale = when {
                imagesDenied && videoDenied ->
                    "Media access denied — showing app cache only. " +
                        "Photos, videos and installers are skipped. " +
                        "Sizes are estimates."
                imagesDenied ->
                    "Photo access denied — photos are skipped; videos are still scanned. " +
                        "Sizes are estimates."
                else ->
                    "Video access denied — videos are skipped; photos are still scanned. " +
                        "Sizes are estimates."
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(rationale, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onRequestMediaPermission) {
                        Text("Grant access")
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                label = { Text("Search results") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = viewModel::scan, enabled = !scanning) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(if (scanning) "Scanning" else "Scan")
            }
        }

        if (scanning) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator()
                Spacer(Modifier.width(8.dp))
                Text("Scanning storage…")
            }
        }

        reclaimed?.let { bytes ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Reclaimed ${formatBytes(bytes)}")
                    TextButton(onClick = viewModel::dismissReclaimed) { Text("Dismiss") }
                }
            }
        }

        when (val state = results) {
            is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Run a scan to see junk.")
            }
            is UiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(state.message)
            }
            is UiState.Success -> {
                val items = state.data
                if (items.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Phone looks clean. No junk found.")
                    }
                } else {
                    if (selected.isNotEmpty()) {
                        Button(
                            onClick = { confirmDelete = true },
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Delete ${selected.size} selected")
                        }
                    }
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(items, key = { it.pathOrKey }) { item ->
                            JunkRow(
                                item = item,
                                checked = selected.contains(item.pathOrKey),
                                onToggle = { viewModel.toggleSelect(item.pathOrKey) },
                                onExempt = { viewModel.exemptItem(item) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        val state = results
        val doomed = if (state is UiState.Success) {
            state.data.filter { selected.contains(it.pathOrKey) }
        } else emptyList()
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${doomed.size} items?") },
            text = { Text("This frees about ${formatBytes(doomed.sumOf { it.estimatedBytes })}. Media files may need an extra system confirmation. Exempted items are never deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.confirmDelete(doomed)
                    confirmDelete = false
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun JunkRow(
    item: JunkSnapshot,
    checked: Boolean,
    onToggle: () -> Unit,
    onExempt: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
            Column(modifier = Modifier.weight(1f)) {
                Text(item.label, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${item.category} · ${formatBytes(item.estimatedBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            IconButton(onClick = onExempt) {
                Icon(Icons.Default.Shield, contentDescription = "Exempt")
            }
        }
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.2f GB".format(mb / 1024.0)
}
