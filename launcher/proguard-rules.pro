# Cubes Launcher ProGuard Rules

# Keep View Binding classes
-keep class com.cubeslauncher.launcher.databinding.** { *; }

# Keep Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}

# Keep Android lifecycle
-keep class * extends androidx.lifecycle.ViewModel { <init>(...); }

# Keep notification service
-keep class com.cubeslauncher.launcher.statusbar.NotificationService { *; }

# Keep broadcast receivers
-keep class com.cubeslauncher.launcher.receiver.** { *; }
