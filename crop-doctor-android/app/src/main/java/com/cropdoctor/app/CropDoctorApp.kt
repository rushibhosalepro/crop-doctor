package com.cropdoctor.app

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

class CropDoctorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        // Debug builds only. Release must use Play Integrity (see README).
        // NEVER commit the debug token printed in logcat.
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance()
        )
        Log.i(TAG, "Firebase initialized with App Check debug provider")
    }

    companion object {
        private const val TAG = "CropDoctorApp"
    }
}
