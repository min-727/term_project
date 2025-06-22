package com.example.term_project

import android.app.AlertDialog
import android.content.Intent
import android.view.View
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.NumberPicker
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.firebase.firestore.FirebaseFirestore
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.*
import android.widget.HorizontalScrollView
import android.widget.TextView
import android.text.TextUtils



class AnalysisActivity : AppCompatActivity() {

    private lateinit var barChart: BarChart
    private lateinit var btnRecommend: Button
    private lateinit var bookCoverLayout: LinearLayout
    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var btnPickMonth : Button
    private val KAKAO_REST_API_KEY = "0b67644a78ec02cab8865905f57070c6"
    private val client = OkHttpClient()
    // 기존 코드 교체 (기존 emotionKeywords 제거)
    private val emotionKeywordMap = mapOf(
        0 to listOf("슬픔", "위로", "이별", "눈물 나는 이야기", "감성 에세이"),     // 😢
        1 to listOf("혼란", "자아 찾기", "불안", "마음의 소리", "감정 정리"),      // 😕
        2 to listOf("정리", "새 출발", "자기 계발", "명상", "비움"),               // 😐
        3 to listOf("에세이", "소확행", "따뜻한 이야기", "기분 전환", "감동"),       // 😊
        4 to listOf("행복", "기쁨", "사랑", "웃음", "힐링 소설")                  // 😄
    )


    private var currentEmojiCounts = IntArray(5)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_emoji_status)

        barChart = findViewById(R.id.barChart)
        btnRecommend = findViewById(R.id.btnRecommendBooks)
        bookCoverLayout = findViewById(R.id.bookCoverLayout)
        val btnCalendar = findViewById<Button>(R.id.btnCalendar)
        val btnDiary = findViewById<Button>(R.id.btnWriteDiary)
        val btnList = findViewById<Button>(R.id.btnList)

        btnCalendar.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        btnDiary.setOnClickListener {
            startActivity(Intent(this, DiaryActivity::class.java))
        }

        btnList.setOnClickListener {
            startActivity(Intent(this, DiaryListActivity::class.java))
        }

        val now = Calendar.getInstance()
        val year = now.get(Calendar.YEAR)
        val month = now.get(Calendar.MONTH) + 1
        val btnPickMonth = findViewById<Button>(R.id.btnPickMonth)
        btnPickMonth.text = "$year/$month"
        btnPickMonth.setOnClickListener {
            showYearMonthPickerDialog()
        }

        btnRecommend.setOnClickListener {
            recommendBookBasedOnEmotion()
        }
        loadEmojiDataForMonth(now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1)
    }

    private fun showYearMonthPickerDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_analysis, null)
        val yearPicker = dialogView.findViewById<NumberPicker>(R.id.pickerYear)
        val monthPicker = dialogView.findViewById<NumberPicker>(R.id.pickerMonth)

        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val currentMonth = Calendar.getInstance().get(Calendar.MONTH) + 1

        yearPicker.minValue = 2000
        yearPicker.maxValue = 2100
        yearPicker.value = currentYear

        monthPicker.minValue = 1
        monthPicker.maxValue = 12
        monthPicker.value = currentMonth

        AlertDialog.Builder(this)
            .setTitle("연도 및 월 선택")
            .setView(dialogView)
            .setPositiveButton("확인") { _, _ ->
                loadEmojiDataForMonth(yearPicker.value, monthPicker.value)
                btnPickMonth.text = "${yearPicker.value}/${monthPicker.value}"
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun loadEmojiDataForMonth(year: Int, month: Int) {
        val monthStr = String.format("%02d", month)
        val startDate = "$year-$monthStr-01"
        val endDate = if (month == 12) "${year + 1}-01-01" else "$year-${String.format("%02d", month + 1)}-01"

        firestore.collection("diaries")
            .whereGreaterThanOrEqualTo("date", startDate)
            .whereLessThan("date", endDate)
            .get()
            .addOnSuccessListener { snapshot ->
                val emojiCounts = IntArray(5) { 0 }

                for (doc in snapshot) {
                    val emojiIndex = (doc.getLong("emoji") ?: -1L).toInt()
                    if (emojiIndex in 1..5) {
                        emojiCounts[emojiIndex - 1]++
                    }
                }

                currentEmojiCounts = emojiCounts
                updateBarChart(emojiCounts)
            }
            .addOnFailureListener {
                Log.e("EmojiStats", "Firestore 오류", it)
            }
    }

    private fun updateBarChart(emojiCounts: IntArray) {
        val entries = emojiCounts.mapIndexed { index, count ->
            BarEntry(index.toFloat(), count.toFloat())
        }

        val dataSet = BarDataSet(entries, "").apply {
            color = Color.parseColor("#A8E6CF")
        }

        barChart.apply {
            data = BarData(dataSet).apply { barWidth = 0.4f }
            xAxis.apply {
                valueFormatter = object : ValueFormatter() {
                    private val labels = listOf("😢", "😕", "😐", "😊", "😄")
                    override fun getFormattedValue(value: Float): String {
                        return labels.getOrNull(value.toInt()) ?: ""
                    }
                }
                granularity = 1f
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                textSize = 24f
                yOffset = 20f
            }
            axisLeft.axisMinimum = 0f
            axisRight.isEnabled = false
            description.isEnabled = false
            legend.isEnabled = false

            setExtraOffsets(0f, 0f, 0f, 50f)
            invalidate()
        }
    }

    // recommendBookBasedOnEmotion 함수 수정
    private fun recommendBookBasedOnEmotion() {
        Log.d("AnalysisActivity", "currentEmojiCounts = ${currentEmojiCounts.joinToString()}")
        val maxIndex = currentEmojiCounts.indices.maxByOrNull { currentEmojiCounts[it] } ?: return

        val keywordList = emotionKeywordMap[maxIndex] ?: listOf("책")
        val keyword = keywordList.random() // ✅ 감정에 맞는 키워드 중 하나를 랜덤으로 선택
        Log.d("AnalysisActivity", "선택된 감정 인덱스: $maxIndex, 키워드: $keyword")

        searchBooks(keyword) { jsonResponse ->
            if (jsonResponse != null) {
                Log.d("KakaoAPI", "응답 JSON:\n$jsonResponse")
                showBooksWithCovers(jsonResponse)
            } else {
                showErrorDialog()
            }
        }
    }




    private fun searchBooks(keyword: String, onResult: (String?) -> Unit) {
        // 최대 페이지 수: Kakao API 기준으로 50 (또는 15까지로 제한해도 안전)
        val randomPage = (1..15).random()
        val url = "https://dapi.kakao.com/v3/search/book?query=${URLEncoder.encode(keyword, "UTF-8")}&sort=latest&page=$randomPage"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "KakaoAK $KAKAO_REST_API_KEY")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { onResult(null) }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                runOnUiThread { onResult(body) }
            }
        })
    }


    private fun showBooksWithCovers(jsonResponse: String) {
        try {
            val json = JSONObject(jsonResponse)
            val documents = json.getJSONArray("documents")

            bookCoverLayout.removeAllViews()

            val indices = (0 until documents.length()).shuffled().take(5)

            for (i in indices) {
                val book = documents.getJSONObject(i)
                val title = book.optString("title", "")
                val thumbnail = book.optString("thumbnail", "")

                if (thumbnail.isNotBlank()) {
                    val container = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        val params = LinearLayout.LayoutParams(200, LinearLayout.LayoutParams.WRAP_CONTENT)
                        params.setMargins(16, 0, 16, 0)
                        layoutParams = params
                    }

                    val imageView = ImageView(this).apply {
                        layoutParams = LinearLayout.LayoutParams(200, 300)
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }

                    Glide.with(this)
                        .load(thumbnail)
                        .error(R.drawable.ic_emoji_confused)
                        .into(imageView)

                    val textView = TextView(this).apply {
                        text = title
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                    }

                    container.addView(imageView)
                    container.addView(textView)
                    bookCoverLayout.addView(container)
                }
            }

            findViewById<HorizontalScrollView>(R.id.bookScrollView).visibility = View.VISIBLE

        } catch (e: Exception) {
            e.printStackTrace()
            showErrorDialog()
        }
    }




    private fun showErrorDialog() {
        AlertDialog.Builder(this)
            .setTitle("오류")
            .setMessage("책 정보를 불러오는 중 오류가 발생했습니다.")
            .setPositiveButton("확인", null)
            .show()
    }
}
