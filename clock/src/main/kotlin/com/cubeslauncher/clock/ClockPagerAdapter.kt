package com.cubeslauncher.clock

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.cubeslauncher.clock.alarm.AlarmFragment
import com.cubeslauncher.clock.stopwatch.StopwatchFragment
import com.cubeslauncher.clock.timer.TimerFragment
import com.cubeslauncher.clock.worldclock.WorldClockFragment

class ClockPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment = when (position) {
        0 -> AlarmFragment()
        1 -> WorldClockFragment()
        2 -> StopwatchFragment()
        3 -> TimerFragment()
        else -> AlarmFragment()
    }
}
