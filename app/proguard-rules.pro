# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# --- Firebase & Google Play Services ---
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# --- GSON ---
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# --- Project Models ---
-keep class com.inchios.agenda.** { *; }
-keepclassmembers class com.inchios.agenda.** {
  <fields>;
  <methods>;
}

# --- Room ---
-keep class androidx.room.paging.** { *; }
-dontwarn androidx.room.paging.**
