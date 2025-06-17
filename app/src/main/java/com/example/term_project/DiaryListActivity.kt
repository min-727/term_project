package com.example.term_project

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class DiaryListActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var adapter: DiaryAdapter
    private var items = mutableListOf<Diary>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diary_list)

        // 년/월
        val tvYearMonth: TextView = findViewById(R.id.tvYearMonth)
        val cal = Calendar.getInstance()
        tvYearMonth.text = "${cal.get(Calendar.YEAR)}년 ${cal.get(Calendar.MONTH)+1}월"

        // RecyclerView & Adapter
        adapter = DiaryAdapter(items) { diary ->
            // 클릭 시 상세보기로 이동
            val intent = Intent(this, DiaryViewActivity::class.java)
            intent.putExtra("date", SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(diary.date))
            startActivity(intent)
        }
        findViewById<RecyclerView>(R.id.rvDiaries).apply {
            layoutManager = LinearLayoutManager(this@DiaryListActivity)
            adapter = this@DiaryListActivity.adapter
        }

        // 버튼 리스너
        findViewById<Button>(R.id.btnCalendar).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnSortTitle).setOnClickListener {
            items.sortBy { it.title }
            adapter.notifyDataSetChanged()
        }
        findViewById<Button>(R.id.btnSortDate).setOnClickListener {
            items.sortByDescending { it.date }
            adapter.notifyDataSetChanged()
        }

        loadDiaries()
    }

    private fun loadDiaries() {
        db.collection("diaries")
            .get()
            .addOnSuccessListener { result ->
                items.clear()
                for (doc in result) {
                    val title = doc.getString("title") ?: ""
                    val text = doc.getString("text") ?: ""
                    val dateStr = doc.getString("date") ?: ""
                    val date: Date = try { dateFormat.parse(dateStr)!! } catch(e: Exception) { Date() }
                    val emoji = (doc.getLong("emoji") ?: 3).toInt()
                    items.add(Diary(title, text, date, emoji))
                }
                adapter.notifyDataSetChanged()
            }
    }
}

// 모델 클래스

data class Diary(
    val title: String,
    val text: String,
    val date: Date,
    val emoji: Int
)

// 어댑터
class DiaryAdapter(
    private val list: List<Diary>,
    private val onClick: (Diary) -> Unit
) : RecyclerView.Adapter<DiaryAdapter.ViewHolder>() {

    inner class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val ivEmoji: android.widget.ImageView = view.findViewById(R.id.ivEmoji)
        val tvTitle: android.widget.TextView = view.findViewById(R.id.tvTitle)
        val tvText: android.widget.TextView = view.findViewById(R.id.tvText)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int) =
        ViewHolder(
            android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_diary, parent, false)
        )

    override fun getItemCount() = list.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]
        // emoji 설정
        val res = when (item.emoji) {
            1 -> R.drawable.ic_emoji_sad
            2 -> R.drawable.ic_emoji_confused
            3 -> R.drawable.ic_emoji_neutral
            4 -> R.drawable.ic_emoji_smile
            5 -> R.drawable.ic_emoji_happy
            else -> R.drawable.ic_emoji_neutral
        }
        holder.ivEmoji.setImageResource(res)
        holder.tvTitle.text = item.title
        holder.tvText.text = item.text

        holder.itemView.setOnClickListener { onClick(item) }
    }
}