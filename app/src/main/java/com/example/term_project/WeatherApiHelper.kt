package com.example.term_project
import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 코루틴 기반 위치 단기예보 조회 헬퍼
 * SKY/PTY 코드 -> 이모지 매핑까지 지원
 */
object WeatherApiHelper {
    private const val SERVICE_KEY = "hwxoYncUf7iua1/vLy8ypCv2X5mcGvupImVHQVR+pEUu60aWdJi4+G94mBw8x99/O9AU5fUd41+HOXmv4YqQeA=="
    private const val BASE_URL = "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst"

    /**
     * 단기예보를 비동기로 조회하고, 결과 이모지를 반환
     */
    suspend fun fetchWeatherEmoji(activity: AppCompatActivity): String {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return "권한 필요"
        }
        val fusedClient = LocationServices.getFusedLocationProviderClient(activity)
        val loc = getCurrentLocationSuspend(fusedClient)
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

        val response = withContext(Dispatchers.IO) {
            OkHttpClient().newCall(Request.Builder().url(url).build()).execute()
        }
        val bodyString = response.body?.string()
        Log.d("WeatherApiHelper", "Raw weather response: $bodyString")
        return parseWeatherEmoji(bodyString)
    }

    /**
     * FusedLocationProviderClient에서 첫 위치를 suspendCoroutine로 수신
     */
    private suspend fun getCurrentLocationSuspend(client: FusedLocationProviderClient): Location =
        suspendCancellableCoroutine { cont ->
            val req = LocationRequest.create().apply {
                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
                numUpdates = 1
                interval = 0
                fastestInterval = 0
            }
            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    client.removeLocationUpdates(this)
                    val loc = result.lastLocation
                    if (loc != null) cont.resume(loc)
                    else cont.resumeWithException(Exception("위치 정보를 얻을 수 없습니다"))
                }
            }
            client.requestLocationUpdates(req, callback, Looper.getMainLooper())
            cont.invokeOnCancellation { client.removeLocationUpdates(callback) }
        }

    /**
     * 발표 시각(0200,0500,...2300)+10분 기준 가장 최근 base_date/time 반환
     */
    private fun getBaseDateTime(): Pair<String, String> {
        val now = Calendar.getInstance()
        val announce = listOf(2, 5, 8, 11, 14, 17, 20, 23)
        val hour = now.get(Calendar.HOUR_OF_DAY)
        val minute = now.get(Calendar.MINUTE)
        val cal = now.clone() as Calendar
        val baseHour = announce.lastOrNull { h ->
            (hour > h) || (hour == h && minute >= 10)
        } ?: run { cal.add(Calendar.DATE, -1); 23 }

        cal.set(Calendar.HOUR_OF_DAY, baseHour)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        val df = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val tf = SimpleDateFormat("HHmm", Locale.getDefault())
        return df.format(cal.time) to tf.format(cal.time)
    }

    /**
     * JSON에서 SKY/PTY 파싱 후 이모지 반환
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
 * 위경도 ↔︎ 기상청 격자 변환 유틸
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
    private val slat1 = SLAT1 * DEGRAD
    private val slat2 = SLAT2 * DEGRAD
    private val olon = OLON * DEGRAD
    private val olat = OLAT * DEGRAD
    private val re = RE / GRID
    private val sn: Double
    private val sf: Double
    private val ro: Double

    init {
        sn = Math.log(Math.cos(slat1) / Math.cos(slat2)) /
                Math.log(Math.tan(Math.PI * 0.25 + slat2 * 0.5) /
                        Math.tan(Math.PI * 0.25 + slat1 * 0.5))
        sf = Math.pow(Math.tan(Math.PI * 0.25 + slat1 * 0.5), sn) * Math.cos(slat1) / sn
        ro = re * sf / Math.pow(Math.tan(Math.PI * 0.25 + olat * 0.5), sn)
    }

    fun latLngToGrid(lat: Double, lon: Double): Pair<Int, Int> {
        val ra = Math.tan(Math.PI * 0.25 + lat * DEGRAD * 0.5)
        val raCalc = re * sf / Math.pow(ra, sn)
        var theta = lon * DEGRAD - olon
        if (theta > Math.PI) theta -= 2.0 * Math.PI
        if (theta < -Math.PI) theta += 2.0 * Math.PI
        theta *= sn
        val x = (raCalc * Math.sin(theta) + XO / GRID + 1.5).toInt()
        val y = (ro - raCalc * Math.cos(theta) + YO / GRID + 1.5).toInt()
        return x to y
    }
}



