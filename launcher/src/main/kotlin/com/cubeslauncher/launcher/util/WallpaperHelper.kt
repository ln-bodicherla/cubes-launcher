package com.cubeslauncher.launcher.util

import android.app.WallpaperManager
import android.content.Context
import android.graphics.drawable.Drawable

object WallpaperHelper {

    fun getWallpaper(context: Context): Drawable? {
        return try {
            val wallpaperManager = WallpaperManager.getInstance(context)
            wallpaperManager.drawable
        } catch (e: Exception) {
            null
        }
    }
}
