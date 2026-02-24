package com.cubeslauncher.app.appslist

import com.cubeslauncher.app.model.AppInfo

sealed class AppItem {
    data class Header(val letter: String) : AppItem()
    data class App(val info: AppInfo) : AppItem()
}
