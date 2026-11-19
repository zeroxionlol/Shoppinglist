package de.zeroxion.einkaufsliste.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.zeroxion.einkaufsliste.R
import de.zeroxion.einkaufsliste.pantry.ui.ExpiredColor
import de.zeroxion.einkaufsliste.pantry.ui.PantryScreen
import de.zeroxion.einkaufsliste.pantry.ui.PantryViewModel
import de.zeroxion.einkaufsliste.pantry.ui.SoonColor

enum class AppTab { SHOPPING, PANTRY }

/** Two tabs: the shopping list and the pantry with best-before dates. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppRoot(
    tab: AppTab,
    onTabChange: (AppTab) -> Unit,
    shoppingViewModel: ShoppingViewModel,
    pantryViewModel: PantryViewModel,
    scanRequest: Int,
    onScanRequestHandled: () -> Unit,
) {
    val shopping by shoppingViewModel.uiState.collectAsStateWithLifecycle()
    val pantry by pantryViewModel.uiState.collectAsStateWithLifecycle()
    val saveableState = rememberSaveableStateHolder()
    // While typing, the keyboard covers the tab bar anyway – hide it so the input bar sits on the keyboard.
    val showBar = !WindowInsets.isImeVisible

    BackHandler(enabled = tab != AppTab.SHOPPING) { onTabChange(AppTab.SHOPPING) }

    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .then(if (showBar) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier),
        ) {
            saveableState.SaveableStateProvider(tab.name) {
                when (tab) {
                    AppTab.SHOPPING -> ShoppingListScreen(shoppingViewModel)
                    AppTab.PANTRY -> PantryScreen(pantryViewModel, scanRequest, onScanRequestHandled)
                }
            }
        }
        if (showBar) {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                NavigationBarItem(
                    selected = tab == AppTab.SHOPPING,
                    onClick = { onTabChange(AppTab.SHOPPING) },
                    icon = {
                        BadgedBox(badge = {
                            if (shopping.openCount > 0) Badge { Text(shopping.openCount.toString()) }
                        }) {
                            Icon(painterResource(R.drawable.ic_cart), contentDescription = null)
                        }
                    },
                    label = { Text("Einkaufsliste") },
                )
                NavigationBarItem(
                    selected = tab == AppTab.PANTRY,
                    onClick = { onTabChange(AppTab.PANTRY) },
                    icon = {
                        BadgedBox(badge = {
                            if (pantry.attention > 0) {
                                Badge(containerColor = if (pantry.expired > 0) ExpiredColor else SoonColor) {
                                    Text(pantry.attention.toString())
                                }
                            }
                        }) {
                            Icon(painterResource(R.drawable.ic_pantry), contentDescription = null)
                        }
                    },
                    label = { Text("Vorrat") },
                )
            }
        }
    }
}
