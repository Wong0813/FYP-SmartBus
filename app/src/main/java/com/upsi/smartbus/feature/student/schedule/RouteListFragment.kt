package com.upsi.smartbus.feature.student.schedule

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.upsi.smartbus.core.data.RouteRepository
import com.upsi.smartbus.core.model.Route
import com.upsi.smartbus.core.model.RouteData
import com.upsi.smartbus.databinding.FragmentRouteListBinding
import com.upsi.smartbus.ui.adapter.LegendAdapter
import com.upsi.smartbus.ui.adapter.RouteCardAdapter

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
        RouteRepository.loadRoutes { routes ->
            if (_binding == null) return@loadRoutes
            val filtered = if (scheduleType == "WEEKDAY") {
                routes.filter { it.scheduleType.equals("WEEKDAY", ignoreCase = true) }
            } else {
                routes.filter { it.scheduleType.equals("SATURDAY", ignoreCase = true) }
            }
            binding.rvRoutes.layoutManager = LinearLayoutManager(requireContext())
            binding.rvRoutes.adapter = RouteCardAdapter(filtered) { route ->
                (activity as? com.upsi.smartbus.feature.student.StudentActivity)?.navigateToMap(route.name)
            }
        }
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
