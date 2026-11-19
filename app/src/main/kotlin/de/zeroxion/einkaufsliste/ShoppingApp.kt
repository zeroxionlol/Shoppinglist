package de.zeroxion.einkaufsliste

import android.app.Application
import de.zeroxion.einkaufsliste.data.ShoppingRepository

class ShoppingApp : Application() {
    val repository: ShoppingRepository by lazy { ShoppingRepository.get(this) }

    override fun onCreate() {
        super.onCreate()
        CrashReport.install(this)
    }
}
