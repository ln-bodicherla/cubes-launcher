# Cubes Keyboard ProGuard Rules

# Keep View Binding classes
-keep class com.cubeslauncher.keyboard.databinding.** { *; }

# Keep InputMethodService
-keep class com.cubeslauncher.keyboard.** extends android.inputmethodservice.InputMethodService { *; }
