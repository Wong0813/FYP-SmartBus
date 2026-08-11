package com.upsi.smartbus.core.model

/**
 * Driver Account data model
 */
data class DriverAccount(
    val routeName: String,
    val driverName: String,
    val email: String,
    val busId: String,
    val plateNumber: String
)

/**
 * Official UPSI JHEPA BHEP Bus Routes & Stops Data (April 2026 Edition)
 *
 * GPS coordinates are calibrated to real UPSI Sultan Abdul Jalil Shah Campus
 * buildings using Google Maps references.
 */
object RouteData {

    // ── Official UPSI Bus Stops with real campus GPS coordinates ──
    val stops = listOf(
        BusStop("KAB",  "Kolej Aminuddin Baki",       3.722061, 101.516966),
        BusStop("KHAR", "Kolej Harun Aminurrashid",    3.722947, 101.521115),
        BusStop("KUO",  "Kolej Ungku Omar",            3.719400, 101.520592),
        BusStop("SS",   "Scholar Suites",              3.685642, 101.525542),
        BusStop("KA",   "Kompleks Akademik",           3.722889, 101.525556),
        BusStop("DKP",  "Dewan Kuliah Pusat",          3.721695, 101.528037),
        BusStop("PT",   "Pintu Timur",                 3.685450, 101.524808),
        BusStop("TB",   "Taman Bahtera",               3.673342, 101.531238),
        BusStop("PC",   "Proton City",                 3.736031, 101.511208)
    )

    // ── Isnin Hingga Jumaat (Monday to Friday) ──
    val weekdayRoutes = listOf(
        Route("L1", "Laluan 1", "KAB - PT - KSAS", listOf("KAB", "PT", "SS", "KA", "DKP", "KAB"), "WEEKDAY"),
        Route("L2", "Laluan 2", "KUO - PT", listOf("KUO", "PT", "KUO"), "WEEKDAY"),
        Route("L3", "Laluan 3", "PT - KUO", listOf("PT", "KUO", "PT"), "WEEKDAY"),
        Route("L4", "Laluan 4", "KHAR - PT", listOf("KHAR", "PT", "KA", "KHAR"), "WEEKDAY"),
        Route("L5", "Laluan 5", "PT - KHAR", listOf("PT", "KA", "KHAR", "PT"), "WEEKDAY"),
        Route("L6", "Laluan 6", "PC - KSAS - PT", listOf("PC", "SS", "KA", "DKP", "PT", "PC"), "WEEKDAY"),
        Route("L7", "Laluan 7", "TB - PT - KSAS", listOf("TB", "PT", "SS", "KA", "DKP", "PT", "TB"), "WEEKDAY"),
        Route("L8", "Laluan 8", "KSAS - PT - TB", listOf("DKP", "PT", "TB", "PT", "SS", "KA", "DKP"), "WEEKDAY"),
        Route("SC1", "Shuttle Campus KAB", "Kitaran Kampus", listOf("KAB", "KHAR", "SS", "DKP", "KA", "KAB"), "WEEKDAY"),
        Route("SC2", "Shuttle Campus KUO", "Kitaran Kampus", listOf("KUO", "KHAR", "SS", "DKP", "KA", "KUO"), "WEEKDAY")
    )

    // ── Sabtu (Saturday) ──
    val saturdayRoutes = listOf(
        Route("L9",  "Laluan 9",  "KAB - PT", listOf("KAB", "PT", "KAB"), "SATURDAY"),
        Route("L10", "Laluan 10", "PT - KAB", listOf("PT", "KAB", "PT"), "SATURDAY"),
        Route("L11", "Laluan 11", "KUO - PT", listOf("KUO", "PT", "KUO"), "SATURDAY"),
        Route("L12", "Laluan 12", "PT - KUO", listOf("PT", "KUO", "PT"), "SATURDAY"),
        Route("L13", "Laluan 13", "KHAR - PT", listOf("KHAR", "PT", "KHAR"), "SATURDAY"),
        Route("L14", "Laluan 14", "PT - KHAR", listOf("PT", "KHAR", "PT"), "SATURDAY"),
        Route("L15", "Laluan 15", "Kitaran Kampus", listOf("KUO", "KAB", "KHAR", "SS", "KA", "DKP", "KUO"), "SATURDAY"),
        Route("L16", "Laluan 16", "PC - KSAS - PT", listOf("PC", "SS", "KA", "DKP", "PT", "PC"), "SATURDAY"),
        Route("L17", "Laluan 17", "TB - PT - KSAS", listOf("TB", "PT", "SS", "KA", "DKP", "PT", "TB"), "SATURDAY"),
        Route("L18", "Laluan 18", "KSAS - PT - TB", listOf("DKP", "PT", "TB", "PT", "SS", "KA", "DKP"), "SATURDAY")
    )

    // ── Official Drivers for Each Route ──
    val defaultDrivers = listOf(
        DriverAccount("Laluan 1", "Encik Razif Bin Hassan", "driver1@upsi.edu.my", "BUS-001", "WAA 1234 A"),
        DriverAccount("Laluan 2", "Encik Harun Bin Ismail", "driver2@upsi.edu.my", "BUS-002", "WAA 2345 B"),
        DriverAccount("Laluan 3", "Encik Azman Bin Rosli", "driver3@upsi.edu.my", "BUS-003", "WAA 3456 C"),
        DriverAccount("Laluan 4", "Encik Faizal Bin Ahmad", "driver4@upsi.edu.my", "BUS-004", "WAA 4567 D"),
        DriverAccount("Laluan 5", "Encik Zulkifli Bin Mansor", "driver5@upsi.edu.my", "BUS-005", "WAA 5678 E"),
        DriverAccount("Laluan 6", "Encik Amirul Bin Zakaria", "driver6@upsi.edu.my", "BUS-006", "WAA 6789 F"),
        DriverAccount("Laluan 7", "Encik Shafiq Bin Ibrahim", "driver7@upsi.edu.my", "BUS-007", "WAA 7890 G"),
        DriverAccount("Laluan 8", "Encik Osman Bin Yusof", "driver8@upsi.edu.my", "BUS-008", "WAA 8901 H"),
        DriverAccount("Shuttle Campus KAB", "Encik Khairul Bin Nordin", "driversc1@upsi.edu.my", "BUS-SC1", "WAA 1122 S"),
        DriverAccount("Shuttle Campus KUO", "Encik Daniel Bin Kamaruddin", "driversc2@upsi.edu.my", "BUS-SC2", "WAA 3344 S"),
        DriverAccount("Laluan 9", "Encik Ridzuan Bin Ali", "driver9@upsi.edu.my", "BUS-009", "WAA 9012 I"),
        DriverAccount("Laluan 10", "Encik Hafiz Bin Latif", "driver10@upsi.edu.my", "BUS-010", "WAA 1023 J"),
        DriverAccount("Laluan 11", "Encik Zainal Bin Abidin", "driver11@upsi.edu.my", "BUS-011", "WAA 1134 K"),
        DriverAccount("Laluan 12", "Encik Badrul Bin Hisham", "driver12@upsi.edu.my", "BUS-012", "WAA 1245 L"),
        DriverAccount("Laluan 13", "Encik Mustaffa Bin Omar", "driver13@upsi.edu.my", "BUS-013", "WAA 1356 M"),
        DriverAccount("Laluan 14", "Encik Nazri Bin Hamzah", "driver14@upsi.edu.my", "BUS-014", "WAA 1467 N"),
        DriverAccount("Laluan 15", "Encik Fikri Bin Daud", "driver15@upsi.edu.my", "BUS-015", "WAA 1578 O"),
        DriverAccount("Laluan 16", "Encik Saifuddin Bin Abdullah", "driver16@upsi.edu.my", "BUS-016", "WAA 1689 P"),
        DriverAccount("Laluan 17", "Encik Imran Bin Khalid", "driver17@upsi.edu.my", "BUS-017", "WAA 1790 Q"),
        DriverAccount("Laluan 18", "Encik Taufik Bin Mahfuz", "driver18@upsi.edu.my", "BUS-018", "WAA 1801 R")
    )

    fun getDriverForRoute(routeName: String): DriverAccount {
        return defaultDrivers.find { it.routeName.equals(routeName, ignoreCase = true) }
            ?: DriverAccount(routeName, "Driver $routeName", "driver@upsi.edu.my", "BUS-001", "WAA 1234 A")
    }

    fun getStopFullName(abbreviation: String): String {
        return stops.find { it.abbreviation == abbreviation }?.fullName ?: abbreviation
    }

    fun getStopByAbbreviation(abbreviation: String): BusStop? {
        return stops.find { it.abbreviation == abbreviation }
    }

    fun getRoutesForToday(): List<Route> {
        val calendar = java.util.Calendar.getInstance()
        return when (calendar.get(java.util.Calendar.DAY_OF_WEEK)) {
            java.util.Calendar.SUNDAY -> emptyList()
            java.util.Calendar.SATURDAY -> saturdayRoutes
            else -> weekdayRoutes
        }
    }

    fun getAllRoutes(): List<Route> {
        return weekdayRoutes + saturdayRoutes
    }
}
