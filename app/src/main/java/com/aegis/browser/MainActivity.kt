package com.aegis.browser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.aegis.browser.ui.BrowserScreen
import com.aegis.browser.ui.theme.AegisTheme
import com.aegis.browser.viewmodel.BrowserViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: BrowserViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        intent?.dataString?.let { incomingUrl ->
            if (incomingUrl.isNotBlank()) {
                viewModel.navigateToInput(incomingUrl)
            }
        }

        setContent {
            AegisTheme {
                BrowserScreen(viewModel = viewModel)
            }
        }
    }
}
