package com.example.diary_write

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.renderscript.RenderScript.Priority
import android.widget.EditText
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.*
import com.google.android.gms.tasks.CancellationTokenSource
import java.io.IOException
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONArray
import com.google.android.gms.location.LocationRequest
import java.net.URLEncoder

object WeatherApiHelper {

    // 공공데이터포털에서 발급받은 키(원본, 디코딩 상태) 그대로 입력하세요
    private const val RAW_SERVICE_KEY = "Voz2HbYkOmKKf2s55dWhJDlwybCUXz9SiSm/eS3YD18JBC7odmBwOPUk5Uk63LauUIWv98mHm8g8j9kd6xodKg=="

    /**
     * 위치 권한 확인 → 위치 조회 → 기상청 API 호출 → 결과 문자열(onResult) 반환
     */
    fun fetchWeather(
        context: Context,
        onResult: (weatherText: String) -> Unit
    ) {
        // 1. 위치 권한 확인
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            onResult("위치 권한이 없습니다")
            return
        }

        val fusedClient: FusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(context)

        // 2. 마지막 위치 시도
        fusedClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    callWeatherApi(location.latitude, location.longitude, onResult)
                } else {
                    // 마지막 위치가 없는 경우 단발성 위치 요청
                    requestOneTimeLocation(context, fusedClient, onResult)
                }
            }
            .addOnFailureListener {
                requestOneTimeLocation(context, fusedClient, onResult)
            }
    }

    /**
     * 마지막 위치 null 시 한 번만 정확한 위치 요청
     */
    private fun requestOneTimeLocation(
        context: Context,
        fusedClient: FusedLocationProviderClient,
        onResult: (weatherText: String) -> Unit
    ) {
        // 권한 재확인
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            onResult("위치 권한이 없습니다")
            return
        }

        val cts = CancellationTokenSource()
        fusedClient.getCurrentLocation(
            LocationRequest.PRIORITY_HIGH_ACCURACY,
            cts.token
        ).addOnSuccessListener { location: Location? ->
            if (location != null) {
                callWeatherApi(location.latitude, location.longitude, onResult)
            } else {
                onResult("위치 정보를 가져올 수 없습니다")
            }
        }.addOnFailureListener {
            onResult("위치 요청 중 오류가 발생했습니다")
        }
    }

    /**
     * 위경도 → 기상청 격자(nx, ny) 변환 후, 초단기실황(getUltraSrtNcst) API 호출
     */
    private fun callWeatherApi(
        lat: Double,
        lon: Double,
        onResult: (weatherText: String) -> Unit
    ) {
        // 1) 위경도 → 격자(nx, ny) 변환
        val (nx, ny) = latLonToGrid(lat, lon)

        // 2) base_date 계산 (yyyyMMdd)
        val now = Calendar.getInstance()
        val dateFmt = SimpleDateFormat("yyyyMMdd", Locale.KOREAN)
        val baseDate = dateFmt.format(now.time)

        // 3) base_time 계산 (“HH00” 형식)
        val hour = now.get(Calendar.HOUR_OF_DAY)      // 0~23
        val minute = now.get(Calendar.MINUTE)         // 0~59

        val baseTime = if (minute < 10) {
            // 10분 이전이면 이전 시(00분)
            if (hour == 0) {
                now.add(Calendar.DATE, -1)            // 전날로 날짜 변경
                "2300"
            } else {
                String.format("%02d00", hour - 1)     // 예: 14:05 → "1300"
            }
        } else {
            // 10분 이상이면 현재 정시
            String.format("%02d00", hour)             // 예: 14:12 → "1400"
        }

        // 4) 서비스 키 인코딩
        val encodedKey = URLEncoder.encode(RAW_SERVICE_KEY, "UTF-8")

        // 5) 요청 URL (HTTPS)
        val url =
            "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getUltraSrtNcst" +
                    "?serviceKey=$encodedKey" +
                    "&numOfRows=10&pageNo=1" +
                    "&dataType=JSON" +
                    "&base_date=$baseDate" +
                    "&base_time=$baseTime" +
                    "&nx=$nx&ny=$ny"

        // 6) 비동기 호출 (OkHttp + 코루틴)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient()
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    // 7) JSON 파싱 시도
                    try {
                        val itemsArray: JSONArray = JSONObject(body)
                            .getJSONObject("response")
                            .getJSONObject("body")
                            .getJSONObject("items")
                            .getJSONArray("item")

                        // “category”가 “T1H”(기온)인 값을 찾아 추출
                        var temperature: String? = null
                        for (i in 0 until itemsArray.length()) {
                            val item = itemsArray.getJSONObject(i)
                            if (item.getString("category") == "T1H") {
                                temperature = item.getString("obsrValue")
                                break
                            }
                        }
                        val resultText = if (temperature != null) {
                            "${temperature}℃"
                        } else {
                            "기온 정보를 가져올 수 없습니다"
                        }
                        onResult(resultText)
                    } catch (je: JSONException) {
                        // JSON 파싱 실패 → 응답이 XML일 가능성
                        onResult("날씨 조회 실패(응답 형식 오류): $body")
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
                onResult("날씨 조회 실패: ${e.message}")
            } catch (e: Exception) {
                e.printStackTrace()
                onResult("날씨 조회 실패: ${e.localizedMessage}")
            }
        }
    }

    /**
     * 위경도(latitude, longitude) → 기상청 격자(nx, ny) 변환
     * Lambert Conformal Conic Projection 공식 적용
     */
    private fun latLonToGrid(lat: Double, lon: Double): Pair<Int, Int> {
        val RE = 6371.00877      // 지구 반경(km)
        val GRID = 5.0          // 격자 간격(km)
        val SLAT1 = 30.0        // 표준위도1
        val SLAT2 = 60.0        // 표준위도2
        val OLON = 126.0        // 기준점 경도
        val OLAT = 38.0         // 기준점 위도
        val XO = 210.0 / GRID   // 기준점 X 좌표
        val YO = 675.0 / GRID   // 기준점 Y 좌표

        val DEGRAD = Math.PI / 180.0
        val re = RE / GRID
        val slat1 = SLAT1 * DEGRAD
        val slat2 = SLAT2 * DEGRAD
        val olon = OLON * DEGRAD
        val olat = OLAT * DEGRAD

        var sn = Math.tan(Math.PI * 0.25 + slat2 * 0.5) /
                Math.tan(Math.PI * 0.25 + slat1 * 0.5)
        sn = Math.log(Math.cos(slat1) / Math.cos(slat2)) / Math.log(sn)
        var sf = Math.tan(Math.PI * 0.25 + slat1 * 0.5)
        sf = Math.pow(sf, sn) * Math.cos(slat1) / sn
        var ro = Math.tan(Math.PI * 0.25 + olat * 0.5)
        ro = re * sf / Math.pow(ro, sn)

        val ra = Math.tan(Math.PI * 0.25 + (lat * DEGRAD) * 0.5)
        val raPow = re * sf / Math.pow(ra, sn)
        var theta = lon * DEGRAD - olon
        if (theta > Math.PI) theta -= 2.0 * Math.PI
        if (theta < -Math.PI) theta += 2.0 * Math.PI
        theta *= sn

        val x = (raPow * Math.sin(theta) + XO + 0.5).toInt()
        val y = (ro - raPow * Math.cos(theta) + YO + 0.5).toInt()
        return Pair(x, y)
    }
}