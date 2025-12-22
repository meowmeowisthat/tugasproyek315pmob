package com.proyek.tugasproyek

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.cloudinary.utils.ObjectUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.proyek.tugasproyek.databinding.ActivityMainBinding
import de.hdodenhof.circleimageview.CircleImageView
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseDatabase
    private lateinit var imageUri: Uri

    // Launcher untuk galeri
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { uploadImage(it) }
    }

    // Launcher untuk kamera
    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) uploadImage(imageUri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        CloudinaryHelper.init(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseDatabase.getInstance()

        // Proteksi login
        if (auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setupToolbarAndDrawer()
        setupHeader()
        setupNavigationDrawer()
        setupLogoutButton()
    }

    // =================== SETUP TOOLBAR + DRAWER ===================
    private fun setupToolbarAndDrawer() {
        val toolbar = binding.includeToolbar.toolbar
        setSupportActionBar(toolbar)

        // Drawer toggle (hamburger menu)
        val toggle = ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            toolbar,
            R.string.open_drawer,
            R.string.close_drawer
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Klik ikon toolbar (Dashboard = kembali ke MainActivity)
        toolbar.setNavigationOnClickListener {
            if (this !is MainActivity) {
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(intent)
            } else {
                binding.drawerLayout.open() // Kalau sudah di MainActivity, buka drawer saja
            }
        }
    }

    // =================== SETUP HEADER ===================
    private fun setupHeader() {
        val header = binding.navView.getHeaderView(0)
        val imageProfile = header.findViewById<CircleImageView>(R.id.imageProfile)
        val addIcon = header.findViewById<ImageView>(R.id.ic_add)
        val tvName = header.findViewById<TextView>(R.id.tvUserName)
        val editIcon = header.findViewById<ImageView>(R.id.ic_edit_name)

        loadUserProfile(imageProfile, tvName)

        val imageClickListener = {
            if (checkPermissions()) showImagePickerDialog()
        }
        imageProfile.setOnClickListener { imageClickListener() }
        addIcon.setOnClickListener { imageClickListener() }

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
                        Toast.makeText(this, "Nama berhasil diperbarui", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Batal", null)
                .show()
        }

        tvName.setOnClickListener { editName() }
        editIcon.setOnClickListener { editName() }
    }

    // =================== NAVIGATION DRAWER ===================
    private fun setupNavigationDrawer() {
        binding.navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_dashboard -> {
                    // Dashboard = MainActivity
                    if (this !is MainActivity) {
                        val intent = Intent(this, MainActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        startActivity(intent)
                    }
                    binding.drawerLayout.closeDrawers()
                    true
                }
                R.id.nav_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                    binding.drawerLayout.closeDrawers()
                    true
                }
                R.id.nav_meal -> {
                    startActivity(Intent(this, MealActivity::class.java))
                    binding.drawerLayout.closeDrawers()
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

    // =================== LOGOUT BUTTON ===================
    private fun setupLogoutButton() {
        binding.btnLogout.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    // =================== PERMISSIONS ===================
    private fun checkPermissions(): Boolean {
        val list = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            list.add(Manifest.permission.CAMERA)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED)
                list.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                list.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (list.isNotEmpty()) {
            requestPermissions(list.toTypedArray(), 101)
            return false
        }
        return true
    }

    // =================== IMAGE PICKER ===================
    private fun showImagePickerDialog() {
        val options = arrayOf("Kamera", "Galeri")
        AlertDialog.Builder(this)
            .setTitle("Pilih Gambar")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openCamera()
                    1 -> galleryLauncher.launch("image/*")
                }
            }
            .show()
    }

    private fun openCamera() {
        val file = File.createTempFile(
            "profile_",
            ".jpg",
            getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        )
        imageUri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
        cameraLauncher.launch(imageUri)
    }

    // =================== LOAD & UPLOAD PROFILE ===================
    private fun loadUserProfile(imageProfile: CircleImageView, tvName: TextView) {
        val uid = auth.currentUser?.uid ?: return
        db.reference.child("users").child(uid)
            .addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    val name = snapshot.child("name").getValue(String::class.java)
                    val url = snapshot.child("profileUrl").getValue(String::class.java)
                    tvName.text = name ?: "User"
                    if (!url.isNullOrEmpty())
                        Glide.with(this@MainActivity).load(url).into(imageProfile)
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
            })
    }

    private fun uploadImage(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: return
            Thread {
                try {
                    val result = CloudinaryHelper.cloudinary.uploader().upload(inputStream, ObjectUtils.emptyMap())
                    val url = result["secure_url"].toString()
                    val uid = auth.currentUser?.uid ?: return@Thread
                    db.reference.child("users").child(uid).child("profileUrl").setValue(url)
                    runOnUiThread {
                        val header = binding.navView.getHeaderView(0)
                        val imageProfile = header.findViewById<CircleImageView>(R.id.imageProfile)
                        Glide.with(this@MainActivity).load(url).into(imageProfile)
                        Toast.makeText(this, "Foto profil berhasil diperbarui", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    runOnUiThread {
                        Toast.makeText(this, "Gagal mengunggah gambar", Toast.LENGTH_SHORT).show()
                    }
                }
            }.start()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Gagal membuka gambar", Toast.LENGTH_SHORT).show()
        }
    }
}
