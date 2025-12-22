package com.proyek.tugasproyek

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.cloudinary.utils.ObjectUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.proyek.tugasproyek.databinding.ActivityProfileBinding
import java.io.File

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseDatabase
    private lateinit var imageUri: Uri

    // Launcher galeri
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { uploadImage(it) }
    }

    // Launcher kamera
    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) uploadImage(imageUri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseDatabase.getInstance()

        loadUserProfile()

        // Back button
        binding.ivBack.setOnClickListener { finish() }

        // Klik foto profil atau + icon
        val imageClickListener = {
            if (checkPermissions()) showImagePickerDialog()
        }
        binding.imageProfile.setOnClickListener { imageClickListener() }
        binding.icAdd.setOnClickListener { imageClickListener() }

        // Tombol edit seluruh profil
        binding.icEditProfile.setOnClickListener {
            binding.formProfile.visibility = View.VISIBLE
        }

        // Tombol hapus seluruh profil
        binding.icDeleteProfile.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Hapus Profil")
                .setMessage("Apakah kamu yakin ingin menghapus semua data profil?")
                .setPositiveButton("Ya") { _, _ -> clearProfile() }
                .setNegativeButton("Batal", null)
                .show()
        }

        // Tombol simpan
        binding.btnSaveProfile.setOnClickListener {
            saveProfile()
            binding.formProfile.visibility = View.GONE
        }
    }

    // Cek permission kamera dan storage
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

    // Dialog pilih sumber gambar
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

    // Buka kamera untuk ambil gambar
    private fun openCamera() {
        val file = File.createTempFile(
            "profile_",
            ".jpg",
            getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        )
        imageUri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
        cameraLauncher.launch(imageUri)
    }

    // Load data profil dari Firebase
    private fun loadUserProfile() {
        val uid = auth.currentUser?.uid ?: return
        db.reference.child("users").child(uid)
            .addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    val name = snapshot.child("name").getValue(String::class.java)
                    val weight = snapshot.child("weight").getValue(String::class.java)
                    val height = snapshot.child("height").getValue(String::class.java)
                    val goal = snapshot.child("goal").getValue(String::class.java)
                    val url = snapshot.child("profileUrl").getValue(String::class.java)

                    binding.etName.setText(name)
                    binding.etWeight.setText(weight)
                    binding.etHeight.setText(height)
                    binding.etGoal.setText(goal)
                    binding.tvUserName.text = name ?: "User"
                    binding.tvNameResult.text = "Nama: ${name ?: "-"}"
                    binding.tvWeightResult.text = "Berat Badan: ${weight ?: "-"}"
                    binding.tvHeightResult.text = "Tinggi Badan: ${height ?: "-"}"
                    binding.tvGoalResult.text = "Tujuan: ${goal ?: "-"}"

                    if (!url.isNullOrEmpty())
                        Glide.with(this@ProfileActivity).load(url).into(binding.imageProfile)
                    else
                        binding.imageProfile.setImageResource(R.drawable.account_svg)
                }

                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
            })
    }

    // Upload gambar ke Cloudinary
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
                        Glide.with(this@ProfileActivity).load(url).into(binding.imageProfile)
                        Toast.makeText(this, "Foto profil berhasil diperbarui", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    runOnUiThread { Toast.makeText(this, "Gagal mengunggah gambar", Toast.LENGTH_SHORT).show() }
                }
            }.start()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Gagal membuka gambar", Toast.LENGTH_SHORT).show()
        }
    }

    // Simpan profil ke Firebase
    private fun saveProfile() {
        val name = binding.etName.text.toString().trim()
        val weight = binding.etWeight.text.toString().trim()
        val height = binding.etHeight.text.toString().trim()
        val goal = binding.etGoal.text.toString().trim()

        if (name.isEmpty()) {
            Toast.makeText(this, "Nama tidak boleh kosong", Toast.LENGTH_SHORT).show()
            return
        }

        val uid = auth.currentUser?.uid ?: return
        val userRef = db.reference.child("users").child(uid)
        userRef.child("name").setValue(name)
        userRef.child("weight").setValue(weight)
        userRef.child("height").setValue(height)
        userRef.child("goal").setValue(goal)

        // Update tampilan
        binding.tvUserName.text = name
        binding.tvNameResult.text = "Nama: $name"
        binding.tvWeightResult.text = "Berat Badan: $weight"
        binding.tvHeightResult.text = "Tinggi Badan: $height"
        binding.tvGoalResult.text = "Tujuan: $goal"

        binding.resultProfile.visibility = View.VISIBLE
        Toast.makeText(this, "Profil berhasil disimpan", Toast.LENGTH_SHORT).show()
    }

    // Hapus semua data profil
    private fun clearProfile() {
        val uid = auth.currentUser?.uid ?: return
        val userRef = db.reference.child("users").child(uid)

        // Hapus field di Firebase
        userRef.child("name").removeValue()
        userRef.child("weight").removeValue()
        userRef.child("height").removeValue()
        userRef.child("goal").removeValue()
        userRef.child("profileUrl").removeValue()

        // Reset tampilan lokal
        binding.etName.setText("")
        binding.etWeight.setText("")
        binding.etHeight.setText("")
        binding.etGoal.setText("")
        binding.tvUserName.text = "User"
        binding.tvNameResult.text = "Nama: -"
        binding.tvWeightResult.text = "Berat Badan: -"
        binding.tvHeightResult.text = "Tinggi Badan: -"
        binding.tvGoalResult.text = "Tujuan: -"
        binding.imageProfile.setImageResource(R.drawable.account_svg)

        binding.resultProfile.visibility = View.GONE
        Toast.makeText(this, "Profil berhasil dihapus", Toast.LENGTH_SHORT).show()
    }
}
