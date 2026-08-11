package com.upsi.smartbus

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.upsi.smartbus.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val auth by lazy { FirebaseAuth.getInstance() }
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
            // Move cursor to end
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

            val email = if (username.contains("@")) username else "$username@upsi.edu.my"
            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener {
                    navigateToMain()
                }
                .addOnFailureListener { exception ->
                    binding.btnLogin.isEnabled = true
                    binding.btnLogin.text = getString(R.string.btn_sign_in)
                    val errorMsg = exception.localizedMessage ?: "Login failed. Please check your credentials."
                    showError(errorMsg)
                }
        }
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
            text = getString(R.string.demo_title)
            textSize = 18f
            setTextColor(resources.getColor(R.color.text_primary, theme))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        container.addView(title)

        // Spacer
        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 24
            )
        }
        container.addView(spacer)

        // Credential entries
        val credentials = listOf(
            "🎓" to getString(R.string.demo_student),
            "🚌" to getString(R.string.demo_driver),
            "🛡️" to getString(R.string.demo_admin)
        )

        for ((emoji, text) in credentials) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, 16, 0, 16)
            }

            val emojiView = TextView(this).apply {
                this.text = emoji
                textSize = 20f
            }
            row.addView(emojiView)

            val spacerH = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(24, 1)
            }
            row.addView(spacerH)

            val credText = TextView(this).apply {
                this.text = text
                textSize = 14f
                setTextColor(resources.getColor(R.color.text_primary, theme))
                typeface = android.graphics.Typeface.MONOSPACE
            }
            row.addView(credText)

            container.addView(row)

            // Add divider (except after last)
            if (text != credentials.last().second) {
                val divider = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1
                    )
                    setBackgroundColor(resources.getColor(R.color.divider, theme))
                }
                container.addView(divider)
            }
        }

        // Bottom spacer
        val bottomSpacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 32
            )
        }
        container.addView(bottomSpacer)

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
