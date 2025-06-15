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

class DiaryViewActivity : AppCompatActivity() {

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
    private var selectedEmojiId: Int = 2131230829

    private lateinit var currentDate: String
    private lateinit var currentTime: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diary)


        // 뷰 바인딩
        etTitle = findViewById(R.id.etTitle)
        etWeather = findViewById(R.id.etWeather)
        etContent = findViewById(R.id.etContent)
        ivAddImage = findViewById(R.id.ivAddImage)
        ivPhoto = findViewById(R.id.ivPhoto)
        btnMic = findViewById(R.id.btnMic)
        llEmojis = findViewById(R.id.llEmojis)
        btnSub = findViewById(R.id.btnSub)

        // 전달된 날짜
        val dateStr = intent.getStringExtra("date") ?: return
        // 2) currentDate를 dateStr로 설정
        currentDate = dateStr

        // Firestore에서 날짜로 쿼리
        firestore.collection("diaries")
            .whereEqualTo("date", dateStr)
            .get()
            .addOnSuccessListener { snaps ->
                if (!snaps.isEmpty) {
                    val doc = snaps.documents[0]
                    etTitle.setText(doc.getString("title"))
                    etContent.setText(doc.getString("text"))
                    etWeather.setText(doc.getString("weather"))
                    val emojiValue = (doc.getLong("emoji") ?: 3).toInt()
                    val btnId = resources.getIdentifier("btnEmoji$emojiValue", "id", packageName)
                    if (1 <= emojiValue && emojiValue <= 5) {
                        selectedEmojiId = btnId
                        val btn = findViewById<ImageButton>(btnId)
                        btn.background = ContextCompat.getDrawable(this, R.drawable.bg_emoji_selected)
                    }
                }
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
                        "emoji" to selectedEmojiId-2131230824,
                        "image" to (selectedImageUri?.toString() ?: "")
                    )
                    firestore.collection("diaries")
                        .add(map)
                        .addOnSuccessListener {
                            Toast.makeText(this, "일기 저장됨", Toast.LENGTH_SHORT).show()
                            val intent = Intent(this, MainActivity::class.java)
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            startActivity(intent)
                            finish()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "저장 실패: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                } else {
                    // (2) 중복인 경우: 기존 문서 전부 삭제
                    for (docSnap in snap.documents) {
                        docSnap.reference.delete()
                    }
                    val map = mapOf(
                        "title" to title,
                        "text" to content,
                        "date" to currentDate,
                        "weather" to weather,
                        "emoji" to selectedEmojiId-2131230824,
                        "image" to (selectedImageUri?.toString() ?: "")
                    )
                    // 그리고 새로 추가
                    firestore.collection("diaries")
                        .add(map)
                        .addOnSuccessListener {
                            Toast.makeText(this, "기존 일기를 덮어쓰고 저장됨", Toast.LENGTH_SHORT).show()
                            val intent = Intent(this, MainActivity::class.java)
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            startActivity(intent)
                            finish()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "덮어쓰기 실패: ${e.message}", Toast.LENGTH_LONG).show()
                        }
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