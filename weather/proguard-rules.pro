# Cubes Weather ProGuard Rules

# Keep View Binding classes
-keep class com.cubeslauncher.weather.databinding.** { *; }

# Keep Android lifecycle
-keep class * extends androidx.lifecycle.ViewModel { <init>(...); }
