package com.screenusdeletus.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.fragment.app.FragmentActivity
import com.screenusdeletus.ui.theme.ScreenusDeletusTheme

/** Hosts the whole app UI. A FragmentActivity because BiometricPrompt needs one. */
class MainActivity : FragmentActivity() {

    private val pendingRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pendingRoute.value = intent.getStringExtra(EXTRA_ROUTE)
        setContent {
            ScreenusDeletusTheme {
                AppRoot(activity = this, pendingRoute = pendingRoute.value, onRouteConsumed = { pendingRoute.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_ROUTE)?.let { pendingRoute.value = it }
    }

    companion object {
        const val EXTRA_ROUTE = "route"
    }
}
