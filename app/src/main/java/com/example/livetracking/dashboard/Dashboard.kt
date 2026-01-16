package com.example.livetracking.dashboard

import android.Manifest
import android.R.attr.delay
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.postDelayed
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.work.*
import com.example.livetracking.R
import com.example.livetracking.foreground_services.LocationForegroundService
import com.example.livetracking.utils.NetworkUtils
import com.example.livetracking.utils.SyncWorker
import com.example.livetracking.view_models.MainViewModel
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class Dashboard : AppCompatActivity() {

    companion object {
        private const val TAG = "Dashboard"
        private const val LOCATION_PERMISSION_REQ = 101
        private const val LOCATION_SETTINGS_REQ = 102
    }
    private lateinit var tvTrackingStatus: TextView
    private var isTracking = false
    private var isNetworkCallbackRegistered = false
    private lateinit var viewModel: MainViewModel
    private lateinit var tvSynced: TextView
    private lateinit var locationManager: LocationManager
    private lateinit var locationListener: LocationListener

    private lateinit var tvPending: TextView
    private lateinit var tvNetworkStatus: TextView
    private lateinit var tvLastUpdate: TextView
    private lateinit var imgIndicator: ImageView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button

    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var networkCallback: ConnectivityManager.NetworkCallback


    private val handler = Handler(Looper.getMainLooper())

    private lateinit var networkRunnable: Runnable


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")

        enableEdgeToEdge()
        setContentView(R.layout.activity_dashboard)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            insets
        }

        initViews()
        setupViewModel()
        setupClicks()
        observeNetwork()
        schedulePeriodicSync()
        lifecycleScope.launch {
            while (true) {
                if (isNetworkAvailable()) {
                    triggerImmediateSync()
                }
                delay(10_000)
            }
        }

        if (isLocationServiceRunning()) {
            updateTrackingUI(true)
        } else {
            updateTrackingUI(false)
        }
        updateNetworkUI(NetworkUtils.isOnline(this))
    }
    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }


    private fun initViews() {
        tvPending = findViewById(R.id.tvPending)
        tvSynced = findViewById(R.id.tvSynced)
        tvTrackingStatus = findViewById(R.id.tvTrackingStatus)
        tvNetworkStatus = findViewById(R.id.tvNetworkStatus)
        tvLastUpdate = findViewById(R.id.tvLastUpdate)
        imgIndicator = findViewById(R.id.img_online_indicator)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        updateTrackingUI(false)

    }
    private fun updateTrackingUI(started: Boolean) {
        isTracking = started

        if (started) {
            tvTrackingStatus.text = "Tracking: STARTED"
            tvTrackingStatus.setTextColor(
                getColor(android.R.color.holo_green_dark)
            )
        } else {
            tvTrackingStatus.text = "Tracking: STOPPED"
            tvTrackingStatus.setTextColor(
                getColor(android.R.color.holo_red_dark)
            )
        }
    }


    private fun setupViewModel() {
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]




        viewModel.pendingCount.observe(this) {
            tvPending.text = "Pending Offline Logs: $it"
            Log.e("syncFeature",""+"Pending Offline Logs: $it")
        }

        viewModel.syncedCount.observe(this) {
            tvSynced.text = "Synced Logs: $it"
            Log.e("syncFeature",""+"Synced Logs: $it")
        }

        viewModel.lastSyncedId.observe(this) { id ->
            if (id != null) {
                Log.e("syncFeature",""+ "Last Synced ID: $id")
                tvLastUpdate.text = "Last Synced ID: $id"
            }
        }

    }

    private fun setupClicks() {
        btnStart.setOnClickListener @androidx.annotation.RequiresPermission(allOf = [android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION]) {

            if (!hasLocationPermission()) {
                requestLocationPermission()
                return@setOnClickListener
            }

            checkAndEnableLocation()
        }

        btnStop.setOnClickListener {
            stopService(Intent(this, LocationForegroundService::class.java))
            updateTrackingUI(false)
            Toast.makeText(this, "Tracking stopped", Toast.LENGTH_SHORT).show()
        }
    }


    private fun hasLocationPermission(): Boolean =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    private fun requestLocationPermission() {
        requestPermissions(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            LOCATION_PERMISSION_REQ
        )
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == LOCATION_PERMISSION_REQ &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            checkAndEnableLocation()
        } else {
            Toast.makeText(this, "Location permission required", Toast.LENGTH_LONG).show()
        }
    }



    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])




    private fun checkAndEnableLocation() {

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 10_000
        ).build()

        val settingsRequest = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .setAlwaysShow(true)
            .build()

        val client = LocationServices.getSettingsClient(this)

        client.checkLocationSettings(settingsRequest)
            .addOnSuccessListener {
                startTrackingService()
            }
            .addOnFailureListener { ex ->
                if (ex is ResolvableApiException) {
                    ex.startResolutionForResult(this, LOCATION_SETTINGS_REQ)
                } else {
                    Toast.makeText(
                        this,
                        "Location services not available",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == LOCATION_SETTINGS_REQ && resultCode == RESULT_OK) {
            startTrackingService()
        }
    }

    private fun startTrackingService() {
        startService(Intent(this, LocationForegroundService::class.java))

        updateTrackingUI(true)
        Toast.makeText(this, "Tracking started", Toast.LENGTH_SHORT).show()
    }


    private fun observeNetwork() {

        if (isNetworkCallbackRegistered) return

        connectivityManager =
            getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

        networkCallback = object : ConnectivityManager.NetworkCallback() {

            override fun onAvailable(network: Network) {
                runOnUiThread {
                    updateNetworkUI(true)
                }
                triggerImmediateSync()
            }

            override fun onLost(network: Network) {
                runOnUiThread {
                    updateNetworkUI(false)
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
            isNetworkCallbackRegistered = true
        }
    }



    private fun startNetworkCheckEvery10Sec() {

        networkRunnable = object : Runnable {
            override fun run() {

                val isConnected = isNetworkAvailable()
                updateNetworkUI(isConnected)

                if (isConnected) {
                    triggerImmediateSync()
                }

                handler.postDelayed(this, 10_000)
            }
        }

        handler.post(networkRunnable)
    }


    private fun updateNetworkUI(isOnline: Boolean) {
        if (isOnline) {
            tvNetworkStatus.text = "Online"
            imgIndicator.setImageResource(R.drawable.green_dot)
        } else {
            tvNetworkStatus.text = "Offline"
            imgIndicator.setImageResource(R.drawable.red_dot)
        }
    }


    private fun schedulePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val work =
            PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(
                "LOCATION_SYNC_WORK",
                ExistingPeriodicWorkPolicy.KEEP,
                work
            )
    }

private fun triggerImmediateSync() {

    stopService(Intent(this, LocationForegroundService::class.java))

    WorkManager.getInstance(this)
        .enqueueUniqueWork(
            "IMMEDIATE_SYNC",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<SyncWorker>().build()
        )
}




    private fun isLocationServiceRunning(): Boolean {
        val manager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager

        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (LocationForegroundService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }


    override fun onDestroy() {
        super.onDestroy()

        if (::locationManager.isInitialized) {
            locationManager.removeUpdates(locationListener)
        }

        if (::connectivityManager.isInitialized) {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
    }
}
