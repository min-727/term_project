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
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class DiaryViewActivity : AppCompatActivity() {

    private lateinit var etTitle: EditText
    private lateinit var etWeather: EditText
    private lateinit var etContent: EditText
    private lateinit var ivAddImage: ImageView
    private lateinit var ivPhoto: ImageView
    private lateinit var btnMic: ImageButton
    private lateinit var llEmojis: LinearLayout
    private lateinit var btnSub: ImageButton
    private lateinit var btnEval: ImageButton
    private lateinit var tflite: Interpreter

    private lateinit var pickImageLauncher: ActivityResultLauncher<String>
    private lateinit var speechLauncher: ActivityResultLauncher<Intent>

    private val firestore = FirebaseFirestore.getInstance()
    private var selectedImageUri: Uri? = null
    private var selectedEmojiId: Int = 2131230828
    private var lastSelectedButton: ImageButton? = null

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
        btnEval = findViewById(R.id.btnEval)

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
                        lastSelectedButton = btn
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
            val button = findViewById<ImageButton>(id)
            button.setOnClickListener {
                // 기존 배경 제거
                lastSelectedButton?.background = getBorderlessSelectableDrawable()

                // 새 배경 적용
                button.background = ContextCompat.getDrawable(this, R.drawable.bg_emoji_selected)
                lastSelectedButton = button
                selectedEmojiId = id
            }
        }

// TFLite 모델 로딩
        val model = loadModelFile("text2emoji_converter.tflite")
        val options = Interpreter.Options()
        tflite = Interpreter(model, options)

// TFLite 분석 버튼
        btnEval.setOnClickListener {
            val inputText = etContent.text.toString().trim()
            if (inputText.isNotEmpty()) {
                val input = arrayOf(arrayOf(inputText))
                val output = Array(1) { FloatArray(1) }
                tflite.run(input, output)

                val score = output[0][0]
                val newEmojiId = when {
                    score < 0.2f -> 2131230828
                    score < 0.4f -> 2131230829
                    score < 0.6f -> 2131230830
                    score < 0.8f -> 2131230831
                    else         -> 2131230832
                }

                // 기존 선택 배경 제거
                lastSelectedButton?.background = getBorderlessSelectableDrawable()

                // 새 선택 배경 적용
                val newButton = findViewById<ImageButton>(newEmojiId)
                newButton.background = ContextCompat.getDrawable(this, R.drawable.bg_emoji_selected)

                // 상태 갱신
                lastSelectedButton = newButton
                selectedEmojiId = newEmojiId

                Toast.makeText(this, "감정 점수: $score", Toast.LENGTH_SHORT).show()
                Log.d("EvalInput", "입력 문장: $inputText")
            }
        }

        btnSub.setOnClickListener { uploadDiaryToFirestore() }
    }

    private fun loadModelFile(modelFilename: String): MappedByteBuffer {
        val fileDescriptor = assets.openFd(modelFilename)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
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
                        "emoji" to selectedEmojiId-2131230827,
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
                        "emoji" to selectedEmojiId-2131230827,
                        "image" to (selectedImageUri?.toString() ?: "")
                    )
                    // 그리고 새로 추가
                    firestore.collection("diaries")
                        .add(map)
                        .addOnSuccessListener {
                            Toast.makeText(this, "기존 일기를 덮어쓰고 저장됨${selectedEmojiId}", Toast.LENGTH_SHORT).show()
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