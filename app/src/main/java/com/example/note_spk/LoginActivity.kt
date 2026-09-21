package com.example.note_spk

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.note_spk.databinding.ActivityLoginBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        // Si ya hay una sesión iniciada, saltar directo a Home
        val currentUser = auth.currentUser
        if (currentUser != null) {
            goToHome(currentUser.displayName.orEmpty(), currentUser.email.orEmpty())
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Botón continuar
        binding.btnContinue.setOnClickListener {
            if (validateFields()) {
                val email = binding.etEmail.text.toString().trim()
                val password = binding.etPassword.text.toString()
                loginUser(email, password)
            }
        }

        // Botón Google
        binding.btnGoogle.setOnClickListener {
            Toast.makeText(this, "Iniciar con Google (futuro desarrollo)", Toast.LENGTH_SHORT).show()
        }

        // Botón Facebook
        binding.btnFacebook.setOnClickListener {
            Toast.makeText(this, "Iniciar con Facebook (futuro desarrollo)", Toast.LENGTH_SHORT).show()
        }

        // Ir a la pantalla de registro
        binding.tvCreateAccount.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun validateFields(): Boolean {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()

        if (email.isEmpty()) {
            binding.etEmail.error = "Ingresa tu correo"
            binding.etEmail.requestFocus()
            return false
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.error = "Correo no válido"
            binding.etEmail.requestFocus()
            return false
        }
        if (password.isEmpty()) {
            binding.etPassword.error = "Ingresa tu contraseña"
            binding.etPassword.requestFocus()
            return false
        }
        return true
    }

    private fun loginUser(email: String, password: String) {
        setLoading(true)

        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val user = result.user
                goToHome(user?.displayName.orEmpty(), user?.email ?: email)
            }
            .addOnFailureListener { e ->
                setLoading(false)
                when (e) {
                    is FirebaseAuthInvalidUserException,
                    is FirebaseAuthInvalidCredentialsException ->
                        Toast.makeText(this, "Correo o contraseña incorrectos", Toast.LENGTH_LONG).show()
                    else ->
                        Toast.makeText(
                            this,
                            e.localizedMessage ?: "Error al iniciar sesión",
                            Toast.LENGTH_LONG
                        ).show()
                }
            }
    }

    private fun goToHome(name: String, email: String) {
        val intent = Intent(this, HomeActivity::class.java)
        intent.putExtra("USER_EMAIL", email)
        intent.putExtra("USER_NAME", name)
        startActivity(intent)

        // Cierra el LoginActivity para que no vuelva atrás
        finish()
    }

    // Evita que el usuario pulse varias veces mientras se inicia sesión
    private fun setLoading(loading: Boolean) {
        binding.btnContinue.isEnabled = !loading
        binding.btnContinue.text = if (loading) "ENTRANDO..." else "CONTINUAR"
    }
}