package com.upsi.smartbus

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.upsi.smartbus.adapter.LegendAdapter
import com.upsi.smartbus.adapter.RouteCardAdapter
import com.upsi.smartbus.databinding.FragmentRouteListBinding
import com.upsi.smartbus.model.Route
import com.upsi.smartbus.model.RouteData

class RouteListFragment : Fragment() {

    private var _binding: FragmentRouteListBinding? = null
    private val binding get() = _binding!!

    private var scheduleType: String = "WEEKDAY"

    companion object {
        private const val ARG_SCHEDULE_TYPE = "schedule_type"

        fun newInstance(scheduleType: String): RouteListFragment {
            return RouteListFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_SCHEDULE_TYPE, scheduleType)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scheduleType = arguments?.getString(ARG_SCHEDULE_TYPE) ?: "WEEKDAY"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRouteListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRouteList()
        setupLegend()
    }

    private fun setupRouteList() {
        val routes: List<Route> = if (scheduleType == "WEEKDAY") {
            RouteData.weekdayRoutes
        } else {
            RouteData.saturdayRoutes
        }

        val adapter = RouteCardAdapter(routes)
        binding.rvRoutes.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRoutes.adapter = adapter
    }

    private fun setupLegend() {
        val legendAdapter = LegendAdapter(RouteData.stops)
        binding.rvLegend.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLegend.adapter = legendAdapter
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
