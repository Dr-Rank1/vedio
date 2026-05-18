# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Jsoup
-keep public class org.jsoup.** { *; }

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# ViewBinding
-keep class com.videosaver.databinding.** { *; }

# App models
-keep class com.videosaver.HistoryItem { *; }
-keep class com.videosaver.PlatformTheme { *; }

# Prevent stripping R8 from crashing on missing classes
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.**
-dontwarn org.jspecify.annotations.**
