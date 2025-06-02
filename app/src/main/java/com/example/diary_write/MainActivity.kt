package com.example.diary_write

import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // MainActivity가 시작되면 바로 DiaryActivity로 이동
        val intent = Intent(this, DiaryActivity::class.java)
        startActivity(intent)
        finish() // MainActivity는 종료해서 뒤로가기 시 돌아오지 않도록
    }
}