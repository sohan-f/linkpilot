package dev.sohan.linkpilot

import android.app.Application
import dev.sohan.linkpilot.data.AppContainer

class LinkPilotApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
