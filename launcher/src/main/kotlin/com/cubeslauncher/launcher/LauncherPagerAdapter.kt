package com.cubeslauncher.launcher

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.cubeslauncher.launcher.appslist.AppsListFragment
import com.cubeslauncher.launcher.home.HomeFragment

class LauncherPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment = when (position) {
        0 -> HomeFragment()
        1 -> AppsListFragment()
        else -> throw IllegalArgumentException("Invalid position $position")
    }
}
