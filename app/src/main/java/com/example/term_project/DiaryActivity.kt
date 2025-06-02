package com.example.term_project

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.net.Uri
import android.speech.RecognizerIntent
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.Manifest
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*
import android.widget.Toast


// 이모티콘 선택 시 “원래 터치 피드백 배경”을 되돌리기 위해
// theme 에 정의된 ?attr/selectableItemBackgroundBorderless 를 코드로 얻는 함수
private fun AppCompatActivity.getBorderlessSelectableDrawable(): Drawable? {
    val attrs = intArrayOf(android.R.attr.selectableItemBackgroundBorderless)
    val ta = this.theme.obtainStyledAttributes(attrs)
    val drawable = ta.getDrawable(0)
    ta.recycle()
    return drawable
}

class DiaryActivity : AppCompatActivity() {

    // 뷰 참조 변수들
    private lateinit var etTitle: EditText
    private lateinit var etWeather: EditText
    private lateinit var etContent: EditText
    private lateinit var ivAddImage: ImageView
    private lateinit var ivPhoto: ImageView
    private lateinit var btnMic: ImageButton
    private lateinit var llEmojis: LinearLayout
    private lateinit var btnSub: ImageButton

    // 이미지 선택용 런처
    private lateinit var pickImageLauncher: ActivityResultLauncher<String>
    // 음성 인식용 런처
    private lateinit var speechLauncher: ActivityResultLauncher<Intent>

    private val firestore = FirebaseFirestore.getInstance()
    private var selectedImageUri: Uri? = null

    // 선택된 이모티콘 ID 저장 변수
    private var selectedEmojiId: Int = -1
    private lateinit var currentDate: String
    private lateinit var currentTime: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val now = Date()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        currentDate = dateFormat.format(now)
        currentTime = timeFormat.format(now)

        Log.d("DiaryActivity", "현재 날짜: $currentDate, 현재 시간: $currentTime")


        setContentView(R.layout.activity_diary)


        // 뷰 바인딩
        etTitle   = findViewById(R.id.etTitle)
        etWeather = findViewById(R.id.etWeather)
        etContent = findViewById(R.id.etContent)
        ivAddImage = findViewById(R.id.ivAddImage)
        ivPhoto    = findViewById(R.id.ivPhoto)
        btnMic     = findViewById(R.id.btnMic)
        llEmojis   = findViewById(R.id.llEmojis)
        btnSub  = findViewById<ImageButton>(R.id.btnSub)

        // 1) 런타임에 위치 권한이 없으면 요청
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                PERMISSION_REQUEST_LOCATION
            )
        } else {
            // 2) 권한이 이미 허용되어 있으면 곧바로 WeatherApiHelper 호출
            WeatherApiHelper.fetchWeather(this) { weatherText ->
                // 받아온 기온(또는 오류 메시지)을 etWeather에 세팅
                etWeather.setText(weatherText)
            }
        }

        // (필요 시) Eval/Sub 버튼 바인딩
        // val btnEval = findViewById<ImageButton>(R.id.btnEval)

        // 1) 이미지 선택 로직 (기존 코드)
        pickImageLauncher = registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            uri?.let {
                ivPhoto.setImageURI(it)
                ivPhoto.visibility = View.VISIBLE
                ivAddImage.visibility = View.GONE
                selectedImageUri = it
            }
        }
        ivAddImage.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
        ivPhoto.setOnClickListener {
            ivPhoto.setImageDrawable(null)
            ivPhoto.visibility = View.GONE
            ivAddImage.visibility = View.VISIBLE
            selectedImageUri = null
        }

        // 2) 음성 인식 로직 (기존 코드)
        speechLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val matches = result.data!!
                    .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                if (!matches.isNullOrEmpty()) {
                    val recognizedText = matches[0]
                    etContent.append(recognizedText)
                    etContent.append("\n")
                }
            }
        }
        btnMic.setOnClickListener {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "음성으로 일기 내용을 입력하세요")
            }
            try {
                speechLauncher.launch(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 3) 이모티콘 버튼 클릭 리스너 등록 및 선택 상태 처리
        for (i in 1..5) {
            val emojiBtnId = resources.getIdentifier("btnEmoji$i", "id", packageName)
            val emojiBtn = findViewById<ImageButton>(emojiBtnId)

            emojiBtn.setOnClickListener {
                // 이전에 선택된 이모티콘이 있으면, 배경을 “원래 터치 피드백”으로 복원
                if (selectedEmojiId != -1 && selectedEmojiId != emojiBtnId) {
                    val prevBtn = findViewById<ImageButton>(selectedEmojiId)
                    // 테마에서 제공하는 default ripple 배경을 가져와서 원상복구
                    prevBtn.background = getBorderlessSelectableDrawable()
                }

                // 현재 클릭된 버튼에는 녹색 테두리 강조 적용
                emojiBtn.background = ContextCompat.getDrawable(
                    this,
                    R.drawable.bg_emoji_selected
                )

                // 선택된 이모티콘 ID 저장
                selectedEmojiId = emojiBtnId

            }
        }

        btnSub.setOnClickListener {
            uploadDiaryToFirestore()
        }

    }
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_LOCATION) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                WeatherApiHelper.fetchWeather(this) { weatherText ->
                    etWeather.setText(weatherText)
                }
            } else {
                etWeather.setText("위치 권한이 필요합니다")
            }
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_LOCATION = 100
    }

    private fun uploadDiaryToFirestore() {
        // 1) 각 필드 값 가져오기
        val titleText = etTitle.text.toString().trim()
        val contentText = etContent.text.toString().trim()
        val weatherText = etWeather.text.toString().trim()
        val emojiId = selectedEmojiId

        // 2) selectedImageUri를 문자열로 변환 (null인 경우는 빈 문자열 "")
        val imageUriString = selectedImageUri?.toString() ?: ""

        // 3) 필수 입력 체크
        if (titleText.isEmpty()) {
            etTitle.error = "제목을 입력하세요"
            return
        }
        if (contentText.isEmpty()) {
            etContent.error = "내용을 입력하세요"
            return
        }

        // 4) 업로드할 데이터 맵 구성
        val diaryMap = hashMapOf(
            "title"   to titleText,
            "text"    to contentText,
            "date"    to currentDate,
            "weather" to weatherText,
            "emoji"   to emojiId,
            "image"   to imageUriString
        )

        // 5) Firestore 콜렉션에 추가
        firestore.collection("diaries")
            .add(diaryMap)
            .addOnSuccessListener { documentRef ->
                Toast.makeText(this, "일기가 저장되었습니다.", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "저장 실패: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

}