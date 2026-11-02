package com.example.park

import android.Manifest
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.gms.location.*
import com.google.android.gms.tasks.CancellationTokenSource
import java.io.File

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var sharedPref: SharedPreferences

    private lateinit var layoutMenu: View
    private lateinit var layoutSave: View
    private lateinit var layoutNavigate: View
    private lateinit var layoutFullscreen: View

    private lateinit var btnMenuSave: Button
    private lateinit var btnMenuNavigate: Button
    private lateinit var btnSetTicket: Button
    private lateinit var btnBackFromSave: Button
    private lateinit var btnBackFromNavigate: Button
    private lateinit var btnTakePhoto: Button
    private lateinit var btnCloseFullscreen: Button
    private lateinit var btnOpenGoogleMaps: Button

    private lateinit var tvStatusSave: TextView
    private lateinit var tvDistance: TextView
    private lateinit var tvArrow: TextView
    private lateinit var ivParkedCar: ImageView
    private lateinit var ivNavParkedCar: ImageView
    private lateinit var ivFullscreen: ImageView

    private var parkedLocation: Location? = null
    private var currentLocation: Location? = null

    private var photoUri: Uri? = null
    private var currentPhotoFile: File? = null

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    private val gravityData = FloatArray(3)
    private val magneticData = FloatArray(3)
    private var currentAzimuth = 0f
    private lateinit var locationCallback: LocationCallback

    private val requestLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            pobierzLokalizacje()
        } else {
            Toast.makeText(this, "Brak uprawnień do GPS!", Toast.LENGTH_SHORT).show()
            pokazEkran(layoutMenu)
        }
    }

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) uruchomAparat()
        else Toast.makeText(this, "Brak uprawnień do aparatu!", Toast.LENGTH_SHORT).show()
    }

    private val requestNotifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) pokazDialogCzasu()
        else Toast.makeText(this, "Brak zgody na powiadomienia!", Toast.LENGTH_SHORT).show()
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && currentPhotoFile != null) {
            sharedPref.edit().putString("photoPath", currentPhotoFile!!.absolutePath).apply()

            val bitmap = wczytajZmniejszonyBitmap(currentPhotoFile!!.absolutePath, 800, 800)
            if (bitmap != null) {
                ivParkedCar.setImageBitmap(bitmap)
                ivParkedCar.visibility = View.VISIBLE
                btnTakePhoto.text = "📷 ZRÓB INNE ZDJĘCIE"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        createNotificationChannel()
        sharedPref = getSharedPreferences("SmartParkPrefs", Context.MODE_PRIVATE)

        layoutMenu = findViewById(R.id.layoutMenu)
        layoutSave = findViewById(R.id.layoutSave)
        layoutNavigate = findViewById(R.id.layoutNavigate)
        layoutFullscreen = findViewById(R.id.layoutFullscreen)

        btnMenuSave = findViewById(R.id.btnMenuSave)
        btnMenuNavigate = findViewById(R.id.btnMenuNavigate)
        btnSetTicket = findViewById(R.id.btnSetTicket)
        btnBackFromSave = findViewById(R.id.btnBackFromSave)
        btnBackFromNavigate = findViewById(R.id.btnBackFromNavigate)
        btnTakePhoto = findViewById(R.id.btnTakePhoto)
        btnCloseFullscreen = findViewById(R.id.btnCloseFullscreen)
        btnOpenGoogleMaps = findViewById(R.id.btnOpenGoogleMaps)

        tvStatusSave = findViewById(R.id.tvStatusSave)
        tvDistance = findViewById(R.id.tvDistance)
        tvArrow = findViewById(R.id.tvArrow)
        ivParkedCar = findViewById(R.id.ivParkedCar)
        ivNavParkedCar = findViewById(R.id.ivNavParkedCar)
        ivFullscreen = findViewById(R.id.ivFullscreen)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        wczytajZapisaneAuto()

        btnMenuSave.setOnClickListener {
            sharedPref.edit().clear().apply()

            btnTakePhoto.visibility = View.GONE
            ivParkedCar.visibility = View.GONE
            ivParkedCar.setImageBitmap(null)
            currentPhotoFile = null
            parkedLocation = null

            pokazEkran(layoutSave)
            sprawdzUprawnieniaIPobierz()
        }

        btnMenuNavigate.setOnClickListener { uruchomNawigacje() }

        btnSetTicket.setOnClickListener {
            sprawdzUprawnieniaPowiadomienIUstaw()
        }

        btnBackFromSave.setOnClickListener { pokazEkran(layoutMenu) }
        btnBackFromNavigate.setOnClickListener {
            zatrzymajNawigacje()
            pokazEkran(layoutMenu)
        }

        btnOpenGoogleMaps.setOnClickListener {
            parkedLocation?.let { location ->
                // Tworzymy zapytanie do map Google (q=Lat,Lon) z wymuszonym trybem pieszym (mode=w)
                val uri = Uri.parse("google.navigation:q=${location.latitude},${location.longitude}&mode=w")
                val mapIntent = Intent(Intent.ACTION_VIEW, uri)

                // Wymuszamy otwarcie dokładnie w aplikacji Google Maps, a nie w przeglądarce
                mapIntent.setPackage("com.google.android.apps.maps")

                try {
                    startActivity(mapIntent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Nie znaleziono aplikacji Google Maps w telefonie!", Toast.LENGTH_SHORT).show()
                }
            } ?: run {
                Toast.makeText(this, "Najpierw zapisz lokalizację!", Toast.LENGTH_SHORT).show()
            }
        }

        btnTakePhoto.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                uruchomAparat()
            } else {
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        val openFullscreenListener = View.OnClickListener {
            currentPhotoFile?.let { file ->
                if (file.exists()) {
                    val fullBitmap = BitmapFactory.decodeFile(file.absolutePath)
                    ivFullscreen.setImageBitmap(fullBitmap)
                    layoutFullscreen.visibility = View.VISIBLE
                }
            }
        }

        ivParkedCar.setOnClickListener(openFullscreenListener)
        ivNavParkedCar.setOnClickListener(openFullscreenListener)

        btnCloseFullscreen.setOnClickListener {
            layoutFullscreen.visibility = View.GONE
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let {
                    currentLocation = it
                    obliczKierunek()
                }
            }
        }
    }

    // --- FUNKCJE DO POWIADOMIEŃ I OKIENKA ---
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "parking_channel",
                "Przypomnienia o bilecie",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun sprawdzUprawnieniaPowiadomienIUstaw() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                pokazDialogCzasu()
            } else {
                requestNotifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            pokazDialogCzasu()
        }
    }

    private fun pokazDialogCzasu() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_time_picker, null)

        val etHoursInput = dialogView.findViewById<EditText>(R.id.etHoursInput)
        val etMinutesInput = dialogView.findViewById<EditText>(R.id.etMinutesInput)

        val btn15Min = dialogView.findViewById<Button>(R.id.btn15Min)
        val btn30Min = dialogView.findViewById<Button>(R.id.btn30Min)
        val btn60Min = dialogView.findViewById<Button>(R.id.btn60Min)

        btn15Min.setOnClickListener {
            etHoursInput.setText("")
            etMinutesInput.setText("15")
        }
        btn30Min.setOnClickListener {
            etHoursInput.setText("")
            etMinutesInput.setText("30")
        }
        btn60Min.setOnClickListener {
            etHoursInput.setText("1")
            etMinutesInput.setText("0")
        }

        // Budujemy dialog i zapisujemy go do zmiennej, aby zmodyfikować jego okno
        val dialog = AlertDialog.Builder(this)
            .setTitle("Ustaw przypomnienie")
            .setView(dialogView)
            .setPositiveButton("USTAW") { _, _ ->
                val hoursStr = etHoursInput.text.toString()
                val minutesStr = etMinutesInput.text.toString()

                val hours = if (hoursStr.isNotEmpty()) hoursStr.toInt() else 0
                val minutes = if (minutesStr.isNotEmpty()) minutesStr.toInt() else 0

                val totalMinutes = (hours * 60) + minutes

                if (totalMinutes > 0) {
                    ustawAlarm(totalMinutes)
                } else {
                    Toast.makeText(this, "Wpisz poprawny czas", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("ANULUJ", null)
            .create()

        // Usuwamy twarde, prostokątne tło systemowe (robimy je przezroczystym)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Wyświetlamy nasze zaokrąglone okienko
        dialog.show()
    }

    private fun ustawAlarm(minuty: Int) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, ParkingAlarmReceiver::class.java)

        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Obliczamy 90% czasu trwania biletu (w milisekundach)
        val czasDoAlarmuMs = (minuty * 60 * 1000 * 0.9).toLong()
        val triggerTime = System.currentTimeMillis() + czasDoAlarmuMs

        // Wyświetlanie zaokrąglonego czasu do alarmu w dymku
        val minutyDoAlarmu = (czasDoAlarmuMs / 1000 / 60).toInt()

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
            Toast.makeText(this, "Bilet: $minuty min. Przypomnienie za ok. $minutyDoAlarmu min", Toast.LENGTH_LONG).show()
        } catch (e: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            Toast.makeText(this, "Bilet: $minuty min. Przypomnienie za ok. $minutyDoAlarmu min", Toast.LENGTH_LONG).show()
        }
    }
    // --- KONIEC FUNKCJI POWIADOMIEŃ ---

    private fun wczytajZapisaneAuto() {
        val savedLat = sharedPref.getString("lat", null)
        val savedLon = sharedPref.getString("lon", null)
        val savedPhotoPath = sharedPref.getString("photoPath", null)

        if (savedLat != null && savedLon != null) {
            parkedLocation = Location("").apply {
                latitude = savedLat.toDouble()
                longitude = savedLon.toDouble()
            }
            if (savedPhotoPath != null) {
                val file = File(savedPhotoPath)
                if (file.exists()) currentPhotoFile = file
            }
        }
    }

    private fun uruchomAparat() {
        currentPhotoFile = File.createTempFile("parked_car_", ".jpg", getExternalFilesDir("Pictures"))
        photoUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", currentPhotoFile!!)
        takePictureLauncher.launch(photoUri)
    }

    private fun wczytajZmniejszonyBitmap(filePath: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        return BitmapFactory.Options().run {
            inJustDecodeBounds = true
            BitmapFactory.decodeFile(filePath, this)
            inSampleSize = calculateInSampleSize(this, reqWidth, reqHeight)
            inJustDecodeBounds = false
            BitmapFactory.decodeFile(filePath, this)
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun pokazEkran(widocznyEkran: View) {
        layoutMenu.visibility = View.GONE
        layoutSave.visibility = View.GONE
        layoutNavigate.visibility = View.GONE
        widocznyEkran.visibility = View.VISIBLE
    }

    private fun sprawdzUprawnieniaIPobierz() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            pobierzLokalizacje()
        } else {
            requestLocationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    private fun pobierzLokalizacje() {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Toast.makeText(this, "Włącz GPS", Toast.LENGTH_SHORT).show()
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            pokazEkran(layoutMenu)
            return
        }

        tvStatusSave.text = "Uruchamiam antenę GPS...\nTo może potrwać kilka sekund."
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
            .addOnSuccessListener { location ->
                if (location != null) {
                    val dokladnosc = location.accuracy
                    parkedLocation = location

                    sharedPref.edit().apply {
                        putString("lat", location.latitude.toString())
                        putString("lon", location.longitude.toString())
                        apply()
                    }

                    if (dokladnosc > 20f) {
                        tvStatusSave.text = "📍 Zaparkowano (Słaby sygnał)\nBłąd pomiaru: ±${dokladnosc.toInt()} m"
                    } else {
                        tvStatusSave.text = "📍 Zaparkowano precyzyjnie!\nDokładność: ±${dokladnosc.toInt()} m"
                    }

                    btnTakePhoto.visibility = View.VISIBLE
                    btnBackFromSave.text = "GOTOWE - WRÓĆ DO MENU"

                } else {
                    tvStatusSave.text = "Brak sygnału. Wyjdź na zewnątrz."
                }
            }
    }

    private fun uruchomNawigacje() {
        if (parkedLocation == null) {
            Toast.makeText(this, "Najpierw zapisz miejsce zaparkowania!", Toast.LENGTH_SHORT).show()
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Brak uprawnień do nawigacji!", Toast.LENGTH_SHORT).show()
            return
        }

        pokazEkran(layoutNavigate)

        if (currentPhotoFile != null && currentPhotoFile!!.exists()) {
            val navBitmap = wczytajZmniejszonyBitmap(currentPhotoFile!!.absolutePath, 400, 400)
            ivNavParkedCar.setImageBitmap(navBitmap)
            ivNavParkedCar.visibility = View.VISIBLE
        } else {
            ivNavParkedCar.visibility = View.GONE
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1500)
            .setMinUpdateIntervalMillis(1000)
            .build()

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, mainLooper)
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI)
    }

    private fun zatrzymajNawigacje() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        sensorManager.unregisterListener(this)
    }

    private fun obliczKierunek() {
        if (parkedLocation == null || currentLocation == null) return

        val distance = currentLocation!!.distanceTo(parkedLocation!!)
        val bearing = currentLocation!!.bearingTo(parkedLocation!!)

        tvDistance.text = "Odległość: ${distance.toInt()} m"

        var rotation = bearing - currentAzimuth
        if (rotation < 0) rotation += 360f
        tvArrow.rotation = rotation
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, gravityData, 0, event.values.size)
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, magneticData, 0, event.values.size)
        }

        val rotationMatrix = FloatArray(9)
        if (SensorManager.getRotationMatrix(rotationMatrix, null, gravityData, magneticData)) {
            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientation)
            currentAzimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
            obliczKierunek()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }
}