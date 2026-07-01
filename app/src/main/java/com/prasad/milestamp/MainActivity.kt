package com.prasad.milestamp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        val viewPager = findViewById<ViewPager2>(R.id.viewPager)

        // Configuration changed to handle 3 pages instead of 2
        viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 3

            override fun createFragment(position: Int): Fragment {
//                return when (position) {
//                    0 -> TimeFragment()
//                    1 -> ActivaFragment()
//                    else -> CarFragment() // Position 2 matches the Car page logic
//                }
                return TimeFragment()
            }
        }

        // Setup the text label title configurations for each index position
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            when (position) {
                0 -> {
                    tab.text = ""
                    tab.setIcon(R.drawable.ic_time) // Points to your time vector asset
                }
//                1 -> {
//                    tab.text = "Activa"
//                    tab.setIcon(R.drawable.ic_activa1) // Points to your activa vector asset
//                }
//                2 -> {
//                    tab.text = "Car"
//                    tab.setIcon(R.drawable.ic_car) // Points to your car vector asset
//                }
            }
        }.attach()
    }
}