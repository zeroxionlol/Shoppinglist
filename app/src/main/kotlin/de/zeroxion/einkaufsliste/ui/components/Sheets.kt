package de.zeroxion.einkaufsliste.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import de.zeroxion.einkaufsliste.R
import de.zeroxion.einkaufsliste.data.Category
import de.zeroxion.einkaufsliste.data.ShoppingItem
import de.zeroxion.einkaufsliste.ui.theme.BrandGradient

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditItemSheet(
    item: ShoppingItem,
    onDismiss: () -> Unit,
    onSave: (name: String, quantity: String?, category: Category) -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember(item.id) { mutableStateOf(item.name) }
    var quantity by remember(item.id) { mutableStateOf(item.quantity.orEmpty()) }
    var category by remember(item.id) { mutableStateOf(item.category) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Produkt bearbeiten", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Produkt") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = quantity,
                onValueChange = { quantity = it },
                label = { Text("Menge (z. B. 2 l, 500 g, 6)") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Kategorie", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Category.entries.forEach { c ->
                    FilterChip(
                        selected = c == category,
                        onClick = { category = c },
                        label = { Text("${c.emoji}  ${c.label}") },
                        shape = CircleShape,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = c.tint().copy(alpha = 0.22f),
                        ),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.height(52.dp),
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Löschen")
                }
                Button(
                    onClick = { onSave(name, quantity, category) },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
                    Text("Speichern")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceHelpSheet(
    appFunctionsSupported: Boolean?,
    onDismiss: () -> Unit,
    onStartVoice: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(BrandGradient),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_sparkle), contentDescription = null, tint = Color.White)
                }
                Spacer(Modifier.width(14.dp))
                Text("Per Sprache & Gemini", style = MaterialTheme.typography.headlineSmall)
            }

            HelpCard(
                emoji = "🎙️",
                title = "Einfach sagen",
                text = "Tippe aufs Mikrofon und sag z. B. „Zwei Liter Milch, Brot und sechs Eier“. " +
                    "Mengen und Kategorien erkennt die App selbst.",
            )
            HelpCard(
                emoji = "✨",
                title = "Aus Gemini übernehmen",
                text = "Frag Gemini z. B. „Was brauche ich für Lasagne für 4 Personen?“. " +
                    "Dann die Antwort über „Teilen“ an „Einkaufsliste“ schicken – " +
                    "oder Text markieren und „Zur Einkaufsliste“ wählen.",
            )
            HelpCard(
                emoji = "📱",
                title = "Schnellzugriff",
                text = "Halte das App-Symbol gedrückt und wähle „Per Sprache hinzufügen“. " +
                    "Die Verknüpfung kannst du auch auf den Startbildschirm ziehen.",
            )
            HelpCard(
                emoji = "🤖",
                title = "Gemini direkt fragen",
                text = when (appFunctionsSupported) {
                    true -> "Dein Handy unterstützt Android AppFunctions. Die App meldet Gemini die Aktionen " +
                        "„Produkte hinzufügen“, „Liste abrufen“ und „Abhaken“. Sobald Google Gemini für " +
                        "eigene Apps freischaltet, reicht „Setz Milch auf meine Einkaufsliste“."
                    false -> "Direkte Gemini-Befehle brauchen Android 16 mit AppFunctions. " +
                        "Auf diesem Gerät nutzt du am besten Mikrofon oder „Teilen“."
                    null -> "Wird geprüft …"
                },
            )

            Button(
                onClick = onStartVoice,
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(top = 4.dp),
            ) {
                Icon(painterResource(R.drawable.ic_mic), contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text("Jetzt sprechen")
            }
        }
    }
}

@Composable
private fun HelpCard(emoji: String, title: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(16.dp),
    ) {
        Text(emoji, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(2.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
