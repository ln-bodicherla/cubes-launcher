# Cubes Contacts ProGuard Rules

# Keep View Binding classes
-keep class com.cubeslauncher.contacts.databinding.** { *; }

# Keep Android lifecycle
-keep class * extends androidx.lifecycle.ViewModel { <init>(...); }
