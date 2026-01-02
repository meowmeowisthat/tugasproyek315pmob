package com.proyek.tugasproyek

import android.Manifest
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.proyek.tugasproyek.databinding.ActivityMealBinding
import java.text.SimpleDateFormat
import java.util.*

class MealActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMealBinding
    private lateinit var db: FirebaseDatabase
    private lateinit var auth: FirebaseAuth
    private lateinit var adapter: MealAdapter

    private val calendar = Calendar.getInstance()
    private val displayDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val dbDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    private var editingMealKey: Pair<String, String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMealBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseDatabase.getInstance()
        auth = FirebaseAuth.getInstance()

        setupPermissions()
        setupUI()
        loadMeals()
    }

    private fun setupUI() {
        adapter = MealAdapter { meal ->
            showMealDialog(meal)
        }
        binding.rvMeals.layoutManager = LinearLayoutManager(this)
        binding.rvMeals.adapter = adapter

        binding.btnBack.setOnClickListener { finish() }

        binding.fabAddMeal.setOnClickListener {
            editingMealKey = null //
            showMealDialog(null)
        }
    }

    private fun showMealDialog(mealToEdit: Meal?) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.layout_dialog_meal, null)
        dialog.setContentView(view)

        (view.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)

        val btnClose = view.findViewById<ImageView>(R.id.btnClose)
        val spMealType = view.findViewById<Spinner>(R.id.spMealType)
        val etName = view.findViewById<EditText>(R.id.etMealName)
        val etPortion = view.findViewById<EditText>(R.id.etPortion)
        val etCalorie = view.findViewById<EditText>(R.id.etCalories)
        val tvDate = view.findViewById<TextView>(R.id.tvDate)
        val tvTime = view.findViewById<TextView>(R.id.tvTime)
        val switchReminder = view.findViewById<SwitchCompat>(R.id.switchReminder)
        val btnCancel = view.findViewById<Button>(R.id.btnCancel)
        val btnSave = view.findViewById<Button>(R.id.btnSave)

        val mealTypes = arrayOf("Sarapan", "Makan Siang", "Makan Malam", "Cemilan")
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, mealTypes)
        spMealType.adapter = spinnerAdapter

        val currentCal = Calendar.getInstance()
        tvDate.text = displayDateFormat.format(currentCal.time)
        tvTime.text = timeFormat.format(currentCal.time)
        var currentDbDate = dbDateFormat.format(currentCal.time)

        if (mealToEdit != null) {
            etName.setText(mealToEdit.food)
            etPortion.setText(mealToEdit.portion.toString())

            val calPerPortion = if (mealToEdit.portion > 0) mealToEdit.calorie / mealToEdit.portion else 0
            etCalorie.setText(calPerPortion.toString())

            tvTime.text = mealToEdit.time

            val spinnerPos = spinnerAdapter.getPosition(mealToEdit.type)
            if (spinnerPos >= 0) spMealType.setSelection(spinnerPos)

            try {
                val dateObj = dbDateFormat.parse(mealToEdit.date)
                if (dateObj != null) {
                    tvDate.text = displayDateFormat.format(dateObj)
                    currentDbDate = mealToEdit.date
                    currentCal.time = dateObj
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            editingMealKey = Pair(mealToEdit.date, mealToEdit.type)
        }

        tvDate.setOnClickListener {
            val datePicker = DatePickerDialog(this, { _, year, month, day ->
                currentCal.set(year, month, day)
                tvDate.text = displayDateFormat.format(currentCal.time)
                currentDbDate = dbDateFormat.format(currentCal.time)
            }, currentCal.get(Calendar.YEAR), currentCal.get(Calendar.MONTH), currentCal.get(Calendar.DAY_OF_MONTH))
            datePicker.show()
        }

        tvTime.setOnClickListener {
            val timeParts = tvTime.text.split(":")
            val initHour = if (timeParts.size == 2) timeParts[0].toInt() else currentCal.get(Calendar.HOUR_OF_DAY)
            val initMinute = if (timeParts.size == 2) timeParts[1].toInt() else currentCal.get(Calendar.MINUTE)

            val timePicker = TimePickerDialog(this, { _, hour, minute ->
                val selectedCal = Calendar.getInstance()
                selectedCal.set(Calendar.HOUR_OF_DAY, hour)
                selectedCal.set(Calendar.MINUTE, minute)
                tvTime.text = timeFormat.format(selectedCal.time)
            }, initHour, initMinute, true)
            timePicker.show()
        }

        btnClose.setOnClickListener { dialog.dismiss() }
        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val type = spMealType.selectedItem.toString()
            val name = etName.text.toString().trim()
            val portionStr = etPortion.text.toString().trim()
            val calStr = etCalorie.text.toString().trim()
            val time = tvTime.text.toString().trim()
            val isReminderEnabled = switchReminder.isChecked

            if (name.isEmpty() || portionStr.isEmpty() || calStr.isEmpty()) {
                Toast.makeText(this, "Mohon lengkapi semua data", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val portion = portionStr.toInt()
            val caloriePerPortion = calStr.toInt()
            val totalCalorie = portion * caloriePerPortion

            saveMealToFirebase(type, name, portion, totalCalorie, currentDbDate, time, isReminderEnabled)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun saveMealToFirebase(type: String, name: String, portion: Int, calorie: Int, dateDbFormat: String, time: String, setReminder: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        val userRef = db.reference.child("users").child(uid).child("meals")

        editingMealKey?.let { (oldDate, oldType) ->
            if (oldDate != dateDbFormat || oldType != type) {
                userRef.child(oldDate).child(oldType).removeValue()
            }
        }

        val mealData = mapOf(
            "food" to name,
            "portion" to portion,
            "calorie" to calorie,
            "time" to time
        )

        userRef.child(dateDbFormat).child(type).setValue(mealData)
            .addOnSuccessListener {
                Toast.makeText(this, "Berhasil disimpan", Toast.LENGTH_SHORT).show()
                loadMeals()

                if (setReminder) {
                    setMealReminder(time, name)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadMeals() {
        val uid = auth.currentUser?.uid ?: return
        val todayDate = dbDateFormat.format(Date())

        db.reference.child("users").child(uid).child("meals")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val mealList = mutableListOf<Meal>()
                    var totalCalories = 0
                    var totalMealsToday = 0
                    var remindersCount = 0

                    for (dateSnap in snapshot.children) {
                        val dateKey = dateSnap.key ?: continue

                        for (typeSnap in dateSnap.children) {
                            val typeKey = typeSnap.key ?: continue
                            val food = typeSnap.child("food").getValue(String::class.java) ?: ""
                            val portion = typeSnap.child("portion").getValue(Int::class.java) ?: 1
                            val calorie = typeSnap.child("calorie").getValue(Int::class.java) ?: 0
                            val time = typeSnap.child("time").getValue(String::class.java) ?: ""

                            mealList.add(Meal(food, typeKey, portion, time, calorie, dateKey))

                            if (dateKey == todayDate) {
                                totalCalories += calorie
                                totalMealsToday++
                                remindersCount++
                            }
                        }
                    }

                    mealList.sortWith(compareBy({ it.date }, { it.time }))
                    adapter.submitList(mealList)

                    binding.tvTotalMeals.text = totalMealsToday.toString()
                    binding.tvTotalKcal.text = totalCalories.toString()
                    binding.tvReminders.text = remindersCount.toString()
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("MealActivity", "Load Error", error.toException())
                }
            })
    }

    private fun setMealReminder(timeString: String, foodName: String) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                startActivity(intent)
                return
            }
        }

        try {
            val parts = timeString.split(":")
            val hour = parts[0].toInt()
            val minute = parts[1].toInt()

            val requestCode = hour * 100 + minute

            val intent = Intent(this, MealReminderReceiver::class.java).apply {
                putExtra("foodName", foodName)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                this, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                if (before(Calendar.getInstance())) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
            Toast.makeText(this, "Pengingat diset jam $timeString", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Log.e("Reminder", "Error setting reminder", e)
            Toast.makeText(this, "Gagal set alarm", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }
    }
}