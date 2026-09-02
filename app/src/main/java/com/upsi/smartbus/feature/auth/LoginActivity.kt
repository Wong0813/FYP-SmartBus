package com.upsi.smartbus.feature.auth

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.upsi.smartbus.feature.MainActivity
import com.upsi.smartbus.R
import com.upsi.smartbus.core.data.FirestoreHelper
import com.upsi.smartbus.databinding.ActivityLoginBinding
import com.upsi.smartbus.feature.admin.seeder.AccountSeeder

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirestoreHelper.db }
    private var passwordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if already logged in
        if (auth.currentUser != null) {
            navigateToMain()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupPasswordToggle()
        setupLoginButton()
        setupHowToAccess()
    }

    private fun setupPasswordToggle() {
        binding.btnTogglePassword.setOnClickListener {
            passwordVisible = !passwordVisible
            if (passwordVisible) {
                binding.etPassword.inputType =
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                binding.btnTogglePassword.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            } else {
                binding.etPassword.inputType =
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                binding.btnTogglePassword.setImageResource(android.R.drawable.ic_menu_view)
            }
            binding.etPassword.setSelection(binding.etPassword.text.length)
        }
    }

    private fun setupLoginButton() {
        binding.btnLogin.setOnClickListener {
            val username = binding.etUsername.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                showError(getString(R.string.login_error_empty))
                return@setOnClickListener
            }

            hideError()
            binding.btnLogin.isEnabled = false
            binding.btnLogin.text = "Signing in…"

            val email = if (username.contains("@")) username.lowercase() else "${username.lowercase()}@upsi.edu.my"

            // 1. Try normal Firebase Auth signIn
            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener {
                    syncUserDocumentOnLogin(email)
                }
                .addOnFailureListener { exception ->
                    // 2. If user doesn't exist in Auth, auto-provision official account into Firebase Authentication
                    if (shouldAutoRegister(email, password)) {
                        autoRegisterInFirebaseAuth(email, password)
                    } else {
                        binding.btnLogin.isEnabled = true
                        binding.btnLogin.text = getString(R.string.btn_sign_in)
                        val errorMsg = exception.localizedMessage ?: "Login failed. Please check your credentials."
                        showError(errorMsg)
                    }
                }
        }
    }

    private fun shouldAutoRegister(email: String, pass: String): Boolean {
        val isOfficialDriver = Regex("driver(\\d+)@upsi\\.edu\\.my").matches(email)
        val isAdmin = email.startsWith("admin")
        val isStudent = email.startsWith("student")
        return (isOfficialDriver || isAdmin || isStudent) && (pass.length >= 6)
    }

    private fun autoRegisterInFirebaseAuth(email: String, pass: String) {
        auth.createUserWithEmailAndPassword(email, pass)
            .addOnSuccessListener { authResult ->
                syncUserDocumentOnLogin(email)
            }
            .addOnFailureListener { regEx ->
                binding.btnLogin.isEnabled = true
                binding.btnLogin.text = getString(R.string.btn_sign_in)
                val msg = regEx.localizedMessage ?: "Authentication error. Please check your password (min 6 characters)."
                showError(msg)
            }
    }

    private fun syncUserDocumentOnLogin(email: String) {
        val currentUid = auth.currentUser?.uid ?: return navigateToMain()

        // 1. If document for currentUid already exists, skip write
        db.collection("users").document(currentUid).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    navigateToMain()
                } else {
                    // 2. Check if this account already exists by email (e.g. admin_01, driver_01, student_01)
                    db.collection("users").whereEqualTo("email", email).get()
                        .addOnSuccessListener { snap ->
                            if (!snap.isEmpty) {
                                // Account already exists in Firestore! Do NOT duplicate.
                                navigateToMain()
                            } else {
                                // Only create new document for genuinely new users
                                val isDriver = email.startsWith("driver")
                                val isAdmin = email.startsWith("admin")
                                val role = when {
                                    isAdmin -> "ADMIN"
                                    isDriver -> "DRIVER"
                                    else -> "STUDENT"
                                }
                                val initialData = mapOf(
                                    "uid" to currentUid,
                                    "email" to email,
                                    "role" to role,
                                    "name" to email.substringBefore("@").replaceFirstChar { it.uppercase() },
                                    "accountType" to "OFFICIAL"
                                )
                                db.collection("users").document(currentUid).set(initialData)
                                    .addOnCompleteListener { navigateToMain() }
                            }
                        }
                        .addOnFailureListener { navigateToMain() }
                }
            }
            .addOnFailureListener { navigateToMain() }
    }

    private fun setupHowToAccess() {
        binding.btnHowToAccess.setOnClickListener {
            showDemoCredentialsSheet()
        }
    }

    private fun showDemoCredentialsSheet() {
        val dialog = BottomSheetDialog(this)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 48, 64, 48)
        }

        // Title
        val title = TextView(this).apply {
            text = "Official Account Credentials"
            textSize = 18f
            setTextColor(resources.getColor(R.color.text_primary, theme))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        container.addView(title)

        val desc = TextView(this).apply {
            text = "All accounts have default password: driver123 / admin123"
            textSize = 12f
            setTextColor(resources.getColor(R.color.text_secondary, theme))
            setPadding(0, 8, 0, 16)
        }
        container.addView(desc)

        val credentials = listOf(
            "🚌" to "driver1@upsi.edu.my ~ driver18 (Laluan 1..18)",
            "🚍" to "driver19@upsi.edu.my (Shuttle Campus KAB)",
            "🚍" to "driver20@upsi.edu.my (Shuttle Campus KUO)",
            "🛡️" to "admin@upsi.edu.my / admin123",
            "🎓" to "student1@upsi.edu.my / student123"
        )

        for ((emoji, text) in credentials) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, 12, 0, 12)
            }

            val emojiView = TextView(this).apply {
                this.text = emoji
                textSize = 18f
            }
            row.addView(emojiView)

            val spacerH = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(24, 1)
            }
            row.addView(spacerH)

            val credText = TextView(this).apply {
                this.text = text
                textSize = 13f
                setTextColor(resources.getColor(R.color.text_primary, theme))
            }
            row.addView(credText)
            container.addView(row)
        }

        dialog.setContentView(container)
        dialog.show()
    }

    private fun showError(message: String) {
        binding.errorBanner.visibility = View.VISIBLE
        binding.tvError.text = message
    }

    private fun hideError() {
        binding.errorBanner.visibility = View.GONE
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
