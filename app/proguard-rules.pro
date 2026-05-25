# R8 full mode optimization keep rules
-keep class android.media.MediaCodec { *; }
-keep class android.media.MediaFormat { *; }
-keep class com.google.android.gms.nearby.** { *; }

# Keep all @Keep annotated elements
-keep @android.support.annotation.Keep class *
-keep @androidx.annotation.Keep class *
-keepclassmembers class * {
    @android.support.annotation.Keep *;
    @androidx.annotation.Keep *;
}

# Hilt specific keep rules (in case they are not bundled)
-keep class dagger.hilt.android.internal.managers.** { *; }
-keep class * implements dagger.hilt.internal.GeneratedComponent { *; }
-keep class * implements dagger.hilt.internal.GeneratedComponentManager { *; }
