package com.example.look_a_bird.ui.auth

import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.look_a_bird.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth

class LoginFragment : Fragment() {

    private lateinit var editTextEmail: TextInputEditText
    private lateinit var editTextPassword: TextInputEditText
    private lateinit var buttonLogin: MaterialButton
    private lateinit var textRegisterLink: TextView
    private lateinit var progressBar: ProgressBar

    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_login, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews(view)
        setupClickListeners()
    }

    private fun setupViews(view: View) {
        editTextEmail = view.findViewById(R.id.edit_text_email)
        editTextPassword = view.findViewById(R.id.edit_text_password)
        buttonLogin = view.findViewById(R.id.button_login)
        textRegisterLink = view.findViewById(R.id.text_register_link)
        progressBar = view.findViewById(R.id.progress_bar)
    }

    private fun setupClickListeners() {
        buttonLogin.setOnClickListener { performLogin() }
        textRegisterLink.setOnClickListener { navigateToRegister() }
    }

    private fun performLogin() {
        val email = editTextEmail.text.toString().trim()
        val password = editTextPassword.text.toString()

        if (!validateInput(email, password)) return

        showLoading(true)
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                showLoading(false)
                if (task.isSuccessful) {
                    Toast.makeText(context, "Login successful!", Toast.LENGTH_SHORT).show()
                    navigateToHome()
                } else {
                    Toast.makeText(
                        context,
                        "Error: ${task.exception?.message ?: "Login failed"}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    private fun validateInput(email: String, password: String): Boolean {
        if (email.isEmpty()) {
            editTextEmail.error = "Email is required"
            return false
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            editTextEmail.error = "Please enter a valid email"
            return false
        }

        if (password.isEmpty()) {
            editTextPassword.error = "Password is required"
            return false
        }

        if (password.length < 6) {
            editTextPassword.error = "Password must be at least 6 characters"
            return false
        }

        return true
    }

    private fun navigateToRegister() {
        val action = LoginFragmentDirections.actionLoginToRegister()
        findNavController().navigate(action)
    }

    private fun navigateToHome() {
        val action = LoginFragmentDirections.actionLoginToHome()
        findNavController().navigate(action)
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        buttonLogin.isEnabled = !show
    }
}