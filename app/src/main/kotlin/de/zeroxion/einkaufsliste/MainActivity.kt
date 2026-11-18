package de.zeroxion.einkaufsliste

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import de.zeroxion.einkaufsliste.ui.ShoppingListScreen
import de.zeroxion.einkaufsliste.ui.ShoppingViewModel
import de.zeroxion.einkaufsliste.ui.theme.EinkaufslisteTheme

class MainActivity : ComponentActivity() {

    private val viewModel: ShoppingViewModel by viewModels { ShoppingViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            EinkaufslisteTheme {
                ShoppingListScreen(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            // "Teilen" from Gemini, Chrome, WhatsApp, ...
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!text.isNullOrBlank()) viewModel.addFromText(text, source = "Übernommen")
            }
            // Text markieren -> "Zur Einkaufsliste"
            Intent.ACTION_PROCESS_TEXT -> {
                val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
                if (!text.isNullOrBlank()) viewModel.addFromText(text, source = "Übernommen")
            }
            // einkaufsliste://add?text=Milch,Brot
            Intent.ACTION_VIEW -> {
                val uri = intent.data
                val text = uri?.getQueryParameter("text")
                    ?: uri?.getQueryParameter("items")
                    ?: uri?.getQueryParameter("item")
                if (!text.isNullOrBlank()) viewModel.addFromText(text, source = "Link")
            }
            ACTION_VOICE_ADD -> viewModel.requestVoice()
        }
    }

    companion object {
        const val ACTION_VOICE_ADD = "de.zeroxion.einkaufsliste.action.VOICE_ADD"
    }
}
