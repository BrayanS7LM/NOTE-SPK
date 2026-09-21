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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Firebase Authentication
        auth = FirebaseAuth.getInstance()

        // Firestore
        db = FirebaseFirestore.getInstance()

        // Botón registrarse
        binding.btnRegister.setOnClickListener {

            if (validateFields()) {

                val name = binding.etName.text.toString().trim()
                val email = binding.etEmail.text.toString().trim()
                val password = binding.etPassword.text.toString()

                registerUser(name, email, password)
            }
        }

        // Google
        binding.btnGoogle.setOnClickListener {
            Toast.makeText(
                this,
                "Registro con Google (futuro desarrollo)",
                Toast.LENGTH_SHORT
            ).show()
        }

        // Facebook
        binding.btnFacebook.setOnClickListener {
            Toast.makeText(
                this,
                "Registro con Facebook (futuro desarrollo)",
                Toast.LENGTH_SHORT
            ).show()
        }

        // Ya tengo una cuenta
        binding.tvHaveAccount.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    // ==========================================
    // VALIDAR CAMPOS
    // ==========================================

    private fun validateFields(): Boolean {

        val name = binding.etName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()

        // Nombre
        if (name.isEmpty()) {
            binding.etName.error = "Ingresa tu nombre"
            binding.etName.requestFocus()
            return false
        }

        // Correo vacío
        if (email.isEmpty()) {
            binding.etEmail.error = "Ingresa tu correo"
            binding.etEmail.requestFocus()
            return false
        }

        // Correo inválido
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.error = "Correo no válido"
            binding.etEmail.requestFocus()
            return false
        }

        // Contraseña
        if (password.length < 6) {
            binding.etPassword.error = "Mínimo 6 caracteres"
            binding.etPassword.requestFocus()
            return false
        }

        return true
    }

    // ==========================================
    // CREAR USUARIO
    // ==========================================

    private fun registerUser(
        name: String,
        email: String,
        password: String
    ) {

        setLoading(true)

        // Crear usuario en Firebase Authentication
        auth.createUserWithEmailAndPassword(email, password)

            .addOnSuccessListener { result ->

                val user = result.user

                if (user == null) {
                    setLoading(false)

                    Toast.makeText(
                        this,
                        "No se pudo crear el usuario",
                        Toast.LENGTH_LONG
                    ).show()

                    return@addOnSuccessListener
                }

                val uid = user.uid

                // Guardar nombre también en Authentication
                val profile = UserProfileChangeRequest.Builder()
                    .setDisplayName(name)
                    .build()

                user.updateProfile(profile)

                    .addOnCompleteListener {

                        // ==========================================
                        // DATOS DEL USUARIO PARA FIRESTORE
                        // ==========================================

                        val userData = hashMapOf(

                            "nombre" to name,

                            "correo" to email,

                            "fechaRegistro" to FieldValue.serverTimestamp(),

                            "activo" to true
                        )

                        // ==========================================
                        // GUARDAR EN FIRESTORE
                        // Usuarios / UID
                        // ==========================================

                        db.collection("Usuarios")
                            .document(uid)
                            .set(userData)

                            .addOnSuccessListener {

                                setLoading(false)

                                Toast.makeText(
                                    this,
                                    "Cuenta creada correctamente",
                                    Toast.LENGTH_SHORT
                                ).show()

                                goToHome(name, email)
                            }

                            .addOnFailureListener { e ->

                                setLoading(false)

                                Toast.makeText(
                                    this,
                                    "ERROR FIRESTORE:\n${e.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                    }
            }

            .addOnFailureListener { e ->

                setLoading(false)

                showError(e)
            }
    }

    // ==========================================
    // IR AL HOME
    // ==========================================

    private fun goToHome(
        name: String,
        email: String
    ) {

        val intent = Intent(this, HomeActivity::class.java)

        intent.putExtra("USER_EMAIL", email)

        intent.putExtra("USER_NAME", name)

        startActivity(intent)

        finish()
    }

    // ==========================================
    // MOSTRAR ERRORES
    // ==========================================

    private fun showError(e: Exception) {

        when (e) {

            is FirebaseAuthUserCollisionException -> {

                binding.etEmail.error =
                    "Este correo ya está registrado"

                binding.etEmail.requestFocus()
            }

            is FirebaseAuthWeakPasswordException -> {

                binding.etPassword.error =
                    "La contraseña es muy débil"

                binding.etPassword.requestFocus()
            }

            is FirebaseAuthInvalidCredentialsException -> {

                binding.etEmail.error =
                    "Correo no válido"

                binding.etEmail.requestFocus()
            }

            else -> {

                Toast.makeText(
                    this,
                    e.localizedMessage ?: "Error al crear la cuenta",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ==========================================
    // ESTADO DEL BOTÓN
    // ==========================================

    private fun setLoading(loading: Boolean) {

        binding.btnRegister.isEnabled = !loading

        binding.btnRegister.text =
            if (loading) {
                "CREANDO CUENTA..."
            } else {
                "CONTINUAR"
            }
    }
}