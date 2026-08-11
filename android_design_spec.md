# SmartBus UI/UX Design System & Prompt Specification

Use this document as a design prompt or technical specification for developing the **exact same UI/UX interface** in native Android using Kotlin (Jetpack Compose) or Java (XML Layouts).

---

## 🎨 1. Global Design Tokens (Theme Specs)

To match the high-quality, modern card-style layout:

### Colors
*   **Primary / Theme Color**: Crimson Red (`#990000`)
*   **Primary Dark**: Deep Red (`#6D0000`)
*   **Secondary / Accent**: Amber Gold (`#FFB300`)
*   **Background Screen**: Light Cool Gray (`#F5F6FA`)
*   **Card Background**: Solid White (`#FFFFFF`)
*   **Text Primary**: Deep Charcoal (`#1A1A2E`)
*   **Text Secondary**: Cool Muted Gray (`#6B7280`)
*   **Status Moving / Active**: Emerald Green (`#10B981` / tint `#E6F4EA`)
*   **Status Resting / Parked**: Vivid Orange (`#F97316` / tint `#FFF3E0`)
*   **Status Offline / Inactive**: Neutral Slate (`#9CA3AF` / tint `#F3F4F6`)

### Layout & Borders
*   **Card Rounded Corners**: `16dp` / `16.0` corner radius.
*   **Input Fields Rounded Corners**: `12dp` / `12.0` corner radius.
*   **Elevations**: Card elevation set to `0dp` or `1dp` with a thin border line (`#E5E7EB` 1dp stroke) for a clean modern flat-card look.
*   **Margins & Paddings**: Standard outer screen padding `16dp`. Internal card content padding `16dp` to `20dp`.

---

## 🧭 2. App Flow & Architecture

*   **Role-Based Dynamic Shell**:
    *   No role selector radio buttons on the Login Screen. Role is automatically resolved on server authentication.
    *   Upon login, redirect user to the respective dashboard wrapper: `StudentNavWrapper`, `DriverDashboard`, or `AdminDashboard`.
*   **Navigation Controller**:
    *   Unified Left popup sliding drawer menu (`AppDrawer`).
    *   No persistent bottom navigation bar. Switching items in the drawer replaces the current fragment/view inside the core content frame of the parent activity.

---

## 📱 3. Detailed Screen Specifications

### 🔑 A. Login Screen
*   **Header Section**:
    *   Vertical gradient background from Crimson Red (`#990000`) to Deep Red (`#6D0000`).
    *   Bottom corners rounded with `40dp`.
    *   Centered **SmartBus Logo** (`ClipOval` image with `BoxFit.cover` or `CenterCrop` - **NO white borders**).
    *   App Title text: **SMARTBUS** (size `22sp`, bold, tracking `2.5sp`), Subtitle badge: "AI ARRIVAL PREDICTION" (caps, size `9sp`, white text on a rounded semi-transparent capsule).
*   **Form Card (White, Rounded `20dp`, margin `28dp`)**:
    *   Header title: "Welcome Back" (`22sp`, w800) and subtitle "Sign in to access SmartBus" (`13sp`, gray).
    *   **Username Field**: Outline input, prefix person icon (Crimson), filled background (`#F9FAFB`).
    *   **Password Field**: Outline input, prefix lock icon (Crimson), suffix toggle eye icon to show/hide text, filled background (`#F9FAFB`).
    *   **Error Banner**: Inline red border container with warning icon, shown only on login failure.
    *   **Sign In Button**: Large flat Crimson button with rounded `14dp` corners.
    *   **Information Link**: Bottom centered text button "How to get access?" showing demo account details in a rounded bottom sheet modal.

---

### 🗺️ B. Student - Home Map Screen
*   **Map Canvas Area**:
    *   Background fill: Soft grass green (`#E8F5E9`).
    *   Tasik Lake: Horizontal soft blue (`#B3E5FC`) oval positioned exactly in the center.
    *   Inactive road network: Light slate gray (`#CFD8DC`) lines (width `5dp`) connecting the campus stops.
    *   Active route overlay: Bold Crimson Red (`#990000`, width `5dp`) highlighted route with a white border (`9dp`) drawn only for the currently selected bus's path.
    *   Stop Markers: White circle dot (size `16dp`) with a blue border (width `3dp`), displaying small stop abbreviation labels (e.g. `KAB`, `PT`) offset next to them.
    *   Animated Bus Marker: Glowing pulsing circle matching bus status (Green = Working, Orange = Resting).
*   **Top Floating Widgets**:
    *   *Left Capsule*: Small white pill container showing global environment weather icon & state name (e.g. `☀️ SUNNY`).
    *   *Right Card*: Compact dropdown selector showing selected bus name with a small status dot indicator.
*   **Bottom Floating ETA Card (CRITICAL: Small and Compact)**:
    *   Fixed height of `60dp`, horizontal layout.
    *   Format: `[Bus Icon] | NEXT STOP: KAB | 40 km/h | ETA: 3m | [Crimson badge: AI Prediction 4m]`
    *   Tapping the card navigates directly to the Bus Detail Screen.

---

### 🚌 C. Student - Active Buses List
*   **Search Bar Container**:
    *   Top white box (`#FFFFFF`), housing a light gray (`#F5F6FA`) rounded text input with a search icon prefix.
*   **Weather/Traffic Alert Banner**:
    *   Thin amber strip (`#FFFBEB`) with info icon showing active environmental model coefficients (e.g. "Environment: Weather: SUNNY | Traffic: NORMAL").
*   **Bus Cards List**:
    *   Standard card template with a **5dp left accent strip** reflecting bus status (Green/Orange/Gray).
    *   Inside Card: Bus Name, Route description string, start stop location badge (`Start: KAB`), next stop indicator label (`📍 Next: KUO`).
    *   Trailing Widget: Distinctive pinkish-red tinted capsule (`#990000` at `0.06` alpha) housing the AI prediction: `AI ETA \n [Bold Crimson Time]`.

---

### 📊 D. Student - Bus Detail View
*   **Route Info Card**: Displays the bus name, route stops sequence (`KAB ➔ PT ➔ SS ➔ KA ➔ DKP ➔ KAB`), and start location badge.
*   **Live Status Card**:
    *   Status badge at top-right (Green/Orange/Gray).
    *   Next stop info + current stats row (Speed, Distance left).
    *   **Visual Track Progress Line**: Horizontal grey track line containing a moving bus icon aligned to the current progress point (`Progress = 1.0 - (distance / 2.0)`), ending at a target blue stop dot.
*   **ETA Comparison Card**:
    *   Grid containing two boxes side-by-side: "Standard ETA" (gray bg) vs "🤖 AI Predicted ETA" (light Crimson bg, Crimson text).
    *   Insight banner below: light amber or green warning box explaining factors affecting variance (weather, traffic peak hours, speed).
*   **Data Feeds Card**:
    *   List of sensor feeds: Weather Sync, Traffic Density, Speedometer, GPS coordinates. Each item has a green glowing status dot and listed data source.

---

### 🗓️ E. Student - Route Schedules Screen
*   **Tab Layout**:
    *   Tab 1: "Weekdays (Mon-Fri)" - displays routes Laluan 1 to 8.
    *   Tab 2: "Saturdays" - displays routes Laluan 9 to 18.
*   **Route Card Item**:
    *   Left: Crimson Red tinted square box showing Route Short Name (e.g. `L1`, `L2`).
    *   Right: Route Full Name + horizontal chain flow of stop chips (`KAB` ➔ `PT` ➔ `SS` ➔ `KA`).
*   **Abbreviation Legend Grid**:
    *   Two-column grid list mapping abbreviations to full stop names (e.g., `KAB` ➔ `Kolej Aminuddin Baki`).

---

### 👤 F. Unified Profile Screen (Student / Driver / Admin)
*   **Identity Header Card**:
    *   Large circular avatar containing role icon (School / Driver / Shield).
    *   Display Full Name (bold `18sp`) and username below.
    *   Access status badge (e.g., Green `Active Portal Access` pill).
*   **Info Rows Card**:
    *   Contains list of details with prefix icons (ID, Faculty, Program, Email, or assigned bus).
*   **Campus/System Info Card**:
    *   Campus region, tracked fleet count, and green `System Health` indicator.
*   **Sign Out Button**:
    *   Tinted red flat button (`#FEF2F2` bg, `#DC2626` text and border) at the bottom.

---

### 🕹️ G. Driver - Control Desk & Telemetry Log
*   **Welcome Header**: Displays driver's full name, assigned route badge, and vehicle details.
*   **Duty Status Card**:
    *   Row of three large Choice Chips: Working, Resting, Off Duty.
    *   Visual representation: active selection gets solid primary color (Green/Orange/Red), while inactive show gray outlines.
*   **GPS Speed Card**:
    *   Square bordered display (`120x120` size, white card, Crimson border) showing only the current speed digits (e.g., `45` km/h). **Read-only - no user buttons.**
*   **Uplink Terminal Log**:
    *   Dark terminal card (`#1A1A2E`) with glowing green monospace log text automatically printing coordinate transmissions.

---

### 🛡️ H. Admin Console (Accounts, Fleet & Routes)
*   **Manage Accounts Page**:
    *   Form to create student/driver/admin credentials with name, username, staff no., password, and driver route allocation.
    *   List of users with role-colored icons and Delete (garbage can) / Edit (pencil) action buttons.
*   **Bus Fleet Page**:
    *   Form to register new buses (ID, Name, License Plate, Route selection).
    *   List of buses showing tracking state, license plate, and delete buttons.
*   **Route Config Page**:
    *   Horizontal flow sequence path builder. Admin clicks stop chips to sequence them (visualised as deletable chips separated by arrow icons).

---

## 🛠️ 4. Android Implementation Guidelines

### Kotlin (Jetpack Compose) Styling Example
```kotlin
// Theme Setup
val CrimsonPrimary = Color(0xFF990000)
val BackgroundGray = Color(0xFFF5F6FA)
val TextDark = Color(0xFF1A1A2E)

@Composable
fun SmartBusCard(
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    ) {
        content()
    }
}
```

### Java (XML Styling Example)
```xml
<!-- card_style.xml -->
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#FFFFFF" />
    <corners android:radius="16dp" />
    <stroke android:width="1dp" android:color="#E5E7EB" />
</shape>
```
