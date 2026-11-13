-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *; }

# WorkManager (used by Glance and the widget actions) opens its Room database by reflection:
# Room calls the generated WorkDatabase_Impl's no-argument constructor. Keep it, or the
# shrunk build crashes at startup in androidx.startup.InitializationProvider.
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# Glance creates widget tap handlers by reflection from the class name stored in the
# PendingIntent (actionRunCallback<T>()), through their no-argument constructor. R8 full
# mode removes that constructor, and every widget tap then fails with
# NoSuchMethodException: ...ToggleAction.<init> [].
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }

# WorkManager's default factory creates workers by reflection through this constructor
# (widget actions run as WidgetWork jobs).
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
