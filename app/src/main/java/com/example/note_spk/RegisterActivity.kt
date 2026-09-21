package com.example.note_spk

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.note_spk.databinding.ActivityRegisterBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.UserProfileChangeRequest

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Instancia de Firebase Authentication
        auth = FirebaseAuth.getInstance()

        // Botón continuar
        binding.btnRegister.setOnClickListener {
            if (validateFields()) {
                val name = binding.etName.text.toString().trim()
                val email = binding.etEmail.text.toString().trim()
                val password = binding.etPassword.text.toString()

                registerUser(name, email, password)
            }
        }

        // Botón Google
        binding.btnGoogle.setOnClickListener {
            Toast.makeText(this, "Registro con Google (futuro desarrollo)", Toast.LENGTH_SHORT).show()
        }

        // Botón Facebook
        binding.btnFacebook.setOnClickListener {
            Toast.makeText(this, "Registro con Facebook (futuro desarrollo)", Toast.LENGTH_SHORT).show()
        }

        // Volver al login
        binding.tvHaveAccount.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun validateFields(): Boolean {
        val name = binding.etName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()

        if (name.isEmpty()) {
            binding.etName.error = "Ingresa tu nombre"
            binding.etName.requestFocus()
            return false
        }
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
        if (password.length < 6) {
            binding.etPassword.error = "Mínimo 6 caracteres"
            binding.etPassword.requestFocus()
            return false
        }
        return true
    }

    private fun registerUser(name: String, email: String, password: String) {
        setLoading(true)

        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                // Guardar el nombre en el perfil del usuario
                val profile = UserProfileChangeRequest.Builder()
                    .setDisplayName(name)
                    .build()

                result.user?.updateProfile(profile)
                    ?.addOnCompleteListener {
                        // Aunque falle guardar el nombre, la cuenta ya fue creada
                        goToHome(name, email)
                    } ?: goToHome(name, email)
            }
            .addOnFailureListener { e ->
                setLoading(false)
                showError(e)
            }
    }

    private fun goToHome(name: String, email: String) {
        Toast.makeText(this, "Cuenta creada para $name", Toast.LENGTH_SHORT).show()

        val intent = Intent(this, HomeActivity::class.java)
        intent.putExtra("USER_EMAIL", email)
        intent.putExtra("USER_NAME", name)
        startActivity(intent)
        finish()
    }

    private fun showError(e: Exception) {
        when (e) {
            is FirebaseAuthUserCollisionException -> {
                binding.etEmail.error = "Este correo ya está registrado"
                binding.etEmail.requestFocus()
            }
            is FirebaseAuthWeakPasswordException -> {
                binding.etPassword.error = "La contraseña es muy débil"
                binding.etPassword.requestFocus()
            }
            is FirebaseAuthInvalidCredentialsException -> {
                binding.etEmail.error = "Correo no válido"
                binding.etEmail.requestFocus()
            }
            else -> Toast.makeText(
                this,
                e.localizedMessage ?: "Error al crear la cuenta",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Evita que el usuario pulse varias veces mientras se crea la cuenta
    private fun setLoading(loading: Boolean) {
        binding.btnRegister.isEnabled = !loading
        binding.btnRegister.text = if (loading) "CREANDO CUENTA..." else "CONTINUAR"
    }
}