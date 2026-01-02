package com.proyek.tugasproyek

import android.graphics.Color
import android.graphics.DashPathEffect
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.proyek.tugasproyek.databinding.ActivityStatisticsBinding

class StatisticsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStatisticsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStatisticsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupCharts()
    }

    private fun setupUI() {
        binding.btnMenu.setOnClickListener { finish() }

        binding.btnWeekly.setOnClickListener {
            binding.btnWeekly.setBackgroundResource(R.drawable.bg_toggle_active)
            binding.btnWeekly.setTextColor(ContextCompat.getColor(this, R.color.app_green))

            binding.btnMonthly.setBackgroundResource(android.R.color.transparent)
            binding.btnMonthly.setTextColor(Color.parseColor("#757575"))

            // TODO: Load data mingguan
        }

        binding.btnMonthly.setOnClickListener {
            binding.btnMonthly.setBackgroundResource(R.drawable.bg_toggle_active)
            binding.btnMonthly.setTextColor(ContextCompat.getColor(this, R.color.app_green))

            binding.btnWeekly.setBackgroundResource(android.R.color.transparent)
            binding.btnWeekly.setTextColor(Color.parseColor("#757575"))

            // TODO: Load data bulanan
        }
    }

    private fun setupCharts() {
        val entries = ArrayList<Entry>()
        entries.add(Entry(0f, 0f))
        entries.add(Entry(1f, 0f))
        entries.add(Entry(2f, 0f))
        entries.add(Entry(3f, 0f))
        entries.add(Entry(4f, 0f))
        entries.add(Entry(5f, 0f))
        entries.add(Entry(6f, 0f))

        val days = arrayOf("Kam", "Jum", "Sab", "Ming", "Sen", "Sel", "Rab")

        configureChart(binding.chartCalories, entries, days, true)

        configureChart(binding.chartMeals, entries, days, false)
    }

    private fun configureChart(chart: LineChart, entries: List<Entry>, labels: Array<String>, isOrange: Boolean) {
        val colorPrimary = if (isOrange) ContextCompat.getColor(this, R.color.app_orange) else Color.LTGRAY

        val dataSet = LineDataSet(entries, "")
        dataSet.color = colorPrimary
        dataSet.lineWidth = 2f
        dataSet.setCircleColor(colorPrimary)
        dataSet.circleRadius = 4f
        dataSet.setDrawCircleHole(false)
        dataSet.setDrawValues(false)
        dataSet.mode = LineDataSet.Mode.LINEAR

        val lineData = LineData(dataSet)
        chart.data = lineData

        chart.description.isEnabled = false
        chart.legend.isEnabled = false
        chart.setTouchEnabled(false)
        chart.setPinchZoom(false)
        chart.setDrawGridBackground(false)

        val xAxis = chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(true)
        xAxis.enableGridDashedLine(10f, 10f, 0f)
        xAxis.gridColor = Color.parseColor("#E0E0E0")
        xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        xAxis.textColor = Color.parseColor("#757575")
        xAxis.axisMinimum = 0f
        xAxis.axisMaximum = 6f

        val axisLeft = chart.axisLeft
        axisLeft.setDrawGridLines(true)
        axisLeft.enableGridDashedLine(10f, 10f, 0f)
        axisLeft.gridColor = Color.parseColor("#E0E0E0")
        axisLeft.textColor = Color.parseColor("#757575")
        axisLeft.axisMinimum = 0f
        axisLeft.axisMaximum = 4f
        axisLeft.setLabelCount(5, true)

        chart.axisRight.isEnabled = false

        chart.invalidate()
    }
}