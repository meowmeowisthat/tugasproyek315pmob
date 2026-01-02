package com.proyek.tugasproyek

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.GravityCompat
import com.bumptech.glide.Glide
import com.cloudinary.utils.ObjectUtils
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Image
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.properties.TextAlignment
import com.proyek.tugasproyek.databinding.ActivityMainBinding
import de.hdodenhof.circleimageview.CircleImageView
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseDatabase

    private lateinit var imageUri: Uri

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { uploadImage(it) }
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) uploadImage(imageUri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            CloudinaryHelper.init(this)
        } catch (e: Exception) {
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseDatabase.getInstance()

        if (auth.currentUser == null) {
            goToLogin()
            return
        }

        setupNavigation()
        setupDashboardData()
        setupButtons()
    }

    private fun setupNavigation() {
        binding.btnMenuDashboard.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        binding.navView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> binding.drawerLayout.closeDrawer(GravityCompat.START)
                R.id.nav_profile -> startActivity(Intent(this, ProfileActivity::class.java))
                R.id.nav_meal -> startActivity(Intent(this, MealActivity::class.java))
                R.id.nav_statistics -> startActivity(Intent(this, StatisticsActivity::class.java))
                R.id.nav_logout -> showLogoutDialog()
            }
            if (item.itemId != R.id.nav_logout) {
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            }
            true
        }

        val headerView = binding.navView.getHeaderView(0)
        val btnClose = headerView.findViewById<ImageView>(R.id.btn_close_drawer)
        val imgProfile = headerView.findViewById<CircleImageView>(R.id.imageProfile)
        val tvName = headerView.findViewById<TextView>(R.id.tvUserName)

        btnClose.setOnClickListener { binding.drawerLayout.closeDrawer(GravityCompat.START) }

        imgProfile.setOnClickListener {
            if (checkPermissions()) showImagePickerDialog()
        }

        tvName.setOnClickListener {
            showEditNameDialog(tvName)
        }
    }

    private fun showEditNameDialog(textView: TextView) {
        val editText = EditText(this)
        editText.setText(textView.text)

        AlertDialog.Builder(this)
            .setTitle("Edit Nama")
            .setView(editText)
            .setPositiveButton("Simpan") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty()) {
                    textView.text = newName
                    binding.tvName.text = newName
                    val uid = auth.currentUser?.uid ?: return@setPositiveButton
                    db.reference.child("users").child(uid).child("name").setValue(newName)
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Apakah anda yakin ingin logout?")
            .setPositiveButton("Iya") { _, _ -> goToLogin() }
            .setNegativeButton("Batal") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun setupDashboardData() {
        loadUserData()
        loadTodaySummary()
        loadWeeklyChart()
    }

    private fun loadUserData() {
        val uid = auth.currentUser?.uid ?: return

        db.reference.child("users").child(uid)
            .addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    val name = snapshot.child("name").getValue(String::class.java) ?: "User"
                    val url = snapshot.child("profileUrl").getValue(String::class.java)

                    binding.tvName.text = name

                    if (!url.isNullOrEmpty()) {
                        binding.tvInitial.visibility = android.view.View.GONE
                        binding.ivDashboardProfile.visibility = android.view.View.VISIBLE

                        Glide.with(this@MainActivity)
                            .load(url)
                            .into(binding.ivDashboardProfile)
                    } else {
                        binding.ivDashboardProfile.visibility = android.view.View.GONE
                        binding.tvInitial.visibility = android.view.View.VISIBLE

                        if (name.isNotEmpty()) {
                            binding.tvInitial.text = name.first().toString().uppercase()
                        }
                    }

                    val headerView = binding.navView.getHeaderView(0)
                    val tvSidebarName = headerView.findViewById<TextView>(R.id.tvUserName)
                    val imgSidebarProfile = headerView.findViewById<CircleImageView>(R.id.imageProfile)

                    tvSidebarName.text = name
                    if (!url.isNullOrEmpty()) {
                        Glide.with(this@MainActivity).load(url).into(imgSidebarProfile)
                    }
                }

                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
            })
    }
    private fun loadTodaySummary() {
        val uid = auth.currentUser?.uid ?: return
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        db.reference.child("users").child(uid).child("meals").child(today)
            .addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    var totalCalories = 0
                    var mealCount = 0
                    var lastTime = ""

                    for (meal in snapshot.children) {
                        mealCount++
                        totalCalories += meal.child("calorie").getValue(Int::class.java) ?: 0
                        val time = meal.child("time").getValue(String::class.java) ?: ""
                        if (time > lastTime) lastTime = time
                    }

                    binding.tvMealsValue.text = mealCount.toString()
                    binding.tvCaloriesValue.text = totalCalories.toString()
                    binding.tvLastMealTime.text = if (lastTime.isNotEmpty()) lastTime else "--:--"

                    updateStatusChip(mealCount)
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
            })
    }

    private fun updateStatusChip(count: Int) {
        val chip = binding.tvStatusChip
        when {
            count == 0 -> {
                chip.text = "Belum Mulai"
                chip.setTextColor(Color.parseColor("#D32F2F"))
            }
            count < 3 -> {
                chip.text = "Belum Konsisten"
                chip.setTextColor(Color.parseColor("#E65100"))
            }
            else -> {
                chip.text = "Good Job!\nKonsisten"
                chip.setTextColor(Color.parseColor("#388E3C"))
            }
        }
    }

    private fun loadWeeklyChart() {
        val uid = auth.currentUser?.uid ?: return
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val calendar = Calendar.getInstance()

        val dates = mutableListOf<String>()
        val labels = mutableListOf<String>()

        for (i in 6 downTo 0) {
            calendar.time = Date()
            calendar.add(Calendar.DAY_OF_YEAR, -i)
            dates.add(sdf.format(calendar.time))
            labels.add(SimpleDateFormat("dd/MM", Locale.getDefault()).format(calendar.time))
        }

        val entries = mutableListOf<Entry>()
        val chart = binding.mealChart
        var processedCount = 0

        for ((index, date) in dates.withIndex()) {
            db.reference.child("users").child(uid).child("meals").child(date)
                .get().addOnSuccessListener { snapshot ->
                    var dailyCal = 0
                    for (meal in snapshot.children) {
                        dailyCal += meal.child("calorie").getValue(Int::class.java) ?: 0
                    }
                    entries.add(Entry(index.toFloat(), dailyCal.toFloat()))

                    processedCount++
                    if (processedCount == dates.size) {
                        entries.sortBy { it.x }
                        renderChart(chart, entries, labels)
                    }
                }
        }
    }

    private fun renderChart(chart: LineChart, entries: List<Entry>, labels: List<String>) {
        val dataSet = LineDataSet(entries, "Calories")
        dataSet.apply {
            mode = LineDataSet.Mode.CUBIC_BEZIER
            color = Color.parseColor("#4CAF50")
            setCircleColor(Color.parseColor("#4CAF50"))
            lineWidth = 3f
            circleRadius = 5f
            setDrawValues(false)
            setDrawFilled(true)
            fillColor = Color.parseColor("#C8E6C9")
        }

        chart.apply {
            data = LineData(dataSet)
            description.isEnabled = false
            legend.isEnabled = false
            axisRight.isEnabled = false
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                valueFormatter = IndexAxisValueFormatter(labels)
                granularity = 1f
                setDrawGridLines(false)
            }
            axisLeft.setDrawGridLines(false)
            animateY(1000)
            invalidate()
        }
    }

    private fun chartToBitmap(chart: LineChart): Bitmap {
        val bitmap = Bitmap.createBitmap(chart.width, chart.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        chart.draw(canvas)
        return bitmap
    }

    private fun setupButtons() {
        binding.fabAddMeal.setOnClickListener {
            startActivity(Intent(this, MealActivity::class.java))
        }

        binding.btnDownloadReport.setOnClickListener {
            generatePdfReport()
        }
    }

    private fun generatePdfReport() {
        val uid = auth.currentUser?.uid ?: return
        val sdfFile = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = sdfDate.format(Date())

        val file = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Laporan_$today.pdf")

        db.reference.child("users").child(uid).child("meals").child(today)
            .get().addOnSuccessListener { snapshot ->
                try {
                    val writer = PdfWriter(file)
                    val pdf = PdfDocument(writer)
                    val document = Document(pdf)

                    document.add(Paragraph("LAPORAN HARIAN").setBold().setFontSize(20f).setTextAlignment(TextAlignment.CENTER))
                    document.add(Paragraph("Tanggal: ${sdfFile.format(Date())}\n"))

                    document.add(Paragraph("Grafik Mingguan:").setBold())
                    val bitmap = chartToBitmap(binding.mealChart)
                    val stream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    val image = Image(ImageDataFactory.create(stream.toByteArray()))
                    image.setAutoScale(true)
                    document.add(image)

                    document.add(Paragraph("\nDetail Makanan Hari Ini:").setBold())
                    var totalCalories = 0
                    if (snapshot.exists()) {
                        for (meal in snapshot.children) {
                            val food = meal.child("food").getValue(String::class.java) ?: "-"
                            val cal = meal.child("calorie").getValue(Int::class.java) ?: 0
                            val time = meal.child("time").getValue(String::class.java) ?: "-"
                            totalCalories += cal
                            document.add(Paragraph("• $time : $food ($cal kkal)"))
                        }
                    } else {
                        document.add(Paragraph("Belum ada data makanan."))
                    }

                    document.add(Paragraph("\nTotal Kalori Hari Ini: $totalCalories kkal").setBold().setFontSize(14f))

                    document.close()
                    Toast.makeText(this, "PDF Berhasil dibuat!", Toast.LENGTH_SHORT).show()
                    openPdf(file)

                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "Gagal membuat PDF: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun openPdf(file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
        }

        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Tidak ada aplikasi pembuka PDF", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkPermissions(): Boolean {
        val list = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            list.add(Manifest.permission.CAMERA)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                list.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        }
        if (list.isNotEmpty()) {
            requestPermissions(list.toTypedArray(), 101)
            return false
        }
        return true
    }

    private fun showImagePickerDialog() {
        AlertDialog.Builder(this)
            .setTitle("Ganti Foto Profil")
            .setItems(arrayOf("Kamera", "Galeri")) { _, which ->
                if (which == 0) openCamera()
                else galleryLauncher.launch("image/*")
            }
            .show()
    }

    private fun openCamera() {
        val file = File.createTempFile("profile_", ".jpg", getExternalFilesDir(Environment.DIRECTORY_PICTURES))
        imageUri = FileProvider.getUriForFile(this, "$packageName.provider", file)
        cameraLauncher.launch(imageUri)
    }

    private fun uploadImage(uri: Uri) {
        Toast.makeText(this, "Mengupload gambar...", Toast.LENGTH_SHORT).show()
        val input = contentResolver.openInputStream(uri) ?: return
        Thread {
            try {
                val result = CloudinaryHelper.cloudinary.uploader().upload(input, ObjectUtils.emptyMap())
                val url = result["secure_url"].toString()

                val uid = auth.currentUser?.uid ?: return@Thread
                db.reference.child("users").child(uid).child("profileUrl").setValue(url)

                runOnUiThread {
                    Toast.makeText(this, "Foto berhasil diupdate!", Toast.LENGTH_SHORT).show()
                    loadUserData()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Gagal upload: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun goToLogin() {
        auth.signOut()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}