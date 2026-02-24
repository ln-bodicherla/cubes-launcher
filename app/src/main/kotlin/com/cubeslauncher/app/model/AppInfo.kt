package com.cubeslauncher.app.model

import android.graphics.drawable.Drawable

enum class TileSize {
    SMALL,  // 1×1: spans 1 col, 1 row
    MEDIUM, // 2×2: spans 2 cols, 2 rows
    WIDE    // 3×2: spans 3 cols, 2 rows
}

data class AppInfo(
    val label: String,
    val packageName: String,
    val icon: Drawable,
    val tileSize: TileSize = TileSize.SMALL
)
