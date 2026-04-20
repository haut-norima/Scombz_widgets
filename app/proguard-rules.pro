# ScombZ Widgets ProGuard Rules

# デバッグ用にスタックトレースを保持
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Gson ---
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
# アプリのデータモデルクラスを保持（Gsonがフィールド名を使用するため）
-keep class com.testapp.scombz_widgets.data.model.** { *; }
-keep class com.testapp.scombz_widgets.data.repository.**$*Json { *; }

# --- Jsoup ---
-keep public class org.jsoup.** { *; }

# --- OkHttp ---
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# --- Kotlin Coroutines ---
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# --- WorkManager ---
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# --- Glance Widgets ---
-keep class androidx.glance.** { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidget { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }

# --- DataStore ---
-keep class androidx.datastore.** { *; }

# --- WebView ---
-keepclassmembers class * extends android.webkit.WebViewClient {
    public void *(android.webkit.WebView, java.lang.String, android.graphics.Bitmap);
    public boolean *(android.webkit.WebView, java.lang.String);
}

# --- SSL (BusRepository で使用) ---
-keep class javax.net.ssl.** { *; }
-dontwarn javax.net.ssl.**
