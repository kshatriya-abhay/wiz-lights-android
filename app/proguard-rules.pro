-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *; }

# WorkManager (used by Glance and the widget actions) opens its Room database by reflection:
# Room calls the generated WorkDatabase_Impl's no-argument constructor. Keep it, or the
# shrunk build crashes at startup in androidx.startup.InitializationProvider.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
