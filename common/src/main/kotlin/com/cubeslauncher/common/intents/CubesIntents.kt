package com.cubeslauncher.common.intents

object CubesIntents {

    // App IDs (applicationId of each APK)
    const val LAUNCHER_APP_ID = "com.cubeslauncher.app"
    const val PHONE_APP_ID = "com.cubeslauncher.phone"
    const val CONTACTS_APP_ID = "com.cubeslauncher.contacts"
    const val MESSAGES_APP_ID = "com.cubeslauncher.messages"
    const val KEYBOARD_APP_ID = "com.cubeslauncher.keyboard"
    const val CALCULATOR_APP_ID = "com.cubeslauncher.calculator"
    const val CLOCK_APP_ID = "com.cubeslauncher.clock"
    const val WEATHER_APP_ID = "com.cubeslauncher.weather"

    // Legacy pseudo-package names used in DataStore before split
    const val LEGACY_PHONE_PACKAGE = "com.cubeslauncher.PHONE"
    const val LEGACY_CONTACTS_PACKAGE = "com.cubeslauncher.CONTACTS"
    const val LEGACY_MESSAGES_PACKAGE = "com.cubeslauncher.MESSAGES"

    // Inter-app launch actions
    const val ACTION_LAUNCH_PHONE = "com.cubeslauncher.action.LAUNCH_PHONE"
    const val ACTION_LAUNCH_CONTACTS = "com.cubeslauncher.action.LAUNCH_CONTACTS"
    const val ACTION_LAUNCH_MESSAGES = "com.cubeslauncher.action.LAUNCH_MESSAGES"
    const val ACTION_LAUNCH_CALCULATOR = "com.cubeslauncher.action.LAUNCH_CALCULATOR"
    const val ACTION_LAUNCH_CLOCK = "com.cubeslauncher.action.LAUNCH_CLOCK"
    const val ACTION_LAUNCH_WEATHER = "com.cubeslauncher.action.LAUNCH_WEATHER"

    // Theme broadcast
    const val ACTION_THEME_CHANGED = "com.cubeslauncher.action.THEME_CHANGED"
    const val EXTRA_THEME_MODE = "theme_mode"
    const val EXTRA_ACCENT_COLOR = "accent_color"
}
