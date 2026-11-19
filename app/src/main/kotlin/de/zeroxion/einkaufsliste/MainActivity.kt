package de.zeroxion.einkaufsliste

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import de.zeroxion.einkaufsliste.pantry.data.PantryJson
import de.zeroxion.einkaufsliste.pantry.reminders.Reminders
import de.zeroxion.einkaufsliste.pantry.ui.PantryViewModel
import de.zeroxion.einkaufsliste.ui.AppRoot
import de.zeroxion.einkaufsliste.ui.AppTab
import de.zeroxion.einkaufsliste.ui.ShoppingViewModel
import de.zeroxion.einkaufsliste.ui.theme.EinkaufslisteTheme
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {

    private val viewModel: ShoppingViewModel by viewModels { ShoppingViewModel.Factory }
    private val pantryViewModel: PantryViewModel by viewModels { PantryViewModel.Factory }

    private val tab = mutableStateOf(AppTab.SHOPPING)

    /** Increased by the "MHD scannen" shortcut; the pantry screen then opens the camera. */
    private val scanRequest = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        savedInstanceState?.getString(KEY_TAB)?.let { name ->
            AppTab.entries.firstOrNull { it.name == name }?.let { tab.value = it }
        }
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            EinkaufslisteTheme {
                AppRoot(
                    tab = tab.value,
                    onTabChange = { tab.value = it },
                    shoppingViewModel = viewModel,
                    pantryViewModel = pantryViewModel,
                    scanRequest = scanRequest.intValue,
                    onScanRequestHandled = { scanRequest.intValue = 0 },
                )
            }
        }
        // Daily MHD check: set the alarm and catch up anything missed while the phone was off.
        val app = applicationContext
        thread(name = "mhd-check") {
            Reminders.schedule(app)
            Reminders.checkIfTimePassed(app, includeToday = true)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_TAB, tab.value.name)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            // "Teilen" from Gemini, Chrome, WhatsApp, ... – or a backup of the pantry / old MHD app
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                when {
                    text.isNullOrBlank() -> Unit
                    PantryJson.looksLikeBackup(text) -> {
                        tab.value = AppTab.PANTRY
                        pantryViewModel.importBackup(text)
                    }
                    else -> {
                        tab.value = AppTab.SHOPPING
                        viewModel.addFromText(text, source = "Übernommen")
                    }
                }
            }
            // Text markieren -> "Zur Einkaufsliste"
            Intent.ACTION_PROCESS_TEXT -> {
                val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
                if (!text.isNullOrBlank()) {
                    tab.value = AppTab.SHOPPING
                    viewModel.addFromText(text, source = "Übernommen")
                }
            }
            // einkaufsliste://add?text=Milch,Brot
            Intent.ACTION_VIEW -> {
                val uri = intent.data
                val text = uri?.getQueryParameter("text")
                    ?: uri?.getQueryParameter("items")
                    ?: uri?.getQueryParameter("item")
                if (!text.isNullOrBlank()) {
                    tab.value = AppTab.SHOPPING
                    viewModel.addFromText(text, source = "Link")
                }
            }
            ACTION_VOICE_ADD -> {
                tab.value = AppTab.SHOPPING
                viewModel.requestVoice()
            }
            ACTION_OPEN_PANTRY -> tab.value = AppTab.PANTRY
            ACTION_SCAN_PANTRY -> {
                tab.value = AppTab.PANTRY
                scanRequest.intValue += 1
            }
        }
    }

    companion object {
        const val ACTION_VOICE_ADD = "de.zeroxion.einkaufsliste.action.VOICE_ADD"
        const val ACTION_OPEN_PANTRY = "de.zeroxion.einkaufsliste.action.OPEN_PANTRY"
        const val ACTION_SCAN_PANTRY = "de.zeroxion.einkaufsliste.action.SCAN_PANTRY"
        private const val KEY_TAB = "tab"
    }
}
