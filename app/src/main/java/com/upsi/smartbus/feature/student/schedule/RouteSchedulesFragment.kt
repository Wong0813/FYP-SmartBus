package com.upsi.smartbus.feature.student.schedule

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.upsi.smartbus.databinding.FragmentRouteSchedulesBinding

class RouteSchedulesFragment : Fragment() {

    private var _binding: FragmentRouteSchedulesBinding? = null
    private val binding get() = _binding!!

    private val tabTitles = listOf("Weekdays (Mon-Fri)", "Weekend")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRouteSchedulesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViewPager()
    }

    private fun setupViewPager() {
        val pagerAdapter = SchedulePagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = tabTitles[position]
        }.attach()
    }

    private inner class SchedulePagerAdapter(
        fragment: Fragment
    ) : FragmentStateAdapter(fragment) {

        override fun getItemCount(): Int = 2

        override fun createFragment(position: Int): Fragment {
            val scheduleType = if (position == 0) "WEEKDAY" else "SATURDAY"
            return RouteListFragment.newInstance(scheduleType)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
