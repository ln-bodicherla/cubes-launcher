package com.cubeslauncher.launcher.appslist

import com.cubeslauncher.launcher.model.AppInfo

sealed class AppItem {
    data class Header(val letter: String) : AppItem()
    data class App(val info: AppInfo) : AppItem()
}
