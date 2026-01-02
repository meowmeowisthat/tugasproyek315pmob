package com.proyek.tugasproyek

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.cloudinary.utils.ObjectUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.proyek.tugasproyek.databinding.ActivityProfileBinding
import java.io.File
import java.text.DecimalFormat

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseDatabase
    private var imageUri: Uri? = null

    private val goalOptions = arrayOf("Pertahankan Berat Badan", "Menurunkan Berat Badan", "Lain")

    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { uploadImage(it) }
        }

    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success && imageUri != null) {
                uploadImage(imageUri!!)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseDatabase.getInstance()

        setupSpinner()
        loadUserProfile()

        binding.btnMenu.setOnClickListener { finish() }

        binding.imgProfile.setOnClickListener {
            if (checkPermissions()) showImagePickerDialog()
        }

        binding.btnSave.setOnClickListener {
            saveProfile()
        }
    }

    private fun setupSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, goalOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerGoal.adapter = adapter
    }

    private fun loadUserProfile() {
        val uid = auth.currentUser?.uid ?: return


        db.reference.child("users").child(uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val name = snapshot.child("name").getValue(String::class.java)
                    val email = auth.currentUser?.email
                    val weight = snapshot.child("weight").getValue(String::class.java)
                    val height = snapshot.child("height").getValue(String::class.java)
                    val goal = snapshot.child("goal").getValue(String::class.java)
                    val url = snapshot.child("profileUrl").getValue(String::class.java)

                    binding.tvProfileName.text = name ?: "User"
                    binding.tvProfileEmail.text = email ?: "-"

                    binding.etFullName.setText(name)
                    binding.etWeight.setText(weight)
                    binding.etHeight.setText(height)

                    if (goal != null) {
                        val spinnerPosition = goalOptions.indexOf(goal)
                        if (spinnerPosition >= 0) {
                            binding.spinnerGoal.setSelection(spinnerPosition)
                        }
                    }

                    if (!url.isNullOrEmpty() && !isDestroyed) {
                        Glide.with(this@ProfileActivity)
                            .load(url)
                            .placeholder(R.drawable.ic_person)
                            .into(binding.imgProfile)
                    }

                    calculateBMI(weight, height)
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@ProfileActivity, "Gagal memuat data", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun saveProfile() {
        val name = binding.etFullName.text.toString().trim()
        val weight = binding.etWeight.text.toString().trim()
        val height = binding.etHeight.text.toString().trim()

        val selectedGoal = if (binding.spinnerGoal.selectedItem != null) {
            binding.spinnerGoal.selectedItem.toString()
        } else {
            ""
        }

        if (name.isEmpty()) {
            Toast.makeText(this, "Nama tidak boleh kosong", Toast.LENGTH_SHORT).show()
            return
        }

        val uid = auth.currentUser?.uid ?: return
        val ref = db.reference.child("users").child(uid)

        val updates = mapOf(
            "name" to name,
            "weight" to weight,
            "height" to height,
            "goal" to selectedGoal
        )

        ref.updateChildren(updates).addOnSuccessListener {
            binding.tvProfileName.text = name
            calculateBMI(weight, height)
            Toast.makeText(this, "Profil berhasil disimpan", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener {
            Toast.makeText(this, "Gagal menyimpan data", Toast.LENGTH_SHORT).show()
        }
    }

    private fun calculateBMI(weightStr: String?, heightStr: String?) {
        val weight = weightStr?.toDoubleOrNull()
        val height = heightStr?.toDoubleOrNull()

        if (weight != null && height != null && height > 0) {
            val heightInMeters = height / 100
            val bmi = weight / (heightInMeters * heightInMeters)
            val formattedBMI = DecimalFormat("#.#").format(bmi)

            binding.tvBMIValue.text = formattedBMI

            val (status, colorStr) = when {
                bmi < 18.5 -> "Berat Badan Kurang" to "#FF9800"
                bmi < 24.9 -> "Berat Badan Normal" to "#4CAF50"
                bmi < 29.9 -> "Berat Badan Berlebih" to "#FF5722"
                else -> "Obesitas" to "#D32F2F"
            }

            binding.tvBMIStatus.text = status
            try {
                binding.tvBMIStatus.setTextColor(Color.parseColor(colorStr))
            } catch (e: Exception) {
                binding.tvBMIStatus.setTextColor(Color.BLACK)
            }
        } else {
            binding.tvBMIValue.text = "-"
            binding.tvBMIStatus.text = "-"
        }
    }

    private fun checkPermissions(): Boolean {
        val list = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) list.add(Manifest.permission.CAMERA)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED)
                list.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED)
                list.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (list.isNotEmpty()) {
            requestPermissions(list.toTypedArray(), 101)
            return false
        }
        return true
    }

    private fun showImagePickerDialog() {
        val options = arrayOf("Kamera", "Galeri")
        AlertDialog.Builder(this)
            .setTitle("Ganti Foto Profil")
            .setItems(options) { _, which ->
                if (which == 0) openCamera()
                else galleryLauncher.launch("image/*")
            }
            .show()
    }

    private fun openCamera() {
        try {
            val file = File.createTempFile(
                "profile_", ".jpg",
                getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            )
            imageUri = FileProvider.getUriForFile(this, "$packageName.provider", file)
            cameraLauncher.launch(imageUri)
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal membuka kamera: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun uploadImage(uri: Uri) {
        val contentResolver = contentResolver ?: return

        Toast.makeText(this, "Mengupload gambar...", Toast.LENGTH_SHORT).show()

        Thread {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val result = CloudinaryHelper.cloudinary.uploader()
                        .upload(inputStream, ObjectUtils.emptyMap())

                    val url = result["secure_url"].toString()
                    val uid = auth.currentUser?.uid

                    if (uid != null) {
                        db.reference.child("users").child(uid).child("profileUrl").setValue(url)

                        runOnUiThread {
                            if (!isDestroyed) {
                                Glide.with(this@ProfileActivity)
                                    .load(url)
                                    .into(binding.imgProfile)
                                Toast.makeText(this@ProfileActivity, "Foto profil diperbarui", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this@ProfileActivity, "Gagal upload: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
}