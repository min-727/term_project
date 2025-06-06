package com.example.term_project

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.NumberPicker
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.view.CalendarView
import com.kizitonwose.calendar.view.MonthDayBinder
import com.kizitonwose.calendar.view.ViewContainer
import java.time.DayOfWeek
import java.time.YearMonth
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var calendarView: CalendarView
    private lateinit var txtYear: TextView
    private lateinit var txtMonth: TextView
    private lateinit var btnMainEmotion: Button
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
        btnMainEmotion = findViewById(R.id.btnMainEmotion)
        btnOption = findViewById(R.id.btnOption)
        btnCalendar = findViewById(R.id.btnCalendar)
        btnWriteDiary = findViewById(R.id.btnWriteDiary)
        btnList = findViewById(R.id.btnList)

        // ✅ dayViewResource 설정 (View를 기반으로 ViewContainer를 만들 수 있게 함)
        calendarView.dayViewResource = R.layout.day_view

        // ✅ MonthDayBinder 설정
        calendarView.dayBinder = object : MonthDayBinder<DayViewContainer> {
            override fun create(view: View): DayViewContainer {
                return DayViewContainer(view)
            }

            override fun bind(container: DayViewContainer, data: CalendarDay) {
                container.textView.text = data.date.dayOfMonth.toString()

                if (data.position == com.kizitonwose.calendar.core.DayPosition.MonthDate) {
                    container.textView.visibility = View.VISIBLE
                } else {
                    container.textView.visibility = View.INVISIBLE
                }
            }
        }

        val today = Calendar.getInstance()
        val currentYear = today.get(Calendar.YEAR)
        val currentMonth = today.get(Calendar.MONTH) + 1



        val startYearMonthObj = YearMonth.of(2000,1)
        val endYearMonthObj = YearMonth.of(2050, 12)
        val currentYearMonthObj = YearMonth.of(currentYear, currentMonth)

        calendarView.setup(startYearMonthObj, endYearMonthObj, DayOfWeek.SUNDAY)
        calendarView.scrollToMonth(currentYearMonthObj)

        updateYearMonth(currentYearMonthObj)

        calendarView.monthScrollListener = { month ->
            updateYearMonth(month.yearMonth)
        }

        btnWriteDiary.setOnClickListener {
            // 일기 쓰기 화면으로 이동
        }

        btnCalendar.setOnClickListener {
            calendarView.scrollToMonth(currentYearMonthObj)
        }

        btnList.setOnClickListener {
            // 일기 목록 화면으로 이동
        }
        btnMainEmotion.setOnClickListener {
            showYearMonthDialog()
        }

    }

    private fun showYearMonthDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_year_month_picker, null)
        val yearPicker = dialogView.findViewById<NumberPicker>(R.id.yearPicker)
        val monthPicker = dialogView.findViewById<NumberPicker>(R.id.monthPicker)

        val currentYear = YearMonth.now().year
        yearPicker.minValue = 2000
        yearPicker.maxValue = 2100
        yearPicker.value = currentYear

        monthPicker.minValue = 1
        monthPicker.maxValue = 12
        monthPicker.value = YearMonth.now().monthValue

        AlertDialog.Builder(this)
            .setTitle("연도/월 선택")
            .setView(dialogView)
            .setPositiveButton("확인") { _, _ ->
                val selectedYear = yearPicker.value
                val selectedMonth = monthPicker.value
                val selectedYearMonth = YearMonth.of(selectedYear, selectedMonth)

                // ✅ 텍스트에 반영
                updateYearMonth(selectedYearMonth)

                // ✅ 캘린더 위치 이동
                calendarView.scrollToMonth(selectedYearMonth)
            }
            .setNegativeButton("취소", null)
            .show()
    }


    private fun updateYearMonth(yearMonth: YearMonth) {
        txtYear.text = getString(R.string.year_format, yearMonth.year)
        txtMonth.text = getString(R.string.month_format, yearMonth.monthValue)
    }


    class DayViewContainer(view: View) : ViewContainer(view) {
        val textView: TextView = view.findViewById(R.id.dayText)
    }
}
