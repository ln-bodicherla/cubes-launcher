# Cubes Messages ProGuard Rules

# Keep View Binding classes
-keep class com.cubeslauncher.messages.databinding.** { *; }

# Keep Android lifecycle
-keep class * extends androidx.lifecycle.ViewModel { <init>(...); }

# Keep Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}

# Keep broadcast receivers
-keep class com.cubeslauncher.messages.receiver.** { *; }

# Keep services
-keep class com.cubeslauncher.messages.service.** { *; }

# Keep ZXing
-keep class com.google.zxing.** { *; }

# Keep WhatsApp integration AAR classes
-keep class go.** { *; }
-keep class whatsmeow.** { *; }
