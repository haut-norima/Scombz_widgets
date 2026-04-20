package com.testapp.scombz_widgets.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.testapp.scombz_widgets.data.model.BusTimesheet
import com.testapp.scombz_widgets.data.model.NextBusInfo
import com.testapp.scombz_widgets.data.parser.BusDataParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.cert.X509Certificate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class BusRepository(private val context: Context) {

    companion object {
        private const val BUS_API_URL = "http://bus.shibaura-it.ac.jp/db/bus_data.json"
        private const val PREF_NAME = "bus_data"
        private const val PREF_JSON = "bus_json"
        private const val PREF_LAST_FETCH = "bus_last_fetch"
        private const val CACHE_DURATION_MINUTES = 15 // 15分ごとに再取得
    }

    private val prefs: SharedPreferences
        get() = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    // TLS証明書に問題があるため、信頼を緩和したクライアント
    private val client: OkHttpClient by lazy {
        try {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())

            OkHttpClient.Builder()
                .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
        } catch (_: Exception) {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
        }
    }

    /**
     * バスデータをAPIから取得してキャッシュ
     */
    suspend fun syncBusData(): Result<List<BusTimesheet>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(BUS_API_URL)
                .build()
            val response = client.newCall(request).execute()
            val json = response.body?.string() ?: return@withContext Result.failure(
                Exception("レスポンスが空です")
            )

            // キャッシュに保存
            prefs.edit()
                .putString(PREF_JSON, json)
                .putString(PREF_LAST_FETCH, LocalDateTime.now().toString())
                .apply()

            val timesheets = BusDataParser.parse(json)
            Result.success(timesheets)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * キャッシュからバスデータを読み込む
     */
    fun loadCachedData(): List<BusTimesheet> {
        val json = prefs.getString(PREF_JSON, null) ?: return emptyList()
        return try {
            BusDataParser.parse(json)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * キャッシュが新しければキャッシュを使い、古ければAPIから取得
     */
    suspend fun getTimesheets(): List<BusTimesheet> {
        val cached = loadCachedData()
        val lastFetch = prefs.getString(PREF_LAST_FETCH, null)

        val needsRefresh = lastFetch == null || try {
            val fetchTime = LocalDateTime.parse(lastFetch)
            fetchTime.plusMinutes(CACHE_DURATION_MINUTES.toLong()).isBefore(LocalDateTime.now())
        } catch (_: Exception) {
            true
        }

        return if (needsRefresh || cached.isEmpty()) {
            syncBusData().getOrDefault(cached)
        } else {
            cached
        }
    }

    /**
     * 次のバスを取得（駅→学校）
     */
    suspend fun getNextBusToSchool(): NextBusInfo? {
        val now = LocalDateTime.now()
        val timesheets = getTimesheets()
        val sheet = BusDataParser.selectTimesheetForToday(timesheets, now.dayOfWeek.value)
            ?: return null
        return BusDataParser.findNextBus(sheet, now.hour, now.minute, toSchool = true)
    }

    /**
     * 次のバスを取得（学校→駅）
     */
    suspend fun getNextBusFromSchool(): NextBusInfo? {
        val now = LocalDateTime.now()
        val timesheets = getTimesheets()
        val sheet = BusDataParser.selectTimesheetForToday(timesheets, now.dayOfWeek.value)
            ?: return null
        return BusDataParser.findNextBus(sheet, now.hour, now.minute, toSchool = false)
    }
}
