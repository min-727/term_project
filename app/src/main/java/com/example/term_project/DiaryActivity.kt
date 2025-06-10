package com.example.term_project

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.term_project.WeatherApiHelper
import com.google.android.gms.location.LocationServices
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import android.content.Intent

class DiaryActivity : AppCompatActivity() {

    private lateinit var etTitle: EditText
    private lateinit var etWeather: EditText
    private lateinit var etContent: EditText
    private lateinit var ivAddImage: ImageView
    private lateinit var ivPhoto: ImageView
    private lateinit var btnMic: ImageButton
    private lateinit var llEmojis: LinearLayout
    private lateinit var btnSub: ImageButton

    private lateinit var pickImageLauncher: ActivityResultLauncher<String>
    private lateinit var speechLauncher: ActivityResultLauncher<Intent>

    private val firestore = FirebaseFirestore.getInstance()
    private var selectedImageUri: Uri? = null
    private var selectedEmojiId: Int = -1

    private lateinit var currentDate: String
    private lateinit var currentTime: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diary)

        // 날짜/시간 포맷
        val now = Date()
        currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now)
        currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(now)
        Log.d("DiaryActivity", "현재 날짜: $currentDate, 현재 시간: $currentTime")

        // 뷰 바인딩
        etTitle = findViewById(R.id.etTitle)
        etWeather = findViewById(R.id.etWeather)
        etContent = findViewById(R.id.etContent)
        ivAddImage = findViewById(R.id.ivAddImage)
        ivPhoto = findViewById(R.id.ivPhoto)
        btnMic = findViewById(R.id.btnMic)
        llEmojis = findViewById(R.id.llEmojis)
        btnSub = findViewById(R.id.btnSub)

        // 날씨 조회 (코루틴)
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            lifecycleScope.launch {
                val emoji = WeatherApiHelper.fetchWeatherEmoji(this@DiaryActivity)
                etWeather.setText(emoji)
            }
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                PERMISSION_REQUEST_LOCATION
            )
        }

        // 이미지 선택
        pickImageLauncher = registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            uri?.let {
                ivPhoto.setImageURI(it)
                ivPhoto.visibility = View.VISIBLE
                ivAddImage.visibility = View.GONE
                selectedImageUri = it
            }
        }
        ivAddImage.setOnClickListener { pickImageLauncher.launch("image/*") }
        ivPhoto.setOnClickListener {
            ivPhoto.setImageDrawable(null)
            ivPhoto.visibility = View.GONE
            ivAddImage.visibility = View.VISIBLE
            selectedImageUri = null
        }

        // 음성 인식
        speechLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val matches = result.data!!.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                matches?.firstOrNull()?.let {
                    etContent.append(it)
                }
            }
        }
        btnMic.setOnClickListener {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "음성으로 일기 입력")
            }
            speechLauncher.launch(intent)
        }

        // 이모지 선택
        for (i in 1..5) {
            val id = resources.getIdentifier("btnEmoji$i", "id", packageName)
            findViewById<ImageButton>(id).setOnClickListener { btn ->
                if (selectedEmojiId != -1 && selectedEmojiId != id) {
                    findViewById<ImageButton>(selectedEmojiId)
                        .background = getBorderlessSelectableDrawable()
                }
                btn.background = ContextCompat.getDrawable(this, R.drawable.bg_emoji_selected)
                selectedEmojiId = id
            }
        }

        btnSub.setOnClickListener { uploadDiaryToFirestore() }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_LOCATION && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            lifecycleScope.launch {
                val emoji = WeatherApiHelper.fetchWeatherEmoji(this@DiaryActivity)
                etWeather.setText(emoji)
            }
        } else {
            etWeather.setText("위치 권한 필요")
        }
    }

    private fun uploadDiaryToFirestore() {
        val title = etTitle.text.toString().trim()
        val content = etContent.text.toString().trim()
        val weather = etWeather.text.toString().trim()
        if (title.isEmpty()) { etTitle.error = "제목을 입력하세요"; return }
        if (content.isEmpty()) { etContent.error = "내용을 입력하세요"; return }

        firestore.collection("diaries")
            .whereEqualTo("date", currentDate)
            .get()
            .addOnSuccessListener { snap ->
                if (snap.isEmpty) {
                    val map = mapOf(
                        "title" to title,
                        "text" to content,
                        "date" to currentDate,
                        "weather" to weather,
                        "emoji" to selectedEmojiId,
                        "image" to (selectedImageUri?.toString() ?: "")
                    )
                    firestore.collection("diaries")
                        .add(map)
                        .addOnSuccessListener {
                            Toast.makeText(this, "일기 저장됨", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "저장 실패: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                } else {
                    Toast.makeText(this, "오늘($currentDate) 이미 작성됨", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "조회 실패: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun AppCompatActivity.getBorderlessSelectableDrawable(): Drawable? {
        val attrs = intArrayOf(android.R.attr.selectableItemBackgroundBorderless)
        val ta = theme.obtainStyledAttributes(attrs)
        val dr = ta.getDrawable(0)
        ta.recycle()
        return dr
    }

    companion object {
        private const val PERMISSION_REQUEST_LOCATION = 100
    }
}
