package com.example.gpsspeed

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.app.Activity

class MainActivity : Activity(), LocationListener {

    private enum class Unit(val label: String, val fromMps: Double) {
        KMH("км/ч", 3.6),
        MPH("миль/ч", 2.2369362920544),
        MS("м/с", 1.0),
        KNOTS("узлы", 1.9438444924406)
    }

    private lateinit var speedText: TextView
    private lateinit var unitText: TextView
    private lateinit var maxText: TextView
    private lateinit var accuracyText: TextView
    private lateinit var statusText: TextView
    private lateinit var unitButton: Button
    private lateinit var resetButton: Button

    private var locationManager: LocationManager? = null
    private var currentUnit = Unit.KMH
    private var maxSpeedMps = 0.0
    private var lastSpeedMps = 0.0

    private val requestCodeLocation = 1001
    private val minSpeedMps = 0.5

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        speedText = findViewById(R.id.speedText)
        unitText = findViewById(R.id.unitText)
        maxText = findViewById(R.id.maxText)
        accuracyText = findViewById(R.id.accuracyText)
        statusText = findViewById(R.id.statusText)
        unitButton = findViewById(R.id.unitButton)
        resetButton = findViewById(R.id.resetButton)

        unitButton.setOnClickListener {
            currentUnit = when (currentUnit) {
                Unit.KMH -> Unit.MPH
                Unit.MPH -> Unit.MS
                Unit.MS -> Unit.KNOTS
                Unit.KNOTS -> Unit.KMH
            }
            render()
        }
        resetButton.setOnClickListener {
            maxSpeedMps = 0.0
            render()
        }

        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        ensurePermissionAndStart()
        render()
    }

    override fun onResume() {
        super.onResume()
        if (hasLocationPermission()) startUpdates()
    }

    override fun onPause() {
        super.onPause()
        try {
            locationManager?.removeUpdates(this)
        } catch (_: SecurityException) {
        }
    }

    private fun hasLocationPermission(): Boolean {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun ensurePermissionAndStart() {
        if (hasLocationPermission()) {
            startUpdates()
        } else {
            statusText.text = "нет разрешения на геолокацию"
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                requestCodeLocation
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == requestCodeLocation) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startUpdates()
            } else {
                statusText.text = "нет разрешения на геолокацию"
            }
        }
    }

    private fun startUpdates() {
        val lm = locationManager ?: return
        if (!hasLocationPermission()) {
            statusText.text = "нет разрешения на геолокацию"
            return
        }
        val gpsOn = try {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        } catch (_: Exception) {
            false
        }
        if (!gpsOn) {
            statusText.text = "GPS выключен"
            return
        }
        statusText.text = "ждём сигнал GPS…"
        try {
            lm.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                200L,
                0f,
                this,
                Looper.getMainLooper()
            )
            val last = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            if (last != null) onLocationChanged(last)
        } catch (_: SecurityException) {
            statusText.text = "нет разрешения на геолокацию"
        }
    }

    override fun onLocationChanged(location: Location) {
        var speed = if (location.hasSpeed()) location.speed.toDouble() else 0.0
        if (speed < minSpeedMps) speed = 0.0
        lastSpeedMps = speed
        if (speed > maxSpeedMps) maxSpeedMps = speed

        val acc = if (location.hasAccuracy()) location.accuracy else Float.NaN
        if (!acc.isNaN()) {
            accuracyText.text = "точность ±${acc.toInt()} м"
        } else {
            accuracyText.text = "точность —"
        }

        val satsHint = if (location.accuracy <= 15f) "GPS ок" else "слабый сигнал"
        statusText.text = satsHint
        render()
    }

    override fun onProviderEnabled(provider: String) {
        statusText.text = "ждём сигнал GPS…"
        startUpdates()
    }

    override fun onProviderDisabled(provider: String) {
        statusText.text = "GPS выключен"
        lastSpeedMps = 0.0
        render()
    }

    private fun render() {
        val shown = lastSpeedMps * currentUnit.fromMps
        val maxShown = maxSpeedMps * currentUnit.fromMps
        speedText.text = formatSpeed(shown)
        unitText.text = currentUnit.label
        maxText.text = "макс. ${formatSpeed(maxShown)}"
    }

    private fun formatSpeed(value: Double): String {
        return if (value < 10.0) String.format("%.1f", value) else String.format("%.0f", value)
    }
}
