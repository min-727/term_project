package com.example.term_project

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.prolificinteractive.materialcalendarview.MaterialCalendarView
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var calendarView: MaterialCalendarView
    private lateinit var txtYear: TextView
    private lateinit var txtMonth: TextView
    private lateinit var txtMainEmotion: TextView
    private lateinit var btnOption: Button
    private lateinit var btnCalendar: Button
    private lateinit var btnWriteDiary: Button
    private lateinit var btnList: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        calendarView = findViewById(R.id.calendarView)
        txtYear = findViewById(R.id.txtYear)
        txtMonth = findViewById(R.id.txtMonth)
        txtMainEmotion = findViewById(R.id.txtMainEmotion)
        btnOption = findViewById(R.id.btnOption)
        btnCalendar = findViewById(R.id.btnCalendar)
        btnWriteDiary = findViewById(R.id.btnWriteDiary)
        btnList = findViewById(R.id.btnList)


        val initialDate = Calendar.getInstance()
        initialDate.time = calendarView.currentDate.date
        updateYearMonth(initialDate)


        calendarView.setOnMonthChangedListener { _, date ->
            val cal = Calendar.getInstance()
            cal.time = date.date
            updateYearMonth(cal)
        }

        btnWriteDiary.setOnClickListener {
            // 일기 쓰기 화면으로 이동
        }

        btnCalendar.setOnClickListener {
            // 현재 캘린더 유지 또는 새로고침
        }

        btnList.setOnClickListener {
            // 일기 목록 화면으로 이동
        }
    }


    private fun updateYearMonth(calendar: Calendar) {
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        txtYear.text = "${year}年"
        txtMonth.text = "${month}월"
    }
}
