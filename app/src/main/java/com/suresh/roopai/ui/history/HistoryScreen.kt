package com.suresh.roopai.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import com.suresh.roopai.ui.icons.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.suresh.roopai.data.db.EditRecord
import com.suresh.roopai.ui.common.EmptyState
import com.suresh.roopai.ui.home.LibraryViewModel
import com.suresh.roopai.ui.home.Thumb
import java.io.File
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: LibraryViewModel, thumbFile: (String) -> File, onOpen: (String) -> Unit, onBack: () -> Unit) {
    val edits by vm.edits.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { pad ->
        val list = edits
        when {
            list == null -> Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                EmptyState(Icons.Filled.History, "Nothing here yet", "Every photo you edit is listed here with its edit steps, so you can reopen and change it.")
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize().padding(pad),
            ) {
                items(list, key = { it.id }) { rec -> HistoryRow(rec, thumbFile(rec.id), { onOpen(rec.id) }, { confirmDelete = rec.id }) }
            }
        }
    }

    confirmDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete this edit?") },
            text = { Text("The saved steps and the app's copy of the photo are removed. Photos already saved to your gallery are not affected.") },
            confirmButton = { TextButton(onClick = { vm.delete(id); confirmDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun HistoryRow(rec: EditRecord, file: File, onOpen: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onOpen)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumb(file, rec.updatedAt, Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)))
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(rec.updatedAt)), style = MaterialTheme.typography.titleSmall)
            val steps = if (rec.opCount == 1) "1 edit step" else "${rec.opCount} edit steps"
            Text(
                steps + if (rec.exportedUri != null) " · saved to gallery" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete edit") }
    }
}
