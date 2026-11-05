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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.*
import com.google.android.gms.tasks.CancellationTokenSource
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

class MainActivity : AppCompatActivity(), SensorEventListener, OnMapReadyCallback {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var sharedPref: SharedPreferences

    // Ekrany
    private lateinit var layoutMenu: View
    private lateinit var layoutSave: View
    private lateinit var layoutNavigate: View
    private lateinit var layoutFullscreen: View
    private lateinit var layoutHistory: View
    private lateinit var cardNavParkedCar: View

    // Zmienne do RecyclerView
    private lateinit var rvHistory: RecyclerView
    private lateinit var parkingAdapter: ParkingAdapter
    private var parkingList = mutableListOf<ParkingRecord>()

    // Przyciski
    private lateinit var btnMenuSave: Button
    private lateinit var btnMenuNavigate: Button
    private lateinit var btnSetTicket: Button
    private lateinit var btnToggleTheme: Button
    private lateinit var fabShare: com.google.android.material.floatingactionbutton.FloatingActionButton
    private lateinit var btnBackFromSave: Button
    private lateinit var btnBackFromNavigate: Button
    private lateinit var btnClearHistory: Button
    private lateinit var btnBackFromHistory: Button
    private lateinit var btnTakePhoto: Button
    private lateinit var btnAddNote: Button
    private lateinit var btnOpenGoogleMaps: Button
    private lateinit var btnCloseFullscreen: Button

    // Widoki tekstowe i obrazki
    private lateinit var tvStatusSave: TextView
    private lateinit var tvDistance: TextView
    private lateinit var tvArrow: TextView
    private lateinit var tvNavNote: TextView
    private lateinit var ivParkedCar: ImageView
    private lateinit var ivNavParkedCar: ImageView
    private lateinit var ivFullscreen: ImageView

    // Zmienne nawigacyjne
    private var googleMap: GoogleMap? = null
    private var parkedLocation: Location? = null
    private var currentLocation: Location? = null

    private var tempLat: Double? = null
    private var tempLon: Double? = null
    private var photoUri: Uri? = null
    private var currentPhotoFile: File? = null
    private var currentNote: String? = null
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
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) pobierzLokalizacje()
        else { Toast.makeText(this, "Brak uprawnień do GPS!", Toast.LENGTH_SHORT).show(); pokazEkran(layoutMenu) }
    }

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) uruchomAparat() else Toast.makeText(this, "Brak uprawnień do aparatu!", Toast.LENGTH_SHORT).show()
    }

    private val requestNotifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) pokazDialogCzasu() else Toast.makeText(this, "Brak zgody na powiadomienia!", Toast.LENGTH_SHORT).show()
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && currentPhotoFile != null) {
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

        sharedPref = getSharedPreferences("SmartParkPrefs", Context.MODE_PRIVATE)
        val savedTheme = sharedPref.getInt("themeMode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(savedTheme)

        setContentView(R.layout.activity_main)
        createNotificationChannel()

        layoutMenu = findViewById(R.id.layoutMenu)
        layoutSave = findViewById(R.id.layoutSave)
        layoutNavigate = findViewById(R.id.layoutNavigate)
        layoutFullscreen = findViewById(R.id.layoutFullscreen)
        layoutHistory = findViewById(R.id.layoutHistory)
        rvHistory = findViewById(R.id.rvHistory)

        btnMenuSave = findViewById(R.id.btnMenuSave)
        btnMenuNavigate = findViewById(R.id.btnMenuNavigate)
        btnSetTicket = findViewById(R.id.btnSetTicket)
        btnToggleTheme = findViewById(R.id.btnToggleTheme)
        fabShare = findViewById(R.id.fabShare)
        btnBackFromSave = findViewById(R.id.btnBackFromSave)
        btnBackFromNavigate = findViewById(R.id.btnBackFromNavigate)
        btnClearHistory = findViewById(R.id.btnClearHistory)
        btnBackFromHistory = findViewById(R.id.btnBackFromHistory)
        btnTakePhoto = findViewById(R.id.btnTakePhoto)
        btnAddNote = findViewById(R.id.btnAddNote)
        btnOpenGoogleMaps = findViewById(R.id.btnOpenGoogleMaps)
        btnCloseFullscreen = findViewById(R.id.btnCloseFullscreen)

        tvStatusSave = findViewById(R.id.tvStatusSave)
        tvDistance = findViewById(R.id.tvDistance)
        tvArrow = findViewById(R.id.tvArrow)
        tvNavNote = findViewById(R.id.tvNavNote)
        ivParkedCar = findViewById(R.id.ivParkedCar)
        ivNavParkedCar = findViewById(R.id.ivNavParkedCar)
        cardNavParkedCar = findViewById(R.id.cardNavParkedCar)
        ivFullscreen = findViewById(R.id.ivFullscreen)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        // KONFIGURACJA RECYCLER VIEW
        rvHistory.layoutManager = LinearLayoutManager(this)
        parkingAdapter = ParkingAdapter(parkingList,
            onClick = { record ->
                uruchomNawigacjeZHistorii(record)
            },
            onLongClick = { position ->
                pokazDialogUsuwania(position)
            }
        )
        rvHistory.adapter = parkingAdapter

        btnMenuSave.setOnClickListener {
            tempLat = null
            tempLon = null
            currentPhotoFile = null
            currentNote = null

            btnTakePhoto.visibility = View.GONE
            btnAddNote.visibility = View.GONE
            btnAddNote.text = "📝 DODAJ NOTATKĘ (NP. PIĘTRO)"
            ivParkedCar.visibility = View.GONE
            ivParkedCar.setImageBitmap(null)

            pokazEkran(layoutSave)
            sprawdzUprawnieniaIPobierz()
        }

        btnMenuNavigate.setOnClickListener { ladowanieHistorii() }
        btnSetTicket.setOnClickListener { sprawdzUprawnieniaPowiadomienIUstaw() }

        btnToggleTheme.setOnClickListener {
            val currentMode = sharedPref.getInt("themeMode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            val newMode = if (currentMode == AppCompatDelegate.MODE_NIGHT_YES) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES
            sharedPref.edit().putInt("themeMode", newMode).apply()
            AppCompatDelegate.setDefaultNightMode(newMode)
        }

        fabShare.setOnClickListener {
            parkedLocation?.let { location ->
                val mapsUrl = "https://maps.google.com/?q=${location.latitude},${location.longitude}"
                val shareMessage = "Moje auto jest zaparkowane tutaj:\n$mapsUrl"
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Lokalizacja mojego auta")
                    putExtra(Intent.EXTRA_TEXT, shareMessage)
                }
                startActivity(Intent.createChooser(shareIntent, "Udostępnij lokalizację przez:"))
            }
        }

        btnBackFromSave.setOnClickListener {
            if (tempLat != null && tempLon != null) zapiszDoHistorii()
            pokazEkran(layoutMenu)
        }

        btnBackFromNavigate.setOnClickListener {
            zatrzymajNawigacje()
            pokazEkran(layoutHistory)
        }

        btnClearHistory.setOnClickListener {
            if (parkingList.isEmpty()) {
                Toast.makeText(this, "Historia jest już pusta!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Używamy MaterialAlertDialogBuilder
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Wyczyść historię")
                .setMessage("Czy na pewno chcesz usunąć WSZYSTKIE zapisane parkowania? Tej operacji nie można cofnąć.")
                .setPositiveButton("USUŃ WSZYSTKO") { _, _ ->
                    parkingList.clear()
                    parkingAdapter.notifyDataSetChanged()
                    sharedPref.edit().remove("parking_history").apply()
                    Toast.makeText(this, "Historia została wyczyszczona", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("ANULUJ", null)
                .show()
        }

        btnBackFromHistory.setOnClickListener { pokazEkran(layoutMenu) }

        btnTakePhoto.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) uruchomAparat()
            else requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        btnAddNote.setOnClickListener { pokazDialogNotatki() }

        btnOpenGoogleMaps.setOnClickListener {
            parkedLocation?.let { location ->
                val uri = Uri.parse("google.navigation:q=${location.latitude},${location.longitude}&mode=w")
                val mapIntent = Intent(Intent.ACTION_VIEW, uri)
                mapIntent.setPackage("com.google.android.apps.maps")
                try { startActivity(mapIntent) } catch (e: Exception) { Toast.makeText(this, "Brak Map Google!", Toast.LENGTH_SHORT).show() }
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
        btnCloseFullscreen.setOnClickListener { layoutFullscreen.visibility = View.GONE }

        // OBSŁUGA SYSTEMOWEGO GESTU COFANIA
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    // 1. Jeśli otwarte jest powiększone zdjęcie, zamknij tylko zdjęcie
                    layoutFullscreen.visibility == View.VISIBLE -> {
                        layoutFullscreen.visibility = View.GONE
                    }
                    // 2. Jeśli jesteśmy w trybie zapisu, wróć do menu (i ewentualnie zapisz)
                    layoutSave.visibility == View.VISIBLE -> {
                        if (tempLat != null && tempLon != null) zapiszDoHistorii()
                        pokazEkran(layoutMenu)
                    }
                    // 3. Jeśli jesteśmy w trybie kompasu, wyłącz nawigację i wróć do historii
                    layoutNavigate.visibility == View.VISIBLE -> {
                        zatrzymajNawigacje()
                        pokazEkran(layoutHistory)
                    }
                    // 4. Jeśli przeglądamy historię, wróć do menu głównego
                    layoutHistory.visibility == View.VISIBLE -> {
                        pokazEkran(layoutMenu)
                    }
                    // 5. Jeśli jesteśmy w menu głównym, pozwól systemowi normalnie zamknąć aplikację
                    else -> {
                        isEnabled = false // Wyłącza nasze przechwytywanie
                        onBackPressedDispatcher.onBackPressed() // Zamyka aplikację
                        isEnabled = true // Włącza ponownie na przyszłość
                    }
                }
            }
        })

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let {
                    currentLocation = it
                    obliczKierunek()
                }
            }
        }
    }

    // --- FUNKCJE HISTORII (RECYCLER VIEW) ---
    private fun zapiszDoHistorii() {
        val historyStr = sharedPref.getString("parking_history", "[]")
        val array = JSONArray(historyStr)

        val obj = JSONObject()
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        obj.put("date", sdf.format(Date()))
        obj.put("lat", tempLat)
        obj.put("lon", tempLon)
        obj.put("photoPath", currentPhotoFile?.absolutePath ?: "")
        obj.put("note", currentNote ?: "")

        // Limit usunięty - aplikacja dopisze każdy nowy element do końca listy
        array.put(obj)

        sharedPref.edit().putString("parking_history", array.toString()).apply()
        Toast.makeText(this, "Zapisano w historii!", Toast.LENGTH_SHORT).show()
    }

    private fun ladowanieHistorii() {
        pokazEkran(layoutHistory)
        parkingList.clear() // Czyszczenie starej listy

        val historyStr = sharedPref.getString("parking_history", "[]")
        val array = JSONArray(historyStr)

        // Ładujemy od końca, aby najnowsze wpisy były na szczycie listy
        for (i in array.length() - 1 downTo 0) {
            val obj = array.getJSONObject(i)
            parkingList.add(ParkingRecord(
                date = obj.getString("date"),
                lat = obj.getDouble("lat"),
                lon = obj.getDouble("lon"),
                photoPath = obj.optString("photoPath", ""),
                note = obj.optString("note", "")
            ))
        }

        // Odświeżamy adapter, żeby narysował widoki
        parkingAdapter.notifyDataSetChanged()

        if (parkingList.isEmpty()) {
            Toast.makeText(this, "Brak zapisanych parkowań.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun pokazDialogUsuwania(position: Int) {
        // Używamy MaterialAlertDialogBuilder zamiast AlertDialog.Builder
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Usuń wpis")
            .setMessage("Czy na pewno chcesz trwale usunąć to parkowanie z historii?")
            .setPositiveButton("USUŃ") { _, _ ->
                parkingList.removeAt(position)
                parkingAdapter.notifyItemRemoved(position)
                zapiszZaktualizowanaHistorie()
            }
            .setNegativeButton("ANULUJ", null)
            .show()
    }

    private fun zapiszZaktualizowanaHistorie() {
        val array = JSONArray()
        // Ponieważ lista wyświetla od najnowszego, a w bazie najstarszy musi być na indeksie 0,
        // zapisujemy naszą listę z powrotem od tyłu.
        for (i in parkingList.size - 1 downTo 0) {
            val record = parkingList[i]
            val obj = JSONObject()
            obj.put("date", record.date)
            obj.put("lat", record.lat)
            obj.put("lon", record.lon)
            obj.put("photoPath", record.photoPath)
            obj.put("note", record.note)
            array.put(obj)
        }
        sharedPref.edit().putString("parking_history", array.toString()).apply()
    }

    private fun uruchomNawigacjeZHistorii(record: ParkingRecord) {
        parkedLocation = Location("").apply {
            latitude = record.lat
            longitude = record.lon
        }

        if (record.photoPath.isNotEmpty()) {
            val file = File(record.photoPath)
            currentPhotoFile = if (file.exists()) file else null
        } else {
            currentPhotoFile = null
        }

        currentNote = if (record.note.isNotEmpty()) record.note else null
        uruchomNawigacje()
    }

    // --- RESZTA FUNKCJI POMOCNICZYCH ---
    private fun pokazDialogNotatki() {
        val editText = EditText(this)
        editText.hint = "Np. Sektor zielony, miejsce 45"
        currentNote?.let { editText.setText(it) }

        val container = FrameLayout(this)
        val params = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        params.setMargins(60, 24, 60, 24)
        editText.layoutParams = params
        container.addView(editText)

        AlertDialog.Builder(this)
            .setTitle("Wpisz szczegóły")
            .setView(container)
            .setPositiveButton("ZAPISZ") { _, _ ->
                val noteText = editText.text.toString()
                if (noteText.isNotEmpty()) {
                    currentNote = noteText
                    btnAddNote.text = "📝 NOTATKA: $noteText"
                } else {
                    currentNote = null
                    btnAddNote.text = "📝 DODAJ NOTATKĘ (NP. PIĘTRO)"
                }
            }
            .setNegativeButton("ANULUJ", null)
            .show()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("parking_channel", "Przypomnienia o bilecie", NotificationManager.IMPORTANCE_HIGH)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun sprawdzUprawnieniaPowiadomienIUstaw() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) pokazDialogCzasu()
            else requestNotifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            pokazDialogCzasu()
        }
    }

    private fun pokazDialogCzasu() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_time_picker, null)
        val etHoursInput = dialogView.findViewById<EditText>(R.id.etHoursInput)
        val etMinutesInput = dialogView.findViewById<EditText>(R.id.etMinutesInput)

        dialogView.findViewById<Button>(R.id.btn15Min).setOnClickListener { etHoursInput.setText(""); etMinutesInput.setText("15") }
        dialogView.findViewById<Button>(R.id.btn30Min).setOnClickListener { etHoursInput.setText(""); etMinutesInput.setText("30") }
        dialogView.findViewById<Button>(R.id.btn60Min).setOnClickListener { etHoursInput.setText("1"); etMinutesInput.setText("0") }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Ustaw przypomnienie")
            .setView(dialogView)
            .setPositiveButton("USTAW") { _, _ ->
                val hoursStr = etHoursInput.text.toString()
                val minutesStr = etMinutesInput.text.toString()
                val hours = if (hoursStr.isNotEmpty()) hoursStr.toInt() else 0
                val minutes = if (minutesStr.isNotEmpty()) minutesStr.toInt() else 0
                val totalMinutes = (hours * 60) + minutes
                if (totalMinutes > 0) ustawAlarm(totalMinutes) else Toast.makeText(this, "Wpisz poprawny czas", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("ANULUJ", null)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun ustawAlarm(minuty: Int) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, ParkingAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val czasDoAlarmuMs = (minuty * 60 * 1000 * 0.9).toLong()
        val triggerTime = System.currentTimeMillis() + czasDoAlarmuMs
        val minutyDoAlarmu = (czasDoAlarmuMs / 1000 / 60).toInt()

        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            Toast.makeText(this, "Bilet: $minuty min. Przypomnienie za ok. $minutyDoAlarmu min", Toast.LENGTH_LONG).show()
        } catch (e: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            Toast.makeText(this, "Bilet: $minuty min. Przypomnienie za ok. $minutyDoAlarmu min", Toast.LENGTH_LONG).show()
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
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) inSampleSize *= 2
        }
        return inSampleSize
    }

    private fun pokazEkran(widocznyEkran: View) {
        val ekrany = listOf(layoutMenu, layoutSave, layoutNavigate, layoutHistory)

        for (ekran in ekrany) {
            if (ekran == widocznyEkran) {
                // Przygotuj widok jako przezroczysty, włącz go i płynnie rozjaśnij
                ekran.alpha = 0f
                ekran.visibility = View.VISIBLE
                ekran.animate()
                    .alpha(1f)
                    .setDuration(250) // Czas trwania animacji w milisekundach
                    .start()
            } else {
                // Jeśli ekran był widoczny, ukryj go
                ekran.visibility = View.GONE
            }
        }

        // Ekran pełnoekranowego zdjęcia obsługujemy osobno (bez animacji menu)
        layoutFullscreen.visibility = View.GONE
    }

    private fun sprawdzUprawnieniaIPobierz() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) pobierzLokalizacje()
        else requestLocationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
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
                    tempLat = location.latitude
                    tempLon = location.longitude

                    if (dokladnosc > 20f) tvStatusSave.text = "📍 Zaparkowano (Słaby sygnał)\nBłąd pomiaru: ±${dokladnosc.toInt()} m"
                    else tvStatusSave.text = "📍 Zaparkowano precyzyjnie!\nDokładność: ±${dokladnosc.toInt()} m"

                    btnTakePhoto.visibility = View.VISIBLE
                    btnAddNote.visibility = View.VISIBLE
                    btnBackFromSave.text = "GOTOWE - WRÓĆ DO MENU"
                } else {
                    tvStatusSave.text = "Brak sygnału. Wyjdź na zewnątrz."
                }
            }
    }

    private fun uruchomNawigacje() {
        if (parkedLocation == null) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return

        pokazEkran(layoutNavigate)

        if (currentPhotoFile != null && currentPhotoFile!!.exists()) {
            val navBitmap = wczytajZmniejszonyBitmap(currentPhotoFile!!.absolutePath, 400, 400)
            ivNavParkedCar.setImageBitmap(navBitmap)
            cardNavParkedCar.visibility = View.VISIBLE
        } else {
            cardNavParkedCar.visibility = View.GONE
        }

        // 2. Aktualizacja wbudowanej mapy Google
        googleMap?.let { map ->
            map.clear() // Usuwamy stare znaczniki z poprzedniego auta
            parkedLocation?.let { loc ->
                val carPosition = LatLng(loc.latitude, loc.longitude)
                // Dodajemy czerwoną pinezkę w miejscu auta
                map.addMarker(MarkerOptions().position(carPosition).title("Twoje Auto"))
                // Robimy płynny najazd kamery na to miejsce
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(carPosition, 18f))
            }
        }

        if (!currentNote.isNullOrEmpty()) {
            tvNavNote.text = "Notatka: $currentNote"
            tvNavNote.visibility = View.VISIBLE
        } else {
            tvNavNote.visibility = View.GONE
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1500).setMinUpdateIntervalMillis(1000).build()
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
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) System.arraycopy(event.values, 0, gravityData, 0, event.values.size)
        else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) System.arraycopy(event.values, 0, magneticData, 0, event.values.size)

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

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        // Jeśli mamy już uprawnienia do GPS, pokazujemy na mapie niebieską kropkę z Twoją pozycją
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            googleMap?.isMyLocationEnabled = true
        }
    }
}

// ==========================================
// NOWE KLASY DLA WIDOKU RECYCLER VIEW
// ==========================================

// Model danych (pojedynczy wpis z historii)
data class ParkingRecord(
    val date: String,
    val lat: Double,
    val lon: Double,
    val photoPath: String,
    val note: String
)

// Adapter zajmujący się wstrzykiwaniem danych do wyglądu pliku item_parking.xml
class ParkingAdapter(
    private val items: List<ParkingRecord>,
    private val onClick: (ParkingRecord) -> Unit,
    private val onLongClick: (Int) -> Unit
) : RecyclerView.Adapter<ParkingAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDate: TextView = view.findViewById(R.id.tvItemDate)
        val tvNote: TextView = view.findViewById(R.id.tvItemNote)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_parking, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvDate.text = item.date

        if (item.note.isNotEmpty()) {
            holder.tvNote.text = item.note
            holder.tvNote.visibility = View.VISIBLE
        } else {
            holder.tvNote.visibility = View.GONE
        }

        // Zwykłe kliknięcie włącza nawigację
        holder.itemView.setOnClickListener { onClick(item) }

        // Długie kliknięcie odpala dialog usuwania
        holder.itemView.setOnLongClickListener {
            onLongClick(position)
            true // "true" informuje system, że obsłużyliśmy długie kliknięcie
        }
    }

    override fun getItemCount() = items.size
}