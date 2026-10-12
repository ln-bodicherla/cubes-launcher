# Cubes Phone ProGuard Rules

# Keep View Binding classes
-keep class com.cubeslauncher.phone.databinding.** { *; }

# Keep Android lifecycle
-keep class * extends androidx.lifecycle.ViewModel { <init>(...); }

# Keep InCallService
-keep class com.cubeslauncher.phone.CubesInCallService { *; }
