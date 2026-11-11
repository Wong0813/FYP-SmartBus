package com.upsi.smartbus.feature.student.schedule

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.upsi.smartbus.databinding.FragmentBusSchedulesBinding
import com.upsi.smartbus.feature.student.StudentActivity

class BusSchedulesFragment : Fragment() {

    private var _binding: FragmentBusSchedulesBinding? = null
    private val binding get() = _binding!!

    private val tabTitles = listOf("Weekdays (Mon-Fri)", "Weekend")
    private val tabFragments = mutableMapOf<Int, BusScheduleTabFragment>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBusSchedulesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnHeroDrawer.setOnClickListener {
            (activity as? StudentActivity)?.openDrawer()
        }

        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.text?.clear()
        }

        setupViewPager()
        setupSearch()
    }

    private fun setupViewPager() {
        val pagerAdapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 2

            override fun createFragment(position: Int): Fragment {
                val scheduleType = if (position == 0) "WEEKDAY" else "SATURDAY"
                val f = BusScheduleTabFragment.newInstance(scheduleType)
                tabFragments[position] = f
                return f
            }
        }
        binding.viewPager.adapter = pagerAdapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = tabTitles[position]
        }.attach()
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString().orEmpty()
                binding.btnClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                // Propagate query to current tabs
                tabFragments.values.forEach { it.applyFilter(query) }
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        tabFragments.clear()
        _binding = null
    }
}
