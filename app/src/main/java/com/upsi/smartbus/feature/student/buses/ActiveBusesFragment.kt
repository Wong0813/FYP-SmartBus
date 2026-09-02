package com.upsi.smartbus.feature.student.buses

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.ListenerRegistration
import com.upsi.smartbus.core.data.FirestoreHelper
import com.upsi.smartbus.core.model.Bus
import com.upsi.smartbus.databinding.FragmentActiveBusesBinding
import com.upsi.smartbus.ui.adapter.BusCardAdapter

class ActiveBusesFragment : Fragment() {

    private var _binding: FragmentActiveBusesBinding? = null
    private val binding get() = _binding!!

    private val db by lazy { FirestoreHelper.db }

    private lateinit var adapter: BusCardAdapter
    private var allBuses: List<Bus> = emptyList()

    /** Holds the Firestore snapshot listener so we can remove it on destroy */
    private var busesListener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentActiveBusesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSearch()
        listenToFirestore()
    }

    private fun setupRecyclerView() {
        adapter = BusCardAdapter(allBuses) { bus ->
            val intent = Intent(requireContext(), BusDetailActivity::class.java)
            intent.putExtra("bus_id", bus.id)
            intent.putExtra("bus_name", bus.name)
            startActivity(intent)
        }
        binding.rvBuses.layoutManager = LinearLayoutManager(requireContext())
        binding.rvBuses.adapter = adapter
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterBuses(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    /** Holds the Realtime Database listener so we can remove it on destroy */
    private var rtdbListener: com.google.firebase.database.ValueEventListener? = null
    private val staticBusesMap = mutableMapOf<String, Bus>()

    /**
     * Attach a real-time Realtime Database listener on the `liveBuses` node.
     * Consumes 0 Firestore read/write quota.
     */
    private fun listenToFirestore() {
        // ✅ Use cache first — avoid Firestore reads every time the fragment opens
        if (com.upsi.smartbus.core.data.AppCache.hasStaticBuses()) {
            staticBusesMap.clear()
            com.upsi.smartbus.core.data.AppCache.getStaticBusesList().forEach { bus ->
                staticBusesMap[bus.id] = bus
            }
            bindRealtimeDatabaseListener()
            return
        }
        // Cache miss — read from Firestore once, then populate cache
        db.collection("buses").get().addOnSuccessListener { snapshot ->
            if (_binding == null) return@addOnSuccessListener
            staticBusesMap.clear()
            val busList = mutableListOf<Bus>()
            for (doc in snapshot.documents) {
                val bus = doc.toObject(Bus::class.java) ?: continue
                val b = bus.copy(id = doc.id)
                staticBusesMap[doc.id] = b
                busList.add(b)
            }
            com.upsi.smartbus.core.data.AppCache.putStaticBuses(busList)
            bindRealtimeDatabaseListener()
        }.addOnFailureListener {
            bindRealtimeDatabaseListener()
        }
    }

    private fun bindRealtimeDatabaseListener() {
        rtdbListener?.let { com.upsi.smartbus.core.data.RealtimeDbHelper.liveBuses.removeEventListener(it) }
        rtdbListener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                if (_binding == null) return

                val fetchedBuses = mutableListOf<Bus>()
                for (child in snapshot.children) {
                    val busId = child.key ?: continue
                    val lat = child.child("latitude").getValue(Double::class.java) ?: 0.0
                    val lng = child.child("longitude").getValue(Double::class.java) ?: 0.0
                    val speed = child.child("speed").getValue(Double::class.java) ?: 0.0
                    val status = child.child("status").getValue(String::class.java) ?: "IDLE"
                    val nextStop = child.child("nextStop").getValue(String::class.java) ?: ""
                    val dist = child.child("distanceToNext").getValue(Double::class.java) ?: 0.0
                    val routeName = child.child("routeName").getValue(String::class.java) ?: ""
                    val lastUpdated = child.child("lastUpdated").getValue(Long::class.java) ?: System.currentTimeMillis()

                    val staticBus = staticBusesMap[busId]
                    val finalRouteName = routeName.ifEmpty { staticBus?.routeName.orEmpty() }
                    val finalRouteStops = staticBus?.routeStops ?: emptyList()

                    val mergedBus = Bus(
                        id = busId,
                        name = staticBus?.name?.ifEmpty { finalRouteName } ?: finalRouteName,
                        routeName = finalRouteName,
                        plateNumber = staticBus?.plateNumber ?: busId,
                        licensePlate = staticBus?.licensePlate ?: staticBus?.plateNumber ?: busId,
                        routeStops = finalRouteStops,
                        startStop = finalRouteStops.firstOrNull().orEmpty(),
                        location = GeoPoint(lat, lng),
                        speed = speed,
                        nextStop = nextStop,
                        distanceToNext = dist,
                        passengerCount = 12,
                        capacity = 40,
                        status = status,
                        photoUrl = staticBus?.photoUrl.orEmpty(),
                        lastUpdated = lastUpdated
                    )
                    fetchedBuses.add(mergedBus)
                }

                allBuses = if (fetchedBuses.isEmpty()) {
                    if (staticBusesMap.isNotEmpty()) staticBusesMap.values.toList() else createFallbackBuses()
                } else {
                    fetchedBuses
                }

                val query = binding.etSearch.text?.toString().orEmpty()
                filterBuses(query)
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                if (_binding == null) return
                allBuses = if (staticBusesMap.isNotEmpty()) staticBusesMap.values.toList() else createFallbackBuses()
                val query = binding.etSearch.text?.toString().orEmpty()
                filterBuses(query)
            }
        }
        com.upsi.smartbus.core.data.RealtimeDbHelper.liveBuses.addValueEventListener(rtdbListener!!)
    }

    private fun filterBuses(query: String) {
        if (query.isBlank()) {
            adapter.updateList(allBuses)
        } else {
            val filtered = allBuses.filter { bus ->
                bus.name.contains(query, ignoreCase = true) ||
                bus.routeName.contains(query, ignoreCase = true) ||
                bus.nextStop.contains(query, ignoreCase = true) ||
                bus.plateNumber.contains(query, ignoreCase = true)
            }
            adapter.updateList(filtered)
        }
    }

    /** Fallback demo buses shown when Firestore is empty or unreachable */
    private fun createFallbackBuses(): List<Bus> {
        return listOf(
            Bus(
                id = "laluan_1", name = "Laluan 1", plateNumber = "WAA 1234 A",
                licensePlate = "WAA 1234 A", routeName = "Laluan 1",
                routeStops = listOf("KAB", "PT", "SS", "KA", "DKP", "KAB"),
                startStop = "KAB",
                location = GeoPoint(3.6845, 101.5234),
                speed = 40.0, nextStop = "PT",
                distanceToNext = 1.2, passengerCount = 18, capacity = 40,
                status = "Working"
            ),
            Bus(
                id = "laluan_2", name = "Laluan 2", plateNumber = "WBB 5678 B",
                licensePlate = "WBB 5678 B", routeName = "Laluan 2",
                routeStops = listOf("KUO", "PT", "KUO"),
                startStop = "KUO",
                location = GeoPoint(3.6862, 101.5251),
                speed = 35.0, nextStop = "PT",
                distanceToNext = 0.8, passengerCount = 22, capacity = 40,
                status = "Working"
            ),
            Bus(
                id = "laluan_4", name = "Laluan 4", plateNumber = "WCC 9012 C",
                licensePlate = "WCC 9012 C", routeName = "Laluan 4",
                routeStops = listOf("KHAR", "PT", "KA", "KHAR"),
                startStop = "KHAR",
                location = GeoPoint(3.6878, 101.5268),
                speed = 0.0, nextStop = "KA",
                distanceToNext = 2.1, passengerCount = 0, capacity = 40,
                status = "Resting"
            ),
            Bus(
                id = "shuttle_kab", name = "Shuttle Campus KAB", plateNumber = "WDD 3456 D",
                licensePlate = "WDD 3456 D", routeName = "Shuttle Campus KAB",
                routeStops = listOf("KAB", "KHAR", "SS", "DKP", "KA", "KAB"),
                startStop = "KAB",
                location = GeoPoint(3.6890, 101.5245),
                speed = 42.0, nextStop = "SS",
                distanceToNext = 1.5, passengerCount = 12, capacity = 40,
                status = "Working"
            ),
            Bus(
                id = "laluan_6", name = "Laluan 6", plateNumber = "WEE 7890 E",
                licensePlate = "WEE 7890 E", routeName = "Laluan 6",
                routeStops = listOf("PC", "SS", "KA", "DKP", "PT", "PC"),
                startStop = "PC",
                location = GeoPoint(3.6830, 101.5200),
                speed = 0.0, nextStop = "SS",
                distanceToNext = 0.0, passengerCount = 0, capacity = 40,
                status = "IDLE"
            )
        )
    }

    override fun onDestroyView() {
        rtdbListener?.let { com.upsi.smartbus.core.data.RealtimeDbHelper.liveBuses.removeEventListener(it) }
        busesListener?.remove()
        _binding = null
        super.onDestroyView()
    }
}
