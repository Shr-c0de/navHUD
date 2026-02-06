package aer.app.navhud

import android.app.Application

class NavHudApplication : Application() {

    lateinit var bleManager: BleManager

    override fun onCreate() {
        super.onCreate()
        bleManager = BleManager(this)
    }
}