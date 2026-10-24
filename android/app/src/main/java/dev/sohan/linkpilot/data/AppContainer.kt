package dev.sohan.linkpilot.data

import android.content.Context
import dev.sohan.linkpilot.LinkPilotApp

class AppContainer(context: Context) {
    val prefs = Prefs(context)
    private val secureStore = SecureStore(context)
    val api = TendaApi(prefs.baseUrl)
    val repository = RouterRepository(api, prefs, secureStore)

    companion object {
        fun from(context: Context): AppContainer =
            (context.applicationContext as LinkPilotApp).container
    }
}
