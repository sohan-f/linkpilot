package dev.sohan.linkpilot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dev.sohan.linkpilot.ui.LinkPilotApp
import dev.sohan.linkpilot.vm.RouterViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: RouterViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { LinkPilotApp(viewModel) }
    }

    override fun onStart() {
        super.onStart()
        viewModel.setForeground(true)
    }

    override fun onStop() {
        viewModel.setForeground(false)
        super.onStop()
    }
}
