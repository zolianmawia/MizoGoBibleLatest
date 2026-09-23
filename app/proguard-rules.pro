# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Preserve line number information for better crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Razorpay Rules
-keep class com.razorpay.** {*;}
-dontwarn com.razorpay.**

# Firebase & Google Play Services
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# OneSignal
-keep class com.onesignal.** { *; }

# Room
-keep class androidx.room.** { *; }

# Glide
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep public class * extends com.bumptech.glide.module.LibraryGlideModule
-dontwarn com.bumptech.glide.load.resource.bitmap.VideoDecoder

# AndroidX Navigation
-keepclassmembers class * extends androidx.navigation.NavArgs {
    <init>(...);
}

# Keep all data models and DAOs for Room and Firestore
-keep class com.zoliana.khampat.mizobible.data.** { *; }

# Keep MemberInfo and MembershipType for Firestore serialization
-keep class com.zoliana.khampat.mizobible.data.MemberInfo { *; }
-keep class com.zoliana.khampat.mizobible.data.MembershipType { *; }
