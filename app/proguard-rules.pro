# Keep WebRTC native-bridged classes.
-keep class org.webrtc.** { *; }

# kotlinx.serialization: keep generated serializers.
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * { @kotlinx.serialization.Serializable <fields>; }

# Ktor uses reflection for some engine wiring.
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-dontwarn org.slf4j.**
