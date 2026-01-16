package com.example.livetracking.foreground_services

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.livetracking.R
import com.example.livetracking.room.AppDatabase
import com.example.livetracking.room.dao.LocationDao
import com.example.livetracking.room.entity.LocationEntity
import com.google.android.gms.location.*
import kotlinx.coroutines.*

class LocationForegroundService : Service() {

    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var locationDao: LocationDao
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        locationDao = AppDatabase.getInstance(this).locationDao()

        startForeground(1, notification())
        requestLocationUpdates()
    }

    private fun hasPermission(): Boolean =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun requestLocationUpdates() {
        if (!hasPermission()) {
            stopSelf()
            return
        }

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            10_000
        ).build()

        fusedClient.requestLocationUpdates(
            request,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val l = result.lastLocation ?: return

            Log.d("LOCATION_CAPTURE", "Lat=${l.latitude}, Lng=${l.longitude}")

            serviceScope.launch {
                locationDao.insert(
                    LocationEntity(
                        employeeId = "EMP001",
                        latitude = l.latitude,
                        longitude = l.longitude,
                        accuracy = l.accuracy,
                        timestamp = System.currentTimeMillis(),
                        speed = l.speed
                    )
                )
            }
        }
    }

    private fun notification(): Notification {
        val channelId = "location_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Location Tracking",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Live Tracking Active")
            .setContentText("Tracking location in background")
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedClient.removeLocationUpdates(locationCallback)
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
