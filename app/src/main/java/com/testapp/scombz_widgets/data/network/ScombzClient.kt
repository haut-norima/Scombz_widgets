package com.testapp.scombz_widgets.data.network

import android.util.Log
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * ScombZ にHTTPリクエストを送信するクライアント。
 */
class ScombzClient {

    companion object {
        private const val TAG = "ScombzClient"

        const val BASE_URL = "https://scombz.shibaura-it.ac.jp"
        const val LOGIN_URL = "$BASE_URL/login"

        private const val TIMETABLE_PATH = "/lms/timetable"
        private const val HOME_PATH = "/portal/home"
        private const val TASK_PATH = "/lms/task"

        fun timetableUrl(year: Int, termCode: Int): String =
            "$BASE_URL$TIMETABLE_PATH" +
            "?selectDisplayMode=0&risyunen=$year&kikanCd=$termCode&yobiCd=6"

        fun homeUrl(): String = "$BASE_URL$HOME_PATH"
        fun taskUrl(): String = "$BASE_URL$TASK_PATH"
    }

    private var cookies: Map<String, String> = emptyMap()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {}
            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                return this@ScombzClient.cookies.map { (name, value) ->
                    Cookie.Builder()
                        .domain(url.host)
                        .path("/")
                        .name(name)
                        .value(value)
                        .build()
                }
            }
        })
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/129.0 Mobile Safari/537.36"
                )
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "ja,en;q=0.9")
                .build()
            chain.proceed(req)
        }
        .build()

    fun setCookies(cookieString: String) {
        cookies = cookieString.split(";")
            .map { it.trim() }
            .filter { it.contains("=") }
            .associate {
                val parts = it.split("=", limit = 2)
                parts[0].trim() to parts.getOrElse(1) { "" }.trim()
            }
        Log.d(TAG, "Cookies set: ${cookies.keys.joinToString()}")
    }

    fun setCookieMap(cookieMap: Map<String, String>) { cookies = cookieMap }
    fun hasCookies(): Boolean = cookies.isNotEmpty()

    /**
     * 指定URLのHTMLを取得。
     *
     * ログイン判定は「最終リダイレクト先のURL」で行う。
     * ページ本文に "/login" が含まれているかどうかでは判定しない
     * （ログイン済みページのナビにも /login リンクが存在するため誤判定する）。
     */
    fun fetchHtml(url: String): Result<String> {
        return try {
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()

            // OkHttp が followRedirects=true の場合、response.request.url は
            // リダイレクト後の最終 URL を指す
            val finalUrl = response.request.url.toString()
            val body = response.body?.string() ?: ""

            Log.d(TAG, "fetch: $url → finalUrl: $finalUrl (${response.code}) bodyLen=${body.length}")

            when {
                // 最終URLがログインページ = 未ログイン or セッション切れ
                finalUrl.contains("/login") ->
                    Result.failure(SessionExpiredException("セッションが切れています。再ログインしてください。"))

                response.isSuccessful ->
                    Result.success(body)

                else ->
                    Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchHtml error: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun fetchTimetable(year: Int, termCode: Int): Result<String> = fetchHtml(timetableUrl(year, termCode))
    fun fetchHomePage(): Result<String> = fetchHtml(homeUrl())
    fun fetchTaskPage(): Result<String> = fetchHtml(taskUrl())
}

class SessionExpiredException(message: String) : Exception(message)
