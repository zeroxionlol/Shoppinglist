package de.zeroxion.einkaufsliste.pantry.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.zeroxion.einkaufsliste.R
import de.zeroxion.einkaufsliste.pantry.data.PantryJson
import de.zeroxion.einkaufsliste.pantry.logic.euro
import de.zeroxion.einkaufsliste.ui.components.CountBadge
import de.zeroxion.einkaufsliste.ui.components.groupedShape
import de.zeroxion.einkaufsliste.ui.theme.BrandGradient
import java.time.format.TextStyle
import java.util.Locale

/** Colours for the expiry states. */
val ExpiredColor = Color(0xFFE5484D)
val SoonColor = Color(0xFFE8A317)
val LaterColor = Color(0xFF0E9F6E)

fun ExpiryStatus.color(): Color = when (this) {
    ExpiryStatus.EXPIRED -> ExpiredColor
    ExpiryStatus.SOON -> SoonColor
    ExpiryStatus.LATER -> LaterColor
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantryScreen(viewModel: PantryViewModel, scanRequest: Int, onScanRequestHandled: () -> Unit) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val editor by viewModel.editor.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    var showSettings by rememberSaveable { mutableStateOf(false) }
    /** Opens the barcode camera as soon as the editor is shown. */
    var scanOnOpen by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose { }
    }

    /** True if the request came from the settings button: then a refusal leads to the system settings. */
    var openSettingsIfDenied by remember { mutableStateOf(false) }
    val openNotificationSettings: () -> Unit = {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        )
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onNotificationPermissionChanged()
        if (!granted && openSettingsIfDenied) openNotificationSettings()
        openSettingsIfDenied = false
    }
    LaunchedEffect(Unit) {
        viewModel.askNotifications.collect {
            if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { msg ->
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = msg.text,
                actionLabel = msg.actionLabel,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) msg.action?.invoke()
        }
    }

    // Launcher shortcut "MHD scannen"
    LaunchedEffect(scanRequest) {
        if (scanRequest > 0) {
            onScanRequestHandled()
            viewModel.openEditor(null)
            scanOnOpen = true
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                title = { Text("Vorrat", style = MaterialTheme.typography.headlineMedium) },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Einstellungen")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    viewModel.openEditor(null)
                    scanOnOpen = true
                },
                icon = { Icon(painterResource(R.drawable.ic_barcode), contentDescription = null) },
                text = { Text("Scannen") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item(key = "summary") { SummaryCard(state) }

            if (state.showSearch) {
                item(key = "search") {
                    SearchField(state.query, viewModel::setQuery)
                }
            }

            if (state.total == 0) {
                item(key = "empty") {
                    EmptyPantry(
                        onScan = {
                            viewModel.openEditor(null)
                            scanOnOpen = true
                        },
                        onManual = { viewModel.openEditor(null) },
                    )
                }
            }
            if (state.noHits) {
                item(key = "nohits") {
                    Text(
                        "Kein Produkt passt zu „${state.query}“.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    )
                }
            }

            state.groups.forEach { group ->
                item(key = "header-${group.status.name}") {
                    GroupHeader(group, Modifier.animateItem())
                }
                itemsIndexed(group.rows, key = { _, row -> row.item.id }) { index, row ->
                    PantryItemRow(
                        row = row,
                        shape = groupedShape(index, group.rows.size),
                        onOpen = { viewModel.openEditor(row.item) },
                        onUsed = { viewModel.markUsed(row.item) },
                        onAddToShopping = { viewModel.addToShoppingList(row.item) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    editor?.let { draft ->
        PantryEditorSheet(
            state = draft,
            viewModel = viewModel,
            scanBarcodeOnOpen = scanOnOpen,
            onScanOnOpenHandled = { scanOnOpen = false },
        )
    }

    if (showSettings) {
        PantrySettingsSheet(
            viewModel = viewModel,
            onDismiss = { showSettings = false },
            onRequestNotifications = {
                val missingPermission = Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                if (missingPermission) {
                    openSettingsIfDenied = true
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    openNotificationSettings() // permission there, but switched off in the system
                }
            },
            onShareBackup = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, PantryJson.BACKUP_SUBJECT)
                    putExtra(Intent.EXTRA_TEXT, viewModel.exportBackup())
                }
                context.startActivity(Intent.createChooser(send, PantryJson.BACKUP_SUBJECT))
            },
        )
    }
}

@Composable
private fun SummaryCard(state: PantryUiState) {
    val today = state.today
    val weekday = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.GERMAN)
    val month = today.month.getDisplayName(TextStyle.FULL, Locale.GERMAN)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(BrandGradient)
            .padding(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = when {
                        state.total == 0 -> "Alles im Blick"
                        state.expired > 0 && state.soon > 0 -> "Bitte bald verbrauchen"
                        state.expired > 0 -> if (state.expired == 1) "1 Produkt abgelaufen" else "${state.expired} Produkte abgelaufen"
                        state.soon > 0 -> if (state.soon == 1) "1 Produkt läuft bald ab" else "${state.soon} Produkte laufen bald ab"
                        else -> "Alles frisch 🌿"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                )
                Text(
                    "$weekday, ${today.dayOfMonth}. $month",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
            if (state.total > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(state.expired, "abgelaufen", Modifier.weight(1f))
                    StatTile(state.soon, "bald fällig", Modifier.weight(1f))
                    StatTile(state.total, if (state.total == 1) "Produkt" else "Produkte", Modifier.weight(1f))
                }
            }
        }
        Text("🧊", style = MaterialTheme.typography.displaySmall, modifier = Modifier.align(Alignment.TopEnd))
    }
}

@Composable
private fun StatTile(count: Int, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = if (count > 0) 0.2f else 0.1f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(count.toString(), style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f), maxLines = 1)
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onChange,
        placeholder = { Text("Produkt suchen …") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) { Icon(Icons.Rounded.Close, contentDescription = "Suche leeren") }
            }
        },
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    )
}

@Composable
private fun GroupHeader(group: PantryGroup, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(group.status.color()))
        Spacer(Modifier.width(12.dp))
        Text(
            if (group.status == ExpiryStatus.SOON) "${group.status.title} ($SOON_DAYS Tage)" else group.status.title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        CountBadge(group.rows.size, group.status.color())
    }
}

@Composable
private fun PantryItemRow(
    row: PantryRow,
    shape: androidx.compose.ui.graphics.Shape,
    onOpen: () -> Unit,
    onUsed: () -> Unit,
    onAddToShopping: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = row.item
    val color = row.status.color()
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onOpen)
                .heightIn(min = 72.dp)
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProductThumb(item.name, item.image, Modifier.size(48.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    item.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.GERMAN).removeSuffix(".") +
                        ", " + item.date.format(PantryViewModel.GERMAN_DATE),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val price = row.price
                val best = price?.bestPrice
                val bestChain = price?.best
                if (price != null && best != null && bestChain != null) {
                    val several = price.chains.size > 1
                    Text(
                        (if (several) "ab " else "") + euro(best.price) + " bei " + bestChain.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            DaysPill(row.days, color)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onUsed, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.Check, contentDescription = "Verbraucht – entfernen", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onAddToShopping, modifier = Modifier.size(40.dp)) {
                    Icon(
                        painterResource(R.drawable.ic_cart),
                        contentDescription = if (row.onShoppingList) "Steht auf der Einkaufsliste" else "Auf die Einkaufsliste",
                        tint = if (row.onShoppingList) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun ProductThumb(name: String, image: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        RemoteImage(image, Modifier.fillMaxSize()) {
            Text(
                name.trim().take(1).uppercase().ifEmpty { "?" },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Big number + small line: "3 Tage", "1 morgen", "! seit 2 Tg.". */
@Composable
private fun DaysPill(days: Int, color: Color) {
    val (big, small) = when {
        days < 0 -> "!" to if (days == -1) "seit 1 Tag" else "seit ${-days} Tg."
        days == 0 -> "0" to "heute"
        days == 1 -> "1" to "morgen"
        else -> days.toString() to "Tage"
    }
    Column(
        modifier = Modifier
            .width(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(big, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
        Text(small, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

@Composable
private fun EmptyPantry(onScan: () -> Unit, onManual: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(onClick = onScan),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_barcode),
                contentDescription = "Scannen",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(44.dp),
            )
        }
        Text("Noch nichts im Vorrat", style = MaterialTheme.typography.titleLarge)
        Text(
            "Scanne Barcode und Mindesthaltbarkeitsdatum – die App erinnert dich rechtzeitig vor dem Ablauf.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        androidx.compose.material3.TextButton(onClick = onManual) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Ohne Kamera eintragen")
        }
        Text(
            "Umzug aus der alten MHD-App: dort Einstellungen → Exportieren → Teilen → „Zur Einkaufsliste“.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
