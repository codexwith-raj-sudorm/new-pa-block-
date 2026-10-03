# Jarvis release hardening rules.
# Keep the rules narrow: Android Gradle Plugin/AndroidX/OkHttp already ship
# consumer rules; these protect the few areas Jarvis reflects or instantiates
# from framework entry points while allowing R8 to shrink the rest.

-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*

# Room database, DAOs, entities and generated implementations.
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class com.jarvis.app.backend.data.** { *; }
-keep @androidx.room.Dao class com.jarvis.app.backend.data.** { *; }
-keep @androidx.room.Dao interface com.jarvis.app.backend.data.** { *; }
-keep class com.jarvis.app.backend.data.**_Impl { *; }
-keepclassmembers class com.jarvis.app.backend.data.** {
    <fields>;
    <methods>;
}

# JSON-adjacent model classes. Jarvis currently parses/builds JSON with
# org.json explicit string keys (not Gson/Moshi reflection), but these rules
# keep externally documented action/schema models stable for future refactors.
-keep class com.jarvis.app.backend.brain.PlayMedia { *; }
-keep class com.jarvis.app.backend.brain.MediaPick { *; }
-keep class com.jarvis.app.backend.net.RepoInfo { *; }
-keep class com.jarvis.app.backend.net.RepoBrief { *; }
-keep class com.jarvis.app.backend.net.RunBrief { *; }
-keep class com.jarvis.app.backend.net.IssueBrief { *; }

# Framework-instantiated app components referenced from AndroidManifest.xml.
-keep class com.jarvis.app.frontend.screens.** extends android.app.Activity { *; }
-keep class com.jarvis.app.backend.system.** extends android.app.Service { *; }
-keep class com.jarvis.app.backend.system.** extends android.content.BroadcastReceiver { *; }
-keep class com.jarvis.app.frontend.widgets.** extends android.appwidget.AppWidgetProvider { *; }

# Kotlin coroutines/debug metadata can be absent in optimized builds.
-dontwarn kotlinx.coroutines.debug.**

# Optional TLS providers that OkHttp probes for at runtime.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**
