# Media3 ships consumer rules; keep our serializable models and Hilt entry points.
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class io.github.tukiza7_debug.vibecast.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}
-dontwarn org.slf4j.**
