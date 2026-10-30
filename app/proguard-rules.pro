# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ---- Snug release rules ----
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Data models are mapped by hand and by Moshi/Firestore; keep their names and members
-keep class com.example.data.model.** { *; }

# Moshi / Retrofit / OkHttp
-keepclassmembers class * { @com.squareup.moshi.* <fields>; }
-keep @com.squareup.moshi.JsonQualifier interface *
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**

# Firebase / Play services ship their own consumer rules; keep Firestore POJO support
-keepclassmembers class * { @com.google.firebase.firestore.PropertyName <fields>; }
