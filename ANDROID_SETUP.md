# Android Studio Setup Guide (SmartBus)

To package this application for Android using Kotlin and Android Studio, follow these steps:

## 1. Permissions
Open `app/src/main/AndroidManifest.xml` and add these lines before the `<application>` tag:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```

Inside the `<application>` tag, add:
```xml
android:usesCleartextTraffic="true"
```

## 2. Layout (activity_main.xml)
Open `app/src/main/res/layout/activity_main.xml` and replace the content with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <WebView
        android:id="@+id/webView"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintLeft_toLeftOf="parent"
        app:layout_constraintRight_toRightOf="parent"
        app:layout_constraintTop_toTopOf="parent" />

</androidx.constraintlayout.widget.ConstraintLayout>
```

## 3. MainActivity (MainActivity.kt)
Open `app/src/main/java/com/yourpackage/MainActivity.kt` and use this code:

```kotlin
package com.yourpackage // Change this to your project's package name

import android.os.Bundle
import android.webkit.GeolocationPermissions
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val myWebView: WebView = findViewById(R.id.webView)
        val webSettings = myWebView.settings
        
        // Essential WebSettings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.databaseEnabled = true
        webSettings.allowFileAccess = true
        webSettings.setGeolocationEnabled(true)

        myWebView.webViewClient = WebViewClient()
        
        // Handle Location Permissions in WebView
        myWebView.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(
                origin: String,
                callback: GeolocationPermissions.Callback
            ) {
                callback.invoke(origin, true, false)
            }
        }

        // Load your App URL
        myWebView.loadUrl("https://ais-pre-ggyd5kgthuzefx7ajcmmeb-776606595002.asia-southeast1.run.app")
    }

    override fun onBackPressed() {
        val myWebView: WebView = findViewById(R.id.webView)
        if (myWebView.canGoBack()) {
            myWebView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
```

## 4. Exporting the web project
To get the source code of this React app as a ZIP:
1. Click on the **Settings (Gear Icon)** in the top right of this AI Studio interface.
2. Select **Export to GitHub** or **Download as ZIP**.
3. You can then upload this code to your own hosting (Vercel, Firebase Hosting, etc.) to get your own production link.
