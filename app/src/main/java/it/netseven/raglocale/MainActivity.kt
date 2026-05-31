package it.netseven.raglocale

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dagger.hilt.android.AndroidEntryPoint
import it.netseven.raglocale.ui.AppRoot
import it.netseven.raglocale.ui.theme.RagLocaleTheme

/** Unica Activity: ospita l'intera UI Compose (single-activity, come la reference). */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RagLocaleTheme {
                AppRoot()
            }
        }
    }
}
