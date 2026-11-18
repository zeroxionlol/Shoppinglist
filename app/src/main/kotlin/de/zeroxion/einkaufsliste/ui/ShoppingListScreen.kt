package de.zeroxion.einkaufsliste.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.appfunctions.AppFunctionManager
import de.zeroxion.einkaufsliste.R
import de.zeroxion.einkaufsliste.data.ShoppingItem
import de.zeroxion.einkaufsliste.ui.components.CategoryHeader
import de.zeroxion.einkaufsliste.ui.components.CheckedHeader
import de.zeroxion.einkaufsliste.ui.components.EditItemSheet
import de.zeroxion.einkaufsliste.ui.components.ShoppingItemRow
import de.zeroxion.einkaufsliste.ui.components.VoiceHelpSheet
import de.zeroxion.einkaufsliste.ui.components.groupedShape
import de.zeroxion.einkaufsliste.ui.theme.BrandGradient
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ShoppingListScreen(viewModel: ShoppingViewModel) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val voiceRequested by viewModel.voiceRequested.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    var input by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<ShoppingItem?>(null) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    // ---- Speech recognition (Google / Samsung speech service of the phone) ----
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spoken.isNullOrBlank()) viewModel.addFromText(spoken, fromSpeech = true)
        }
    }
    val startVoice: () -> Unit = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Was soll auf die Liste?")
        }
        try {
            speechLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            viewModel.showMessage("Keine Spracherkennung auf dem Gerät gefunden")
        }
    }

    LaunchedEffect(voiceRequested) {
        if (voiceRequested) {
            viewModel.consumeVoiceRequest()
            startVoice()
        }
    }

    // ---- Snackbar messages with undo ----
    LaunchedEffect(Unit) {
        viewModel.messages.collect { msg ->
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = msg.text,
                actionLabel = if (msg.undo.isNotEmpty()) "Rückgängig" else null,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undo(msg.undo)
        }
    }

    // Does this phone offer Android AppFunctions (needed for direct Gemini commands)?
    val appFunctionsSupported by produceState<Boolean?>(initialValue = null) {
        value = if (Build.VERSION.SDK_INT >= 36) {
            try {
                AppFunctionManager.getInstance(context) != null
            } catch (e: Throwable) {
                false
            }
        } else {
            false
        }
    }

    val submit: () -> Unit = {
        if (input.isNotBlank()) {
            viewModel.addFromText(input)
            input = ""
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                title = { Text("Einkaufsliste", style = MaterialTheme.typography.headlineMedium) },
                actions = {
                    IconButton(onClick = { showHelp = true }) {
                        Icon(painterResource(R.drawable.ic_sparkle), contentDescription = "Gemini & Sprache")
                    }
                    IconButton(
                        onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, viewModel.shareText())
                            }
                            context.startActivity(Intent.createChooser(send, "Liste teilen"))
                        },
                        enabled = state.openCount > 0,
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = "Liste teilen")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "Mehr")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Erledigte entfernen") },
                                enabled = state.checkedCount > 0,
                                onClick = {
                                    menuOpen = false
                                    viewModel.clearChecked()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Liste leeren") },
                                enabled = !state.isEmpty,
                                onClick = {
                                    menuOpen = false
                                    viewModel.clearAll()
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            AddBar(
                value = input,
                onValueChange = { input = it },
                onSubmit = submit,
                onVoice = startVoice,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item(key = "progress") {
                ProgressCard(open = state.openCount, total = state.totalCount)
            }

            if (state.isEmpty) {
                item(key = "empty") {
                    EmptyState(
                        onQuickAdd = { viewModel.addFromText(it) },
                        onVoice = startVoice,
                    )
                }
            }

            state.groups.forEach { group ->
                item(key = "header-${group.category.name}") {
                    CategoryHeader(group.category, group.items.size, Modifier.animateItem())
                }
                itemsIndexed(group.items, key = { _, item -> item.id }) { index, item ->
                    ShoppingItemRow(
                        item = item,
                        shape = groupedShape(index, group.items.size),
                        onToggle = { viewModel.toggle(item) },
                        onEdit = { editing = item },
                        onDelete = { viewModel.delete(item) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            if (state.checked.isNotEmpty()) {
                item(key = "checked-header") {
                    CheckedHeader(
                        count = state.checked.size,
                        expanded = state.showChecked,
                        onToggle = { viewModel.toggleShowChecked() },
                        modifier = Modifier.animateItem(),
                    )
                }
                if (state.showChecked) {
                    itemsIndexed(state.checked, key = { _, item -> item.id }) { index, item ->
                        ShoppingItemRow(
                            item = item,
                            shape = groupedShape(index, state.checked.size),
                            onToggle = { viewModel.toggle(item) },
                            onEdit = { editing = item },
                            onDelete = { viewModel.delete(item) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }

    editing?.let { item ->
        EditItemSheet(
            item = item,
            onDismiss = { editing = null },
            onSave = { name, quantity, category ->
                viewModel.update(item, name, quantity, category)
                editing = null
            },
            onDelete = {
                viewModel.delete(item)
                editing = null
            },
        )
    }

    if (showHelp) {
        VoiceHelpSheet(
            appFunctionsSupported = appFunctionsSupported,
            onDismiss = { showHelp = false },
            onStartVoice = {
                showHelp = false
                scope.launch { startVoice() }
            },
        )
    }
}

@Composable
private fun ProgressCard(open: Int, total: Int) {
    val done = total - open
    val progress = if (total == 0) 0f else done.toFloat() / total
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(BrandGradient)
            .padding(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = when {
                    total == 0 -> "Bereit zum Einkaufen"
                    open == 0 -> "Alles erledigt 🎉"
                    open == 1 -> "Noch 1 Produkt"
                    else -> "Noch $open Produkte"
                },
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
            )
            Text(
                text = if (total == 0) "Tippen oder sprechen, um loszulegen" else "$done von $total im Wagen",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.25f),
                strokeCap = StrokeCap.Round,
            )
        }
        Text(
            "🛒",
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyState(onQuickAdd: (String) -> Unit, onVoice: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(onClick = onVoice),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_mic),
                contentDescription = "Sprechen",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(44.dp),
            )
        }
        Text("Deine Liste ist leer", style = MaterialTheme.typography.titleLarge)
        Text(
            "Sag zum Beispiel „Milch, Brot und sechs Eier“ –\noder teile eine Liste aus Gemini mit der App.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("🥛 Milch", "🍞 Brot", "🥚 Eier", "🍌 Bananen", "🧈 Butter").forEach { label ->
                SuggestionChip(
                    onClick = { onQuickAdd(label.substringAfter(' ')) },
                    label = { Text(label) },
                    shape = CircleShape,
                )
            }
        }
    }
}

@Composable
private fun AddBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onVoice: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text("Produkt hinzufügen …") },
                singleLine = true,
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            AnimatedContent(
                targetState = value.isBlank(),
                transitionSpec = { (scaleIn() + fadeIn()) togetherWith (scaleOut() + fadeOut()) },
                label = "action",
            ) { blank ->
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(BrandGradient)
                        .clickable { if (blank) onVoice() else onSubmit() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (blank) {
                        Icon(painterResource(R.drawable.ic_mic), contentDescription = "Sprechen", tint = Color.White)
                    } else {
                        Icon(Icons.Rounded.Add, contentDescription = "Hinzufügen", tint = Color.White)
                    }
                }
            }
        }
    }
}
