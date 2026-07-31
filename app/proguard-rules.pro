# Add project specific ProGuard rules here.
# Keep application and entry points
-keep class com.example.ElImtiyazApplication { *; }
-keep class com.example.MainActivity { *; }
-keep class com.example.** { *; }

# Keep Hilt and Dagger generated code
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.internal.UnsafeCasts { *; }
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
}

