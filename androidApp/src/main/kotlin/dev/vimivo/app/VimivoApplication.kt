package dev.vimivo.app

import android.app.Application
import dev.vimivo.platform.android.di.androidModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class VimivoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@VimivoApplication)
            modules(androidModule)
        }
    }
}
