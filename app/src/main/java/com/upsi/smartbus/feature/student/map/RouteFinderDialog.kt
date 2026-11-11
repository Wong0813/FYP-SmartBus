package com.upsi.smartbus.feature.student.map

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.upsi.smartbus.R
import com.upsi.smartbus.core.network.AiApiClient
import com.upsi.smartbus.core.network.RouteRecommendRequest
import com.upsi.smartbus.core.network.RouteRecommendResponse
import com.upsi.smartbus.core.network.RouteSolution
import com.upsi.smartbus.databinding.DialogRouteFinderBinding
import com.upsi.smartbus.databinding.ItemRouteRecommendationBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class RouteFinderDialog : BottomSheetDialogFragment() {

    private var _binding: DialogRouteFinderBinding? = null
    private val binding get() = _binding!!

    // UPSI Campus Stops
    private val campusStops = listOf(
        "KAB", "PT", "SS", "KA", "DKP", "PKR", "FM",
        "KK", "KHAR", "KUO", "FPE", "FSMT", "FSKPM", "FSKTM", "PP", "PSS"
    )

    private val solutions = mutableListOf<RouteSolution>()
    private lateinit var adapter: RouteSolutionAdapter

    var onRouteSelectedListener: ((String) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogRouteFinderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupTodayScheduleHint()
        setupSpinners()
        setupRecyclerView()

        binding.btnCloseFinder.setOnClickListener {
            dismiss()
        }

        binding.btnFindRoute.setOnClickListener {
            performRouteSearch()
        }
    }

    override fun onStart() {
        super.onStart()
        val d = dialog as? com.google.android.material.bottomsheet.BottomSheetDialog ?: return
        val sheet = d.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(sheet)
        behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        behavior.skipCollapsed = true
    }

    private fun isCurrentWeekend(): Boolean {
        val calendar = java.util.Calendar.getInstance()
        val dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK)
        return dayOfWeek == java.util.Calendar.SATURDAY || dayOfWeek == java.util.Calendar.SUNDAY
    }

    private fun setupTodayScheduleHint() {
        val calendar = java.util.Calendar.getInstance()
        val dayName = java.text.SimpleDateFormat("EEEE", java.util.Locale.ENGLISH).format(calendar.time)
        val isWeekend = isCurrentWeekend()
        val freqDesc = if (isWeekend) "Weekend Service (Every 60m)" else "Weekday Service (Every 30m)"
        binding.tvTodayScheduleHint.text = "📅 Today: $dayName • $freqDesc"
    }

    private fun setupSpinners() {
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, campusStops)
        binding.spinnerOrigin.adapter = adapter
        binding.spinnerDestination.adapter = adapter

        // Set sensible defaults
        binding.spinnerOrigin.setSelection(campusStops.indexOf("KAB").coerceAtLeast(0))
        binding.spinnerDestination.setSelection(campusStops.indexOf("KUO").coerceAtLeast(1))
    }

    private fun setupRecyclerView() {
        adapter = RouteSolutionAdapter(solutions) { solution ->
            val routeName = solution.route ?: solution.route_first
            if (!routeName.isNullOrEmpty()) {
                onRouteSelectedListener?.invoke(routeName)
                dismiss()
            }
        }
        binding.rvRouteResults.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRouteResults.adapter = adapter
    }

    private fun performRouteSearch() {
        val origin = binding.spinnerOrigin.selectedItem as? String ?: return
        val dest = binding.spinnerDestination.selectedItem as? String ?: return

        if (origin == dest) {
            Toast.makeText(requireContext(), "Origin and destination are the same! (Walking distance)", Toast.LENGTH_SHORT).show()
            return
        }

        binding.pbRouteLoading.visibility = View.VISIBLE
        binding.btnFindRoute.isEnabled = false

        // Automatically determine day type from today's real day
        val dayType = if (isCurrentWeekend()) "weekend" else "weekday"
        val request = RouteRecommendRequest(origin = origin, destination = dest, day_type = dayType)
        AiApiClient.getService().recommendRoute(request).enqueue(object : Callback<RouteRecommendResponse> {
            override fun onResponse(call: Call<RouteRecommendResponse>, response: Response<RouteRecommendResponse>) {
                if (_binding == null) return
                binding.pbRouteLoading.visibility = View.GONE
                binding.btnFindRoute.isEnabled = true

                val body = response.body()
                if (response.isSuccessful && body?.status == "success" && body.data != null) {
                    solutions.clear()
                    solutions.addAll(body.data.solutions)
                    adapter.notifyDataSetChanged()

                    val dayLabel = if (dayType == "weekend") "Weekend (60m headway)" else "Weekday (30m headway)"
                    binding.tvResultsHeader.visibility = View.VISIBLE
                    binding.tvResultsHeader.text = "Available Options (${solutions.size} found • $dayLabel)"
                } else {
                    Toast.makeText(requireContext(), "No routes found between $origin and $dest", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<RouteRecommendResponse>, t: Throwable) {
                if (_binding == null) return
                binding.pbRouteLoading.visibility = View.GONE
                binding.btnFindRoute.isEnabled = true
                Toast.makeText(requireContext(), "AI Server offline. Please make sure AI server is running.", Toast.LENGTH_SHORT).show()
            }
        })
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    inner class RouteSolutionAdapter(
        private val items: List<RouteSolution>,
        private val onItemClick: (RouteSolution) -> Unit
    ) : RecyclerView.Adapter<RouteSolutionAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemRouteRecommendationBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemRouteRecommendationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(b)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            val isDirect = item.type.equals("DIRECT", true)

            holder.binding.tvSolutionType.text = if (isDirect) "DIRECT" else "TRANSFER"
            holder.binding.tvSolutionType.setBackgroundResource(
                if (isDirect) R.drawable.bg_status_moving else R.drawable.bg_status_resting
            )
            holder.binding.tvSolutionType.setTextColor(
                ContextCompat.getColor(
                    holder.itemView.context,
                    if (isDirect) R.color.status_moving else R.color.status_resting
                )
            )

            val stopsText = item.stops_count?.let { " ($it stops)" } ?: item.total_stops?.let { " ($it stops total)" } ?: ""
            holder.binding.tvSolutionDuration.text = "~${item.est_duration_minutes} min$stopsText"
            holder.binding.tvSolutionDesc.text = item.description

            // Time breakdown & comparison
            val rideMin = item.ride_duration_minutes ?: item.est_duration_minutes
            holder.binding.tvRideTime.text = "🚌 Ride: ${rideMin}m"

            val waitMin = item.wait_time_minutes ?: 0
            if (isDirect || waitMin <= 0) {
                holder.binding.tvWaitTime.visibility = View.GONE
            } else {
                holder.binding.tvWaitTime.visibility = View.VISIBLE
                holder.binding.tvWaitTime.text = "⏳ Wait: ${waitMin}m"
            }

            val walkMin = item.walking_time_minutes ?: 15
            holder.binding.tvWalkingTime.text = "🚶 Walk: ${walkMin}m"

            if (!item.advice.isNullOrEmpty()) {
                holder.binding.tvAdvice.visibility = View.VISIBLE
                holder.binding.tvAdvice.text = "💡 ${item.advice}"
            } else {
                holder.binding.tvAdvice.visibility = View.GONE
            }

            holder.itemView.setOnClickListener {
                onItemClick(item)
            }
        }

        override fun getItemCount(): Int = items.size
    }

    companion object {
        fun newInstance(): RouteFinderDialog = RouteFinderDialog()
    }
}
