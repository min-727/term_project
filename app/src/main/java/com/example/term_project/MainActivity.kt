package com.example.term_project

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.prolificinteractive.materialcalendarview.MaterialCalendarView
import com.prolificinteractive.materialcalendarview.CalendarDay
import java.util.*

class HomeActivity : AppCompatActivity() {

    private lateinit var calendarView: MaterialCalendarView
    private lateinit var btnSelectDate: Button
    private lateinit var btnAnalyzeEmotion: Button
    private lateinit var btnWriteToday: Button
    private lateinit var btnDiaryList: Button
    private lateinit var btnGoHome: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        calendarView = findViewById(R.id.calendarView)
        btnSelectDate = findViewById(R.id.btnSelectDate)
        btnAnalyzeEmotion = findViewById(R.id.btnAnalyzeEmotion)
        btnWriteToday = findViewById(R.id.btnWriteToday)
        btnDiaryList = findViewById(R.id.btnDiaryList)
        btnGoHome = findViewById(R.id.btnGoHome)

        // 1. 초기 날짜 설정
        val today = CalendarDay.today()
        calendarView.setDateSelected(today, true)
        updateDateButton(today)

        // 2. 날짜가 선택되면 버튼 텍스트 변경
        calendarView.setOnDateChangedListener { _, date, _ ->
            updateDateButton(date)
        }

        // 3. 버튼 클릭 이벤트들
        btnSelectDate.setOnClickListener {
            // 선택된 날짜 다시 홈으로 반영 (이미 캘린더에 선택되므로 특별히 액션 X)
        }

        btnWriteToday.setOnClickListener {
            val intent = Intent(this, WriteDiaryActivity::class.java)
            intent.putExtra("selectedDate", calendarView.selectedDate?.date.toString())
            startActivity(intent)
        }

        btnDiaryList.setOnClickListener {
            startActivity(Intent(this, DiaryListActivity::class.java))
        }

        btnAnalyzeEmotion.setOnClickListener {
            startActivity(Intent(this, AnalyzeActivity::class.java))
        }

        btnGoHome.setOnClickListener {
            // 홈에서 홈으로 가는 버튼이긴 하지만, 새로고침 기능으로 활용 가능
            recreate()
        }
    }

    private fun updateDateButton(date: CalendarDay?) {
        date?.let {
            val year = it.year
            val month = it.month + 1 // 0부터 시작하므로 +1
            btnSelectDate.text = "${year}년 ${month}월"
        }
    }
}
