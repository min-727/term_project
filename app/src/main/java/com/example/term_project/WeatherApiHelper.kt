package com.example.term_project
import android.Manifest
import android.content.pm.PackageManager
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

/**
 * 위치 기반 단기예보 조회 헬퍼
 * 이제 SKY/PTY 값을 해석해 이모지로 반환합니다.
 */
object WeatherApiHelper {
    private const val SERVICE_KEY = "hwxoYncUf7iua1/vLy8ypCv2X5mcGvupImVHQVR+pEUu60aWdJi4+G94mBw8x99/O9AU5fUd41+HOXmv4YqQeA=="
    private const val BASE_URL = "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst"

    /**
     * 현 위치 기반 단기예보 조회
     * 요청 URL과 원본 JSON을 로그로 출력
     * callback으로 날씨 이모지를 전달
     */
    fun fetchWeather(activity: AppCompatActivity, callback: (String) -> Unit) {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            callback("권한 필요")
            return
        }

        val fusedClient = LocationServices.getFusedLocationProviderClient(activity)
        val req = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            interval = 0
            fastestInterval = 0
            numUpdates = 1
        }

        fusedClient.requestLocationUpdates(req, object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                fusedClient.removeLocationUpdates(this)
                val loc = result.lastLocation
                if (loc != null) {
                    val (nx, ny) = GridConverter.latLngToGrid(loc.latitude, loc.longitude)
                    val (baseDate, baseTime) = getBaseDateTime()
                    val url = BASE_URL.toHttpUrlOrNull()!!.newBuilder().apply {
                        addQueryParameter("serviceKey", SERVICE_KEY)
                        addQueryParameter("numOfRows", "10")
                        addQueryParameter("pageNo", "1")
                        addQueryParameter("dataType", "JSON")
                        addQueryParameter("base_date", baseDate)
                        addQueryParameter("base_time", baseTime)
                        addQueryParameter("nx", nx.toString())
                        addQueryParameter("ny", ny.toString())
                    }.build().toString()
                    Log.d("WeatherApiHelper", "Request URL: $url")

                    OkHttpClient().newCall(Request.Builder().url(url).build())
                        .enqueue(object : Callback {
                            override fun onFailure(call: Call, e: IOException) {
                                Log.e("WeatherApiHelper", "Request failed", e)
                                activity.runOnUiThread { callback("오류") }
                            }

                            override fun onResponse(call: Call, response: Response) {
                                val json = response.body?.string()
                                Log.d("WeatherApiHelper", "Raw weather response: $json")
                                val emoji = parseWeatherEmoji(json)
                                activity.runOnUiThread { callback(emoji) }
                            }
                        })
                } else {
                    activity.runOnUiThread { callback("위치 오류") }
                }
            }
        }, Looper.getMainLooper())
    }

    /**
     * 발표 시각(0200,0500,...2300)+10분 기준으로 최근 base_date/base_time 계산
     */
    private fun getBaseDateTime(): Pair<String, String> {
        val now = Calendar.getInstance()
        val announce = listOf(2,5,8,11,14,17,20,23)
        val hour = now.get(Calendar.HOUR_OF_DAY)
        val minute = now.get(Calendar.MINUTE)
        val cal = now.clone() as Calendar
        val baseHour = announce.lastOrNull { h -> (hour>h) || (hour==h && minute>=10) } ?: run {
            cal.add(Calendar.DATE, -1)
            23
        }
        cal.set(Calendar.HOUR_OF_DAY, baseHour)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        val df = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val tf = SimpleDateFormat("HHmm", Locale.getDefault())
        return df.format(cal.time) to tf.format(cal.time)
    }

    /**
     * JSON에서 SKY/PTY 코드만 추출해 이모지로 매핑
     */
    private fun parseWeatherEmoji(json: String?): String {
        if (json.isNullOrBlank()) return ""
        return try {
            val arr = JSONObject(json)
                .getJSONObject("response").getJSONObject("body")
                .getJSONObject("items").getJSONArray("item")
            var sky: Int? = null
            var pty: Int? = null
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                when (obj.getString("category")) {
                    "SKY" -> sky = obj.getString("fcstValue").toIntOrNull()
                    "PTY" -> pty = obj.getString("fcstValue").toIntOrNull()
                }
            }
            when {
                pty != null && pty > 0 -> "🌧️"
                sky == 1 -> "☀️"
                sky == 2 -> "🌤️"
                sky == 3 -> "⛅️"
                sky == 4 -> "☁️"
                else -> ""
            }
        } catch (e: Exception) {
            ""
        }
    }
}

/**
 * 위경도 ↔︎ 기상청 격자 변환
 */
object GridConverter {
    private const val RE = 6371.00877
    private const val GRID = 5.0
    private const val SLAT1 = 30.0
    private const val SLAT2 = 60.0
    private const val OLON = 126.0
    private const val OLAT = 38.0
    private const val XO = 210.0
    private const val YO = 675.0
    private val DEGRAD = Math.PI / 180.0
    private val sn: Double
    private val sf: Double
    private val ro: Double
    init {
        val slat1 = SLAT1 * DEGRAD
        val slat2 = SLAT2 * DEGRAD
        val olon = OLON * DEGRAD
        val olat = OLAT * DEGRAD
        val re = RE/GRID
        sn = Math.log(Math.cos(slat1)/Math.cos(slat2)) /
                Math.log(Math.tan(Math.PI*0.25+slat2*0.5)/Math.tan(Math.PI*0.25+slat1*0.5))
        sf = Math.pow(Math.tan(Math.PI*0.25+slat1*0.5), sn)*Math.cos(slat1)/sn
        ro = re*sf/Math.pow(Math.tan(Math.PI*0.25+olat*0.5), sn)
    }
    fun latLngToGrid(lat: Double, lon: Double): Pair<Int, Int> {
        val ra = Math.tan(Math.PI*0.25+lat*DEGRAD*0.5)
        val raCalc = (RE/GRID)*sf/Math.pow(ra, sn)
        var theta = lon*DEGRAD - (OLON*DEGRAD)
        if (theta > Math.PI) theta -= 2*Math.PI
        if (theta < -Math.PI) theta += 2*Math.PI
        theta *= sn
        val x = (raCalc*Math.sin(theta)+XO/GRID+1.5).toInt()
        val y = (ro - raCalc*Math.cos(theta)+YO/GRID+1.5).toInt()
        return x to y
    }
}


