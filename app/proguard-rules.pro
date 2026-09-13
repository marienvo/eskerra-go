# Reflection and service metadata used by JGit, Media3, Sentry, and Kotlin serialization.
-keep class org.eclipse.jgit.** { *; }
-keep class androidx.media3.** { *; }
-keep class io.sentry.** { *; }
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keepclassmembers class ** {
    @kotlinx.serialization.Serializable *;
}

# JGit's desktop-only optional integrations are unreachable on Android.
-dontwarn java.lang.ProcessHandle
-dontwarn java.lang.management.**
-dontwarn javax.management.**
-dontwarn org.ietf.jgss.**
-dontwarn org.slf4j.impl.StaticLoggerBinder
