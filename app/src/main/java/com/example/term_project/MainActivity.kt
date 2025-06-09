package com.example.term_project

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.view.CalendarView
import com.kizitonwose.calendar.view.MonthDayBinder
import com.kizitonwose.calendar.view.ViewContainer
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.LocalDate
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

    private val db = FirebaseFirestore.getInstance()
    private val emojiMap = mutableMapOf<String, Int>()

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

        calendarView.dayViewResource = R.layout.day_view

// 🔁 dayBinder 수정
        calendarView.dayBinder = object : MonthDayBinder<DayViewContainer> {
            override fun create(view: View): DayViewContainer = DayViewContainer(view)

            override fun bind(container: DayViewContainer, data: CalendarDay) {
                container.textView.text = data.date.dayOfMonth.toString()

                // 오늘 날짜인지 체크
                val isToday = data.date == LocalDate.now()

                if (data.position == DayPosition.MonthDate) {
                    container.textView.visibility = View.VISIBLE

                    // 배경 다르게 설정
                    container.view.background = if (isToday) {
                        ContextCompat.getDrawable(this@MainActivity, R.drawable.day_today_border)
                    } else {
                        ContextCompat.getDrawable(this@MainActivity, R.drawable.day_border)
                    }

                    val dateKey = data.date.toString() // yyyy-MM-dd
                    val emojiRes = emojiMap[dateKey]

                    if (emojiRes != null) {
                        container.emojiView.visibility = View.VISIBLE
                        container.emojiView.setImageResource(emojiRes)
                        container.textView.visibility = View.GONE // 숫자는 숨김
                    } else {
                        container.emojiView.visibility = View.GONE
                        container.textView.visibility = View.VISIBLE
                    }
                } else {
                    container.textView.visibility = View.INVISIBLE
                    container.emojiView.visibility = View.GONE
                    container.view.background = null
                }
            }


        }


        val today = Calendar.getInstance()
        val currentYear = today.get(Calendar.YEAR)
        val currentMonth = today.get(Calendar.MONTH) + 1
        val currentYearMonth = YearMonth.of(currentYear, currentMonth)

        calendarView.setup(
            YearMonth.of(2000, 1),
            YearMonth.of(2050, 12),
            DayOfWeek.SUNDAY
        )
        calendarView.scrollToMonth(currentYearMonth)
        updateYearMonth(currentYearMonth)

        calendarView.monthScrollListener = { month ->
            updateYearMonth(month.yearMonth)
        }

        btnMainEmotion.setOnClickListener {
            showYearMonthDialog()
        }

        btnCalendar.setOnClickListener {
            calendarView.scrollToMonth(currentYearMonth)
        }

        btnWriteDiary.setOnClickListener {
            // 일기 쓰기 화면으로 이동
        }

        btnList.setOnClickListener {
            // 일기 목록 화면으로 이동
        }

        // 🎯 Firestore에서 emoji 데이터 불러오기
        fetchEmojiData()
    }

    private fun updateYearMonth(yearMonth: YearMonth) {
        txtYear.text = getString(R.string.year_format, yearMonth.year)
        txtMonth.text = getString(R.string.month_format, yearMonth.monthValue)
    }

    private fun showYearMonthDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_year_month_picker, null)
        val yearPicker = dialogView.findViewById<NumberPicker>(R.id.yearPicker)
        val monthPicker = dialogView.findViewById<NumberPicker>(R.id.monthPicker)

        val now = YearMonth.now()
        yearPicker.minValue = 2000
        yearPicker.maxValue = 2100
        yearPicker.value = now.year

        monthPicker.minValue = 1
        monthPicker.maxValue = 12
        monthPicker.value = now.monthValue

        AlertDialog.Builder(this)
            .setTitle("연도/월 선택")
            .setView(dialogView)
            .setPositiveButton("확인") { _, _ ->
                val selected = YearMonth.of(yearPicker.value, monthPicker.value)
                updateYearMonth(selected)
                calendarView.scrollToMonth(selected)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun fetchEmojiData() {
        db.collection("diaries")
            .get()
            .addOnSuccessListener { result ->
                for (document in result) {
                    val date = document.getString("date")
                    val emoji = document.getLong("emoji")?.toInt()

                    if (date != null && emoji != null) {
                        emojiMap[date] = getEmojiResId(emoji)
                    }
                }
                calendarView.notifyCalendarChanged()
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
            }
    }

    private fun getEmojiResId(emoji: Int): Int {
        return when (emoji) {
            1 -> R.drawable.ic_emoji_sad
            2 -> R.drawable.ic_emoji_confused
            3 -> R.drawable.ic_emoji_neutral
            4 -> R.drawable.ic_emoji_smile
            5 -> R.drawable.ic_emoji_happy
            else -> R.drawable.day_border
        }
    }

    class DayViewContainer(view: View) : ViewContainer(view) {
        val textView: TextView = view.findViewById(R.id.dayText)
        val emojiView: ImageView = view.findViewById(R.id.emojiImage)
    }

}
