package com.example.note_spk

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.example.note_spk.databinding.ActivityLoginBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var biometricPrompt: BiometricPrompt

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        setupBiometricPrompt()
        setupClickListeners()

        // Si ya hay una sesión guardada, intentar entrar con huella
        val currentUser = auth.currentUser
        if (currentUser != null) {
            if (isBiometricAvailable()) {
                showBiometricPrompt(currentUser)
            } else {
                // El celular no tiene huella configurada: continuar con la sesión guardada
                goToHome(currentUser.displayName.orEmpty(), currentUser.email.orEmpty())
            }
        }
    }

    private fun setupClickListeners() {
        // Botón continuar (correo y contraseña)
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
        // Ir a la pantalla de registro
        binding.tvCreateAccount.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    // ---------- HUELLA ----------

    private fun isBiometricAvailable(): Boolean {
        val biometricManager = BiometricManager.from(this)
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun setupBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)

        biometricPrompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    val user = auth.currentUser
                    goToHome(user?.displayName.orEmpty(), user?.email.orEmpty())
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    // El usuario canceló o eligió "Usar contraseña": se queda en el formulario visible
                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_USER_CANCELED
                    ) {
                        Toast.makeText(this@LoginActivity, errString, Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(
                        this@LoginActivity,
                        "Huella no reconocida, intenta de nuevo",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    private fun showBiometricPrompt(user: FirebaseUser) {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Iniciar sesión")
            .setSubtitle("Usa tu huella para entrar como ${user.displayName ?: user.email}")
            .setNegativeButtonText("Usar contraseña")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    // ---------- LOGIN CON CORREO Y CONTRASEÑA ----------

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