package com.cubeslauncher.clock.model

data class Alarm(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true,
    val repeatDays: Set<Int> = emptySet(), // Calendar.MONDAY, etc.
    val label: String = ""
)
