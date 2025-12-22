package com.proyek.tugasproyek

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.proyek.tugasproyek.databinding.ActivityMealBinding
import java.text.SimpleDateFormat
import java.util.*

class MealActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMealBinding
    private lateinit var db: FirebaseDatabase
    private lateinit var auth: FirebaseAuth
    private val calendar = Calendar.getInstance()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    private lateinit var adapter: MealAdapter
    private var editingMealKey: Pair<String, String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMealBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseDatabase.getInstance()
        auth = FirebaseAuth.getInstance()

        // Toolbar back
        binding.ivBack.setOnClickListener { finish() }

        adapter = MealAdapter(::editMeal, ::deleteMeal)
        binding.rvMeals.layoutManager = LinearLayoutManager(this)
        binding.rvMeals.adapter = adapter

        binding.etDate.setText(dateFormat.format(calendar.time))
        binding.etTime.setText(timeFormat.format(calendar.time))

        binding.etDate.setOnClickListener { showDatePicker() }
        binding.etTime.setOnClickListener { showTimePicker() }

        // Tombol tambah toggle form
        binding.btnAddMeal.setOnClickListener {
            binding.mealBox.visibility = View.VISIBLE
            binding.btnAddMeal.visibility = View.GONE
        }

        // Tombol simpan
        binding.btnSaveMeal.setOnClickListener { saveMeal() }

        Log.d("AUTH_TEST", "UID = ${auth.currentUser?.uid}")

        loadMeals()
    }

    private fun saveMeal() {
        val type = binding.etType.text.toString().trim()
        val name = binding.etName.text.toString().trim()
        val portionText = binding.etPortion.text.toString().trim()
        val date = binding.etDate.text.toString().trim()
        val time = binding.etTime.text.toString().trim()

        if (type.isEmpty() || name.isEmpty() || portionText.isEmpty() || date.isEmpty() || time.isEmpty()) {
            Toast.makeText(this, "Semua field wajib diisi", Toast.LENGTH_SHORT).show()
            return
        }

        val portion = portionText.toIntOrNull()
        if (portion == null) {
            Toast.makeText(this, "Porsi harus berupa angka", Toast.LENGTH_SHORT).show()
            return
        }

        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "User belum login", Toast.LENGTH_SHORT).show()
            return
        }

        val mealData = mapOf(
            "food" to name,
            "portion" to portion,
            "time" to time
        )

        val userRef = db.reference.child("users").child(uid).child("meals")

        // Hapus data lama jika edit
        editingMealKey?.let { key ->
            val (oldDate, oldType) = key
            if (oldDate != date || oldType != type) {
                userRef.child(oldDate).child(oldType).removeValue()
            }
            editingMealKey = null
        }

        userRef.child(date).child(type)
            .setValue(mealData)
            .addOnSuccessListener {
                Toast.makeText(this, "Data berhasil disimpan", Toast.LENGTH_SHORT).show()
                clearForm()
                binding.mealBox.visibility = View.GONE
                binding.btnAddMeal.visibility = View.VISIBLE
                loadMeals()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal menyimpan data", Toast.LENGTH_SHORT).show()
                Log.e("MealActivity", "Error saat simpan", e)
            }
    }

    private fun loadMeals() {
        val uid = auth.currentUser?.uid ?: return
        db.reference.child("users").child(uid).child("meals")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val meals = mutableListOf<Meal>()
                    for (dateSnap in snapshot.children) {
                        val date = dateSnap.key ?: continue
                        for (typeSnap in dateSnap.children) {
                            val type = typeSnap.key ?: continue
                            val food = typeSnap.child("food").getValue(String::class.java) ?: ""
                            val portion = typeSnap.child("portion").getValue(Int::class.java) ?: 0
                            val time = typeSnap.child("time").getValue(String::class.java) ?: ""
                            meals.add(Meal(date, type, food, portion, time))
                        }
                    }
                    meals.sortWith(compareBy({ it.date }, { it.time }))
                    adapter.submitList(meals)
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun editMeal(meal: Meal) {
        binding.mealBox.visibility = View.VISIBLE
        binding.btnAddMeal.visibility = View.GONE
        binding.etType.setText(meal.type)
        binding.etName.setText(meal.food)
        binding.etPortion.setText(meal.portion.toString())
        binding.etDate.setText(meal.date)
        binding.etTime.setText(meal.time)

        editingMealKey = Pair(meal.date, meal.type)
    }

    private fun deleteMeal(meal: Meal) {
        val uid = auth.currentUser?.uid ?: return
        db.reference.child("users").child(uid).child("meals")
            .child(meal.date).child(meal.type)
            .removeValue()
            .addOnSuccessListener {
                loadMeals()
                Toast.makeText(this, "Data dihapus", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal menghapus data", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showDatePicker() {
        val today = Calendar.getInstance()
        val dpd = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val selected = Calendar.getInstance()
                selected.set(year, month, dayOfMonth)
                binding.etDate.setText(dateFormat.format(selected.time))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        dpd.datePicker.minDate = today.timeInMillis
        dpd.show()
    }

    private fun showTimePicker() {
        val tpd = TimePickerDialog(
            this,
            { _, hour, minute ->
                val selected = Calendar.getInstance()
                selected.set(Calendar.HOUR_OF_DAY, hour)
                selected.set(Calendar.MINUTE, minute)
                binding.etTime.setText(timeFormat.format(selected.time))
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        )
        tpd.show()
    }

    private fun clearForm() {
        binding.etType.text?.clear()
        binding.etName.text?.clear()
        binding.etPortion.text?.clear()
        binding.etDate.setText(dateFormat.format(Calendar.getInstance().time))
        binding.etTime.setText(timeFormat.format(Calendar.getInstance().time))
        editingMealKey = null
    }
}
