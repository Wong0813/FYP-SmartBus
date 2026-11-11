package com.upsi.smartbus.feature.student.schedule

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.upsi.smartbus.R
import com.upsi.smartbus.databinding.DialogDriverDetailBinding
import com.upsi.smartbus.feature.student.StudentActivity
import com.upsi.smartbus.ui.adapter.UnifiedBusScheduleItem

class DriverDetailBottomSheet : BottomSheetDialogFragment() {

    private var _binding: DialogDriverDetailBinding? = null
    private val binding get() = _binding!!

    private var item: UnifiedBusScheduleItem? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogDriverDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val data = item ?: return

        binding.btnDetailClose.setOnClickListener { dismiss() }

        // Route & Status
        binding.tvDetailRouteName.text = data.route.name
        val stopsStr = data.route.stops.joinToString(" ➔ ")
        binding.tvDetailRouteSubtitle.text = data.route.shortName.ifEmpty { stopsStr }
        binding.tvDetailStopsChain.text = stopsStr

        val isWorking = data.isActive && (data.liveBus?.status.equals("WORKING", true))
        if (data.isActive) {
            binding.llDetailStatusPill.setBackgroundResource(
                if (isWorking) R.drawable.bg_status_moving else R.drawable.bg_status_resting
            )
            binding.detailStatusDot.setBackgroundResource(
                if (isWorking) R.drawable.dot_green else R.drawable.dot_orange
            )
            binding.tvDetailStatusLabel.text = if (isWorking) "ACTIVE" else "RESTING"
            binding.tvDetailStatusLabel.setTextColor(
                ContextCompat.getColor(requireContext(), if (isWorking) R.color.status_moving else R.color.status_resting)
            )
        } else {
            binding.llDetailStatusPill.setBackgroundResource(R.drawable.bg_status_offline)
            binding.detailStatusDot.setBackgroundResource(R.drawable.dot_gray)
            binding.tvDetailStatusLabel.text = "OFFLINE"
            binding.tvDetailStatusLabel.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.status_offline)
            )
        }

        // Driver details
        binding.tvDriverName.text = data.driver.driverName
        binding.tvDriverPhone.text = data.driver.phone.ifEmpty { "012-345 6700" }

        val plate = data.liveBus?.plateNumber?.ifEmpty { data.driver.plateNumber } ?: data.driver.plateNumber
        val busId = data.driver.busId
        binding.tvBusPlateInfo.text = "$busId • $plate"

        // Call Driver button
        binding.btnCallDriver.setOnClickListener {
            val phone = data.driver.phone.ifEmpty { "0123456700" }
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone.replace(" ", "")}"))
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Cannot dial phone: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        // Track on map button
        binding.btnViewOnMap.setOnClickListener {
            dismiss()
            (activity as? StudentActivity)?.navigateToMap(data.route.name)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(item: UnifiedBusScheduleItem): DriverDetailBottomSheet {
            return DriverDetailBottomSheet().apply {
                this.item = item
            }
        }
    }
}
