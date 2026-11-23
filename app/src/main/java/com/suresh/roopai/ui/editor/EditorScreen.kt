package com.suresh.roopai.ui.editor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.suresh.roopai.ui.icons.Redo
import com.suresh.roopai.ui.icons.RotateLeft
import com.suresh.roopai.ui.icons.RotateRight
import com.suresh.roopai.ui.icons.Undo
import com.suresh.roopai.ui.icons.Compare
import androidx.compose.material.icons.filled.Close
import com.suresh.roopai.ui.icons.Flip
import com.suresh.roopai.ui.icons.RestartAlt
import com.suresh.roopai.ui.icons.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.suresh.roopai.edit.Category
import com.suresh.roopai.edit.EditOp
import com.suresh.roopai.edit.EditStack
import com.suresh.roopai.edit.Needs
import com.suresh.roopai.edit.Tool
import com.suresh.roopai.engine.cloud.CloudGroup
import com.suresh.roopai.engine.cloud.CloudPreset
import com.suresh.roopai.engine.cloud.CloudPresets
import com.suresh.roopai.engine.cloud.PromptBuilder
import com.suresh.roopai.ui.common.AiBadge
import com.suresh.roopai.ui.theme.EditorTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

const val TAG_CATEGORIES = "categories"
const val TAG_TOOLS = "tools"

@Composable
fun EditorScreen(vm: EditorViewModel, onBack: () -> Unit, onOpenSettings: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.onResume() }

    // Events are consumed immediately; the snackbar runs in its own scope so that consuming the
    // event (which restarts this effect) does not cancel it.
    val scope = rememberCoroutineScope()
    LaunchedEffect(state.event) {
        when (val e = state.event) {
            is EditorEvent.Share -> {
                vm.consumeEvent()
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = e.mime
                    putExtra(Intent.EXTRA_STREAM, e.uri.toUri())
                    clipData = android.content.ClipData.newRawUri("RoopAI", e.uri.toUri())
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                try {
                    context.startActivity(Intent.createChooser(send, "Share photo"))
                } catch (_: Exception) {
                    scope.launch { snackbar.showSnackbar("No app can share this photo.") }
                }
            }
            is EditorEvent.Message -> {
                vm.consumeEvent()
                scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar(e.text)
                }
            }
            null -> Unit
        }
    }

    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.save() else vm.storagePermissionDenied()
    }
    val save = {
        val needs = vm.needsStoragePermission &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        if (needs) storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else vm.save()
    }
    val backgroundPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.setBackgroundPhoto(uri.toString())
    }

    BackHandler(enabled = state.panel != Panel.None) { vm.closePanel() }

    EditorTheme {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
        ) { pad ->
            Column(Modifier.fillMaxSize().padding(pad)) {
                TopBar(state, vm, onBack, save)
                if (state.rendering || state.busy != Busy.NONE) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp)) else Spacer(Modifier.height(2.dp))

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                        state.fatalError != null -> Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.fatalError!!, style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = onBack) { Text("Back") }
                        }
                        else -> {
                            val g = state.stack.geometry
                            val crop = if (state.isCropping) floatArrayOf(g?.cropL ?: 0f, g?.cropT ?: 0f, g?.cropR ?: 1f, g?.cropB ?: 1f) else null
                            PreviewCanvas(
                                frame = if (state.comparing) state.original else state.preview,
                                faces = state.faces,
                                selectedFace = state.stack.faceIndex,
                                // boxes are in base-image coordinates, so hide them once the frame is rotated or cropped
                                showFaces = !state.comparing && (g == null || g.isIdentity),
                                crop = if (state.comparing) null else crop,
                                cropAspect = state.cropAspect,
                                onPickFace = vm::pickFace,
                                onCropChanged = { vm.setCrop(it[0], it[1], it[2], it[3]); vm.commitSlider() },
                            )
                            if (state.comparing) Label("Original", Modifier.align(Alignment.TopCenter).padding(top = 10.dp))
                        }
                    }
                    state.cloudRunning?.let { title -> CloudProgress(title, state.cloud, vm::cancelCloud) }
                    // inside the image area so messages never cover the tool and category rows
                    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
                }

                if (!state.loading && state.fatalError == null) {
                    Banners(state, vm)
                    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.navigationBarsPadding()) {
                            ControlPanel(state, vm, onPickBackground = {
                                backgroundPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            })
                            ToolRow(state, vm)
                            CategoryBar(state.category, vm::selectCategory)
                        }
                    }
                }
            }
        }
        Dialogs(state, vm, onOpenSettings)
    }
}

@Composable
private fun TopBar(state: EditorUiState, vm: EditorViewModel, onBack: () -> Unit, onSave: () -> Unit) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        IconButton(onClick = vm::undo, enabled = state.history.canUndo && state.cloudRunning == null) { Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo") }
        IconButton(onClick = vm::redo, enabled = state.history.canRedo && state.cloudRunning == null) { Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo") }
        IconButton(onClick = vm::askReset, enabled = !state.stack.isEmpty && state.cloudRunning == null) { Icon(Icons.Filled.RestartAlt, contentDescription = "Reset all edits") }
        Spacer(Modifier.weight(1f))
        // press and hold to see the original
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (state.comparing) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                .semantics { contentDescription = "Hold to compare with original" }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        vm.setComparing(true)
                        waitForUpOrCancellation()
                        vm.setComparing(false)
                    }
                },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Compare, contentDescription = null) }
        IconButton(onClick = vm::share, enabled = state.canExport) {
            if (state.busy == Busy.SHARING) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Filled.Share, contentDescription = "Share")
        }
        IconButton(onClick = onSave, enabled = state.canExport) {
            if (state.busy == Busy.SAVING) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Filled.SaveAlt, contentDescription = "Save to gallery")
        }
    }
}

@Composable
private fun Label(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 10.dp, vertical = 4.dp),
        color = Color.White, style = MaterialTheme.typography.labelMedium,
    )
}

@Composable
private fun CloudProgress(title: String, cloud: CloudStatus, onCancel: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text("AI edit: $title", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text("${cloud.providerName} · ${cloud.model}", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f))
            Text("This usually takes 10 to 40 seconds.", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f))
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

@Composable
private fun Banners(state: EditorUiState, vm: EditorViewModel) {
    val faceCategory = Tool.of(state.category).any { it.needs == Needs.FACE }
    if (state.noFace && faceCategory) {
        Text(
            "No face detected in this photo. Face tools are unavailable; background, filters, crop and AI edits still work.",
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 16.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.bodySmall,
        )
    }
    if (state.faces.size > 1 && faceCategory) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${state.faces.size} faces:", style = MaterialTheme.typography.labelLarge)
            state.faces.forEachIndexed { i, _ ->
                FilterChip(selected = i == state.stack.faceIndex, onClick = { vm.pickFace(i) }, label = { Text("Face ${i + 1}") })
            }
        }
    }
}

// --------------------------------------------------------------- control panel

@Composable
private fun ControlPanel(state: EditorUiState, vm: EditorViewModel, onPickBackground: () -> Unit) {
    when (val panel = state.panel) {
        is Panel.ToolPanel -> ToolControls(panel.tool, state, vm, onPickBackground)
        Panel.Crop -> CropControls(state, vm)
        Panel.Rotate -> RotateControls(state, vm)
        Panel.None -> if (state.category == Category.AI_EDIT) CustomPrompt(state, vm)
    }
}

@Composable
private fun ToolControls(tool: Tool, state: EditorUiState, vm: EditorViewModel, onPickBackground: () -> Unit) {
    val op = state.stack.adjust(tool)
    val amount = op?.amount ?: 0f
    val enabled = vm.available(tool)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tool.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(96.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Slider(
                value = amount,
                onValueChange = { vm.setAmount(tool, it) },
                onValueChangeFinished = vm::commitSlider,
                valueRange = tool.minAmount..1f,
                enabled = enabled,
                modifier = Modifier.weight(1f).semantics { contentDescription = "${tool.title} intensity" },
            )
            Text("${(amount * 100).roundToInt()}", modifier = Modifier.width(40.dp).padding(start = 8.dp), style = MaterialTheme.typography.labelLarge)
            IconButton(onClick = { vm.clearTool(tool) }, enabled = op != null) { Icon(Icons.Filled.Close, contentDescription = "Remove ${tool.title}") }
        }
        if (tool.variants.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tool.variants) { v ->
                    val isPhoto = tool == Tool.BG_REPLACE && v == "photo"
                    val selected = if (isPhoto) op?.variant?.startsWith(EditStack.PHOTO_PREFIX) == true else op?.variant == v
                    FilterChip(
                        selected = selected,
                        onClick = { if (isPhoto) onPickBackground() else vm.setVariant(tool, v) },
                        label = { Text(if (isPhoto) "Choose photo" else v.replaceFirstChar { it.uppercase() }) },
                        enabled = enabled,
                    )
                }
            }
        }
        if (tool.hasColor) {
            Spacer(Modifier.height(4.dp))
            PaletteRow(tool.palette, op?.color, onPick = { vm.setColor(tool, it) })
        }
        Spacer(Modifier.height(4.dp))
    }
}

private val ASPECTS: List<Pair<String, Float?>> = listOf(
    "Free" to null, "1:1" to 1f, "4:5" to 0.8f, "3:4" to 0.75f, "9:16" to 9f / 16f, "16:9" to 16f / 9f, "4:3" to 4f / 3f,
)

@Composable
private fun CropControls(state: EditorUiState, vm: EditorViewModel) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text("Drag the corners or the frame. Pick a ratio to lock it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            items(ASPECTS) { (label, aspect) ->
                FilterChip(
                    selected = state.cropAspect == aspect && (aspect != null || state.stack.geometry?.hasCrop != true),
                    onClick = { vm.setCropAspect(aspect) },
                    label = { Text(label) },
                )
            }
            item { TextButton(onClick = vm::closePanel) { Text("Done") } }
        }
    }
}

@Composable
private fun RotateControls(state: EditorUiState, vm: EditorViewModel) {
    val g = state.stack.geometry ?: EditOp.Geometry()
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = { vm.rotate(-1) }) { Icon(Icons.AutoMirrored.Filled.RotateLeft, contentDescription = "Rotate left") }
            IconButton(onClick = { vm.rotate(1) }) { Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Rotate right") }
            IconButton(onClick = vm::flipHorizontal) { Icon(Icons.Filled.Flip, contentDescription = "Flip horizontally") }
            IconButton(onClick = vm::flipVertical) { Icon(Icons.Filled.Flip, contentDescription = "Flip vertically", modifier = Modifier.rotate(90f)) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = vm::closePanel) { Text("Done") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Straighten", style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(96.dp))
            Slider(
                value = g.straighten,
                onValueChange = vm::setStraighten,
                onValueChangeFinished = vm::commitSlider,
                valueRange = -EditorViewModel.MAX_STRAIGHTEN..EditorViewModel.MAX_STRAIGHTEN,
                modifier = Modifier.weight(1f).semantics { contentDescription = "Straighten angle" },
            )
            Text("%.1f°".format(g.straighten), modifier = Modifier.width(52.dp).padding(start = 8.dp), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun CustomPrompt(state: EditorUiState, vm: EditorViewModel) {
    var text by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        if (!state.cloud.hasKey) {
            Text("Add an API key in Settings to use AI edits.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(PromptBuilder.MAX_CUSTOM_LENGTH) },
                label = { Text("Describe one change") },
                placeholder = { Text("e.g. add round gold earrings") },
                maxLines = 3,
                modifier = Modifier.weight(1f).heightIn(max = 120.dp),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { vm.requestCustom(text) }, enabled = text.isNotBlank() && state.cloudRunning == null) { Text("Edit") }
        }
        Text("The person's identity, face shape, skin tone, lighting and background are kept.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ------------------------------------------------------------------- tool row

@Composable
private fun ToolRow(state: EditorUiState, vm: EditorViewModel) {
    val tools = Tool.of(state.category)
    val presets: List<CloudPreset> = if (state.category == Category.AI_EDIT) emptyList() else CloudPresets.of(state.category)
    if (state.category == Category.AI_EDIT) {
        // preset grid grouped by kind
        Column(Modifier.fillMaxWidth().heightIn(max = 190.dp).verticalScroll(rememberScrollState()).padding(vertical = 4.dp)) {
            CloudGroup.entries.forEach { group ->
                Text(group.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, top = 4.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(CloudPresets.of(group), key = { it.id }) { p -> PresetChip(p, state, vm) }
                }
            }
        }
        return
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag(TAG_TOOLS),
    ) {
        items(tools, key = { it.name }) { tool ->
            val active = state.stack.adjust(tool) != null
            FilterChip(
                selected = state.panel == Panel.ToolPanel(tool),
                onClick = { vm.selectTool(tool) },
                enabled = vm.available(tool),
                label = { Text(tool.title, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal) },
                leadingIcon = if (active) ({ Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)) }) else null,
            )
        }
        if (state.category == Category.ADJUST) {
            val g = state.stack.geometry
            item { FilterChip(selected = state.panel == Panel.Crop, onClick = vm::openCrop, label = { Text("Crop", fontWeight = if (g?.hasCrop == true) FontWeight.Bold else FontWeight.Normal) }) }
            item { FilterChip(selected = state.panel == Panel.Rotate, onClick = vm::openRotate, label = { Text("Rotate", fontWeight = if (g != null && !g.withoutCrop().isIdentity) FontWeight.Bold else FontWeight.Normal) }) }
        }
        items(presets, key = { it.id }) { p -> PresetChip(p, state, vm) }
    }
}

@Composable
private fun PresetChip(p: CloudPreset, state: EditorUiState, vm: EditorViewModel) {
    AssistChip(
        onClick = { vm.requestPreset(p) },
        enabled = state.cloudRunning == null,
        label = { Text(p.title) },
        trailingIcon = { AiBadge() },
    )
}

@Composable
private fun CategoryBar(selected: Category, onSelect: (Category) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 8.dp),
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh).testTag(TAG_CATEGORIES),
    ) {
        items(Category.entries, key = { it.name }) { c ->
            val isSelected = c == selected
            Column(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClickLabel = "Open ${c.title} tools") { onSelect(c) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        c.title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (c.cloudOnly) { Spacer(Modifier.width(4.dp)); AiBadge() }
                }
                Spacer(Modifier.height(4.dp))
                Box(Modifier.width(24.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent))
            }
        }
    }
}

// --------------------------------------------------------------------- dialogs

@Composable
private fun Dialogs(state: EditorUiState, vm: EditorViewModel, onOpenSettings: () -> Unit) {
    when (val d = state.dialog) {
        EditorDialog.NeedApiKey -> AlertDialog(
            onDismissRequest = vm::dismissDialog,
            title = { Text("Add API key") },
            text = { Text("AI edits (beard, hairstyle, age, looks and free-text edits) use a cloud image model with your own API key. Add a key in Settings to turn them on. All other tools work without it.") },
            confirmButton = { TextButton(onClick = { vm.dismissDialog(); onOpenSettings() }) { Text("Open Settings") } },
            dismissButton = { TextButton(onClick = vm::dismissDialog) { Text("Not now") } },
        )
        is EditorDialog.Consent -> AlertDialog(
            onDismissRequest = vm::dismissDialog,
            title = { Text("Send photo for AI editing?") },
            text = {
                Text(
                    "To make this edit, the current photo and a text instruction are uploaded from this device directly to " +
                        "${state.cloud.providerName} using your API key. The provider processes it under its own terms and data policy. " +
                        "RoopAI has no server and keeps no copy outside this phone.\n\n" +
                        "Only use photos you have the right to edit. You are asked this once; you can withdraw consent in Settings.",
                )
            },
            confirmButton = { TextButton(onClick = { vm.acceptConsent(d.pending) }) { Text("Agree and continue") } },
            dismissButton = { TextButton(onClick = vm::dismissDialog) { Text("Cancel") } },
        )
        is EditorDialog.Error -> AlertDialog(
            onDismissRequest = vm::dismissDialog,
            title = { Text("Could not finish") },
            text = { Text(d.message) },
            confirmButton = {
                if (d.offerSettings) TextButton(onClick = { vm.dismissDialog(); onOpenSettings() }) { Text("Open Settings") }
                else TextButton(onClick = vm::dismissDialog) { Text("OK") }
            },
            dismissButton = if (d.offerSettings) ({ TextButton(onClick = vm::dismissDialog) { Text("Close") } }) else null,
        )
        EditorDialog.ConfirmReset -> AlertDialog(
            onDismissRequest = vm::dismissDialog,
            title = { Text("Reset all edits?") },
            text = { Text("The photo goes back to the original. You can still undo this.") },
            confirmButton = { TextButton(onClick = vm::reset) { Text("Reset") } },
            dismissButton = { TextButton(onClick = vm::dismissDialog) { Text("Cancel") } },
        )
        null -> Unit
    }
}
