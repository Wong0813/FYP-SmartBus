# SmartBus Native Android (Kotlin)

This project has been migrated to a **Pure Native Android** application using **Kotlin** and **Firebase**.

## How to use:
1. **Download as ZIP**: Go to the Settings (Gear Icon) -> Download as ZIP.
2. **Extract**: Unzip the folder.
3. **Android Studio**: Open Android Studio, select "Open an existing project", and choose this folder.
4. **Firebase**:
   - Create a project on [Firebase Console](https://console.firebase.google.com/).
   - Add an Android App with package name `com.upsi.smartbus`.
   - Download `google-services.json` and place it in the `app/` folder of this project.
5. **Run**: Connect your Android device or emulator and press "Run".

## Key Features (implemented in Kotlin):
- **Real-time Tracking**: Integrated with Firestore for live bus GPS.
- **AI Prediction (ETA)**: Heuristic-based arrival time engine in `EtaPredictor.kt`.
- **User Roles**: Separate logic for Students and Drivers.
- **Modern UI**: Material 3 design with a clean, professional aesthetic.
