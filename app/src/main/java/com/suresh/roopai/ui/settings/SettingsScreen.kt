package com.suresh.roopai.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.suresh.roopai.BuildConfig
import com.suresh.roopai.data.settings.OutputQuality
import com.suresh.roopai.data.settings.ThemeMode

const val PRIVACY_NOTICE =
    "RoopAI has no accounts, no analytics, no ads and no trackers.\n\n" +
        "Offline tools (skin, face, eyes, lips, makeup, hair tint, accessories, background, filters, crop) run " +
        "entirely on this device with bundled models. Your photos never leave the phone for these.\n\n" +
        "AI edits are optional. They only work after you add your own API key and agree to the upload notice. " +
        "For each AI edit, the current photo and a text instruction are sent over HTTPS directly from this " +
        "device to the provider you selected (for example Google Gemini), under your own API key and that " +
        "provider's terms and data policy. RoopAI has no server and receives nothing.\n\n" +
        "Your API key is encrypted with a key held in the Android Keystore and is stored only on this device. " +
        "Edits and copies of the photos you open are kept in the app's private storage until you delete them " +
        "in History or clear the app's data. Saved photos go to Pictures/RoopAI without any watermark."

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var keyInput by rememberSaveable { mutableStateOf("") }
    var customModel by rememberSaveable { mutableStateOf("") }
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            val provider = state.provider

            Section("AI edits (optional)")
            Text(
                "Beard, hairstyle, age, expression and one-tap looks use a cloud image model with your own API key. Everything else works without a key or network.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            if (state.providers.size > 1) {
                Text("Provider", style = MaterialTheme.typography.titleSmall)
                state.providers.forEach { p -> Choice(p.displayName, "", p.id == provider?.id) { vm.setProvider(p.id) } }
            } else {
                Text("Provider: ${provider?.displayName.orEmpty()}", style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(12.dp))

            Text("API key", style = MaterialTheme.typography.titleSmall)
            Text(provider?.keyHelp.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            if (state.hasKey) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text("  Key stored ${state.keyHint}", modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = vm::testKey, enabled = state.keyCheck != KeyCheck.Running) { Text("Test key") }
                    TextButton(onClick = vm::removeKey) { Text("Remove") }
                    when (val c = state.keyCheck) {
                        KeyCheck.Running -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        KeyCheck.Valid -> Text("Key works", color = MaterialTheme.colorScheme.primary)
                        is KeyCheck.Failed -> Icon(Icons.Filled.ErrorOutline, contentDescription = c.message, tint = MaterialTheme.colorScheme.error)
                        KeyCheck.Idle -> Unit
                    }
                }
                (state.keyCheck as? KeyCheck.Failed)?.let { Text(it.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            } else {
                Text("No key added. AI tools show an \"Add API key\" prompt.", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it },
                label = { Text(if (state.hasKey) "Replace key" else "Paste API key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { vm.saveKey(keyInput); keyInput = "" }, enabled = keyInput.isNotBlank()) { Text("Save key") }

            Spacer(Modifier.height(16.dp))
            Text("Model", style = MaterialTheme.typography.titleSmall)
            provider?.models?.forEach { m -> Choice(m.title, m.note, state.model == m.id) { vm.setModel(m.id) } }
            val isCustom = provider != null && provider.models.none { it.id == state.model }
            if (isCustom) Choice("Custom: ${state.model}", "Entered manually", true) {}
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = customModel, onValueChange = { customModel = it },
                    label = { Text("Other model ID") }, singleLine = true, modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { vm.setModel(customModel); customModel = "" }, enabled = customModel.isNotBlank()) { Text("Use") }
            }
            Text("Use this when the provider releases a newer image model.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Allow photo upload for AI edits", style = MaterialTheme.typography.bodyLarge)
                    Text("Asked once before the first AI edit. Turn off to be asked again.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = state.settings.cloudConsent, onCheckedChange = vm::setConsent)
            }

            Section("Output quality")
            OutputQuality.entries.forEach { q -> Choice(q.title, q.note + " · AI upload ${q.uploadLongSide}px", state.settings.quality == q) { vm.setQuality(q) } }
            Text("Photos are exported at original resolution where the device's memory allows.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Section("Theme")
            ThemeMode.entries.forEach { t -> Choice(t.title, "", state.settings.theme == t) { vm.setTheme(t) } }

            Section("History")
            OutlinedButton(onClick = { confirmClear = true }) { Text("Clear history") }

            Section("Privacy")
            Text(PRIVACY_NOTICE, style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(20.dp))
            Text("RoopAI ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(32.dp))
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all history?") },
            text = { Text("All saved edit steps and the app's copies of your photos are deleted. Photos already saved to the gallery stay.") },
            confirmButton = { TextButton(onClick = { vm.clearHistory(); confirmClear = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(20.dp))
    HorizontalDivider()
    Spacer(Modifier.height(14.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Choice(title: String, note: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (note.isNotEmpty()) Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
