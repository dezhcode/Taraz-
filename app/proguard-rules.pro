# Retrofit / OkHttp / Moshi
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Moshi reflective adapters need the DTO fields intact
-keep class com.example.data.remote.** { *; }
-keep class com.example.data.Transaction { *; }
-keep class com.example.data.BankCard { *; }
-keep class com.example.data.Loan { *; }
-keep class com.example.data.FinancialGoal { *; }
-keep class com.example.data.Category { *; }
-keepclassmembers class kotlin.Metadata { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Strip debug logging — and any transaction or SMS content it carries — from release builds
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
