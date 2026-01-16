package com.example.livetracking

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp

class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        Log.d("APP_START", "MyApplication onCreate called")

        FirebaseApp.initializeApp(this)
    }
}
