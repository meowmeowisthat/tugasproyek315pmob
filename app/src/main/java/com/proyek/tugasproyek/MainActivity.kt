package com.proyek.tugasproyek


import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.properties.TextAlignment


import android.graphics.Bitmap
import android.graphics.Canvas
import java.io.ByteArrayOutputStream
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.layout.element.Image


import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.cloudinary.utils.ObjectUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.proyek.tugasproyek.databinding.ActivityMainBinding
import de.hdodenhof.circleimageview.CircleImageView
import java.io.File
import java.text.SimpleDateFormat
import java.util.*




class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseDatabase
    private lateinit var imageUri: Uri

    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { uploadImage(it) }
        }

    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) uploadImage(imageUri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        CloudinaryHelper.init(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseDatabase.getInstance()

        if (auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        binding.btnDownloadReport.setOnClickListener {
            generatePdfReport()
        }


        setupToolbarAndDrawer()
        setupHeader()
        setupNavigationDrawer()
        setupLogoutButton()
        setupQuickAddMeal()
        loadWeeklyMealChart()
        loadTodayMealSummary()
    }

    // ================= QUICK ADD MEAL =================
    private fun setupQuickAddMeal() {
        binding.quickAddMeal.setOnClickListener {
            startActivity(Intent(this, MealActivity::class.java))
        }
    }

    // ================= TOOLBAR + DRAWER =================
    private fun setupToolbarAndDrawer() {
        setSupportActionBar(binding.includeToolbar.toolbar)

        val toggle = ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            binding.includeToolbar.toolbar,
            R.string.open_drawer,
            R.string.close_drawer
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
    }

    // ================= HEADER =================
    private fun setupHeader() {
        val header = binding.navView.getHeaderView(0)
        val imageProfile = header.findViewById<CircleImageView>(R.id.imageProfile)
        val addIcon = header.findViewById<ImageView>(R.id.ic_add)
        val tvName = header.findViewById<TextView>(R.id.tvUserName)
        val editIcon = header.findViewById<ImageView>(R.id.ic_edit_name)

        loadUserProfile(imageProfile, tvName)

        val imageClick = {
            if (checkPermissions()) showImagePickerDialog()
        }

        imageProfile.setOnClickListener { imageClick() }
        addIcon.setOnClickListener { imageClick() }

        val editName = {
            val editText = EditText(this)
            editText.setText(tvName.text)

            AlertDialog.Builder(this)
                .setTitle("Edit Nama")
                .setView(editText)
                .setPositiveButton("Simpan") { _, _ ->
                    val newName = editText.text.toString().trim()
                    if (newName.isNotEmpty()) {
                        tvName.text = newName
                        val uid = auth.currentUser?.uid ?: return@setPositiveButton
                        db.reference.child("users").child(uid).child("name").setValue(newName)
                    }
                }
                .setNegativeButton("Batal", null)
                .show()
        }

        tvName.setOnClickListener { editName() }
        editIcon.setOnClickListener { editName() }
    }


    private fun setupChart(
        chart: LineChart,
        entries: List<Entry>,
        labels: List<String>
    ) {
        val dataSet = LineDataSet(entries, "Kalori Harian")

        dataSet.apply {
            setDrawCircles(true)
            setDrawValues(false)
            lineWidth = 2f
            circleRadius = 4f
            color = resources.getColor(R.color.purple_500, null)
            setCircleColor(color)
        }

        chart.apply {
            data = LineData(dataSet)
            description.isEnabled = false
            legend.isEnabled = true

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                valueFormatter = IndexAxisValueFormatter(labels)
            }

            axisRight.isEnabled = false
            animateX(800)
            invalidate()
        }
    }

    private fun loadWeeklyMealChart() {
        val uid = auth.currentUser?.uid ?: return

        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val calendar = Calendar.getInstance()

        val dates = mutableListOf<String>()
        val labels = mutableListOf<String>()

        for (i in 6 downTo 0) {
            calendar.time = Date()
            calendar.add(Calendar.DAY_OF_YEAR, -i)

            val date = sdf.format(calendar.time)
            dates.add(date)

            val label = java.text.SimpleDateFormat("dd/MM", Locale.getDefault())
                .format(calendar.time)
            labels.add(label)
        }

        val entries = mutableListOf<Entry>()
        val chart = binding.mealChart

        var index = 0

        for (date in dates) {
            db.reference.child("users").child(uid)
                .child("meals").child(date)
                .get()
                .addOnSuccessListener { snapshot ->
                    var totalCalories = 0

                    for (mealSnap in snapshot.children) {
                        totalCalories += mealSnap.child("calorie")
                            .getValue(Int::class.java) ?: 0
                    }

                    entries.add(Entry(index.toFloat(), totalCalories.toFloat()))
                    index++

                    if (entries.size == dates.size) {
                        setupChart(chart, entries, labels)
                    }
                }
        }
    }



    // ================= NAVIGATION =================
    private fun setupNavigationDrawer() {
        binding.navView.setNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.nav_dashboard -> true
                R.id.nav_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                    true
                }
                R.id.nav_meal -> {
                    startActivity(Intent(this, MealActivity::class.java))
                    true
                }
                R.id.nav_logout -> {
                    auth.signOut()
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                    true
                }
                else -> false
            }
        }
    }

    // ================= DASHBOARD =================
    private fun loadTodayMealSummary() {
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

                    binding.tvTodayMealCount.text = "🍽 Jumlah makan: $mealCount kali"
                    binding.tvTodayCalories.text = "🔥 Total kalori: $totalCalories kkal"
                    binding.tvLastMealTime.text =
                        if (lastTime.isNotEmpty()) "⏰ Terakhir makan: $lastTime"
                        else "⏰ Terakhir makan: -"

                    updateMealStatus(mealCount)
                    binding.quickAddMeal.visibility =
                        if (mealCount == 0) View.VISIBLE else View.GONE
                }

                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
            })
    }

    private fun updateMealStatus(count: Int) {
        binding.tvMealStatus.text = when {
            count == 0 -> "Status Pola Makan: ❌ Belum Makan Hari Ini"
            count < 3 -> "Status Pola Makan: ⚠️ Belum Konsisten"
            else -> "Status Pola Makan: ✅ Konsisten"
        }
    }

    // ================= PERMISSION & IMAGE =================
    private fun checkPermissions(): Boolean {
        val list = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) list.add(Manifest.permission.CAMERA)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED
            ) list.add(Manifest.permission.READ_MEDIA_IMAGES)
        }

        if (list.isNotEmpty()) {
            requestPermissions(list.toTypedArray(), 101)
            return false
        }
        return true
    }

    private fun showImagePickerDialog() {
        AlertDialog.Builder(this)
            .setTitle("Pilih Gambar")
            .setItems(arrayOf("Kamera", "Galeri")) { _, which ->
                if (which == 0) openCamera()
                else galleryLauncher.launch("image/*")
            }
            .show()
    }

    private fun openCamera() {
        val file = File.createTempFile(
            "profile_",
            ".jpg",
            getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        )
        imageUri = FileProvider.getUriForFile(this, "$packageName.provider", file)
        cameraLauncher.launch(imageUri)
    }

    private fun loadUserProfile(img: CircleImageView, tvName: TextView) {
        val uid = auth.currentUser?.uid ?: return
        db.reference.child("users").child(uid)
            .addListenerForSingleValueEvent(object :
                com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    tvName.text = snapshot.child("name").getValue(String::class.java) ?: "User"
                    val url = snapshot.child("profileUrl").getValue(String::class.java)
                    if (!url.isNullOrEmpty())
                        Glide.with(this@MainActivity).load(url).into(img)
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
            })
    }

    private fun uploadImage(uri: Uri) {
        val input = contentResolver.openInputStream(uri) ?: return
        Thread {
            val result =
                CloudinaryHelper.cloudinary.uploader().upload(input, ObjectUtils.emptyMap())
            val url = result["secure_url"].toString()
            val uid = auth.currentUser?.uid ?: return@Thread
            db.reference.child("users").child(uid).child("profileUrl").setValue(url)
            runOnUiThread {
                val header = binding.navView.getHeaderView(0)
                val img = header.findViewById<CircleImageView>(R.id.imageProfile)
                Glide.with(this).load(url).into(img)
            }
        }.start()
    }

    private fun setupLogoutButton() {
        binding.btnLogout.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun generatePdfReport() {
        val uid = auth.currentUser?.uid ?: return

        val sdfFile = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = sdfDate.format(Date())

        val file = File(
            getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            "Laporan_Pola_Makan_${sdfFile.format(Date())}.pdf"
        )

        db.reference.child("users").child(uid)
            .child("meals").child(today)
            .get()
            .addOnSuccessListener { snapshot ->

                try {
                    val writer = PdfWriter(file)
                    val pdf = PdfDocument(writer)
                    val document = Document(pdf)

                    // ===== JUDUL =====
                    document.add(
                        Paragraph("LAPORAN POLA MAKAN")
                            .setBold()
                            .setFontSize(18f)
                            .setTextAlignment(TextAlignment.CENTER)
                    )

                    document.add(Paragraph("Tanggal: ${sdfFile.format(Date())}\n"))

                    // ===== DETAIL MAKAN =====
                    document.add(Paragraph("DETAIL MAKAN HARI INI").setBold())

                    var totalCalories = 0
                    var mealCount = 0

                    if (snapshot.exists()) {
                        for (meal in snapshot.children) {
                            val name =
                                meal.child("name").getValue(String::class.java) ?: "Tidak diketahui"
                            val calorie =
                                meal.child("calorie").getValue(Int::class.java) ?: 0
                            val time =
                                meal.child("time").getValue(String::class.java) ?: "-"

                            totalCalories += calorie
                            mealCount++

                            document.add(
                                Paragraph(
                                    "• Waktu   : $time\n" +
                                            "  Makanan : $name\n" +
                                            "  Kalori  : $calorie kkal\n"
                                )
                            )
                        }
                    } else {
                        document.add(Paragraph("Belum ada data makanan hari ini\n"))
                    }

                    // ===== GRAPH =====
                    document.add(Paragraph("\nGRAFIK KALORI MINGGUAN").setBold())

                    val bitmap = chartToBitmap(binding.mealChart)
                    val stream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)

                    val image = Image(ImageDataFactory.create(stream.toByteArray()))
                    image.setAutoScale(true)

                    document.add(image)

                    // ===== RINGKASAN =====
                    document.add(Paragraph("\nRINGKASAN").setBold())
                    document.add(Paragraph("Jumlah makan : $mealCount kali"))
                    document.add(Paragraph("Total kalori : $totalCalories kkal"))

                    val status = when {
                        mealCount == 0 -> "Belum makan"
                        mealCount < 3 -> "Belum konsisten"
                        else -> "Konsisten"
                    }

                    document.add(Paragraph("Status pola makan : $status"))

                    // ===== TUTUP =====
                    document.close()

                    Toast.makeText(this, "PDF + Grafik berhasil dibuat", Toast.LENGTH_SHORT).show()
                    openPdf(file)

                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "Gagal membuat PDF", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun openPdf(file: File) {
        val uri = FileProvider.getUriForFile(
            this,
            "$packageName.provider",
            file
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
        }

        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            Toast.makeText(
                this,
                "Tidak ada aplikasi pembuka PDF",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    private fun chartToBitmap(chart: LineChart): Bitmap {
        val bitmap = Bitmap.createBitmap(
            chart.width,
            chart.height,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        chart.draw(canvas)
        return bitmap
    }



}
