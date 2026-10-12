# Cubes Clock ProGuard Rules

# Keep View Binding classes
-keep class com.cubeslauncher.clock.databinding.** { *; }

# Keep Android lifecycle
-keep class * extends androidx.lifecycle.ViewModel { <init>(...); }
