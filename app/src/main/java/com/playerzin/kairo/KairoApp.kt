package com.playerzin.kairo

class KairoApp : android.app.Application() {
    override fun onCreate() {
        super.onCreate()
        Diag.instalar(this)
    }
}
