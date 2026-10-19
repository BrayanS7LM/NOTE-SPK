package com.example.note_spk

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import com.example.note_spk.databinding.ActivityRegisterBinding
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var credentialManager: CredentialManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        credentialManager = CredentialManager.create(this)

        // Botón registrarse (correo y contraseña)
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
            signUpWithGoogle()
        }

        // Facebook
        binding.btnFacebook.setOnClickListener {
            Toast.makeText(this, "Registro con Facebook (futuro desarrollo)", Toast.LENGTH_SHORT).show()
        }

        // Ya tengo una cuenta
        binding.tvHaveAccount.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    // ==========================================
    // REGISTRO / INICIO DE SESIÓN CON GOOGLE
    // ==========================================

    private fun signUpWithGoogle() {
        val signInOption = GetSignInWithGoogleOption.Builder(
            serverClientId = getString(R.string.default_web_client_id)
        ).build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(
                    context = this@RegisterActivity,
                    request = request
                )
                handleGoogleCredential(result.credential)
            } catch (e: GetCredentialException) {
                Toast.makeText(
                    this@RegisterActivity,
                    "No se pudo completar el registro con Google",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun handleGoogleCredential(credential: androidx.credentials.Credential) {
        if (
            credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            try {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken
                val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)

                setLoading(true)

                auth.signInWithCredential(firebaseCredential)
                    .addOnSuccessListener { result ->
                        val user = result.user
                        if (user != null) {
                            guardarPerfilSiNoExiste(user)
                        } else {
                            setLoading(false)
                            Toast.makeText(this, "No se pudo crear el usuario", Toast.LENGTH_LONG).show()
                        }
                    }
                    .addOnFailureListener { e ->
                        setLoading(false)
                        Toast.makeText(
                            this,
                            e.localizedMessage ?: "Error al registrarse con Google",
                            Toast.LENGTH_LONG
                        ).show()
                    }

            } catch (e: GoogleIdTokenParsingException) {
                Toast.makeText(this, "Credencial de Google no válida", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Tipo de credencial no reconocido", Toast.LENGTH_SHORT).show()
        }
    }

    // ==========================================
    // GUARDAR PERFIL EN FIRESTORE (SOLO SI NO EXISTE)
    // ==========================================

    private fun guardarPerfilSiNoExiste(user: FirebaseUser) {
        val uid = user.uid
        val nombre = user.displayName ?: user.email?.substringBefore("@") ?: "Usuario"
        val correo = user.email ?: ""

        val docRef = db.collection("Usuarios").document(uid)

        docRef.get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    // El perfil ya existía (por ejemplo, se registró antes con correo/contraseña)
                    setLoading(false)
                    goToHome(nombre, correo)
                } else {
                    val userData = hashMapOf(
                        "nombre" to nombre,
                        "correo" to correo,
                        "fechaRegistro" to FieldValue.serverTimestamp(),
                        "activo" to true
                    )

                    docRef.set(userData)
                        .addOnSuccessListener {
                            setLoading(false)
                            Toast.makeText(this, "Cuenta creada correctamente", Toast.LENGTH_SHORT).show()
                            goToHome(nombre, correo)
                        }
                        .addOnFailureListener { e ->
                            setLoading(false)
                            Toast.makeText(this, "ERROR FIRESTORE:\n${e.message}", Toast.LENGTH_LONG).show()
                        }
                }
            }
            .addOnFailureListener { e ->
                setLoading(false)
                Toast.makeText(this, "ERROR FIRESTORE:\n${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    // ==========================================
    // VALIDAR CAMPOS
    // ==========================================

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

    // ==========================================
    // CREAR USUARIO (CORREO Y CONTRASEÑA)
    // ==========================================

    private fun registerUser(name: String, email: String, password: String) {
        setLoading(true)

        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val user = result.user

                if (user == null) {
                    setLoading(false)
                    Toast.makeText(this, "No se pudo crear el usuario", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                val uid = user.uid

                val profile = UserProfileChangeRequest.Builder()
                    .setDisplayName(name)
                    .build()

                user.updateProfile(profile)
                    .addOnCompleteListener {
                        val userData = hashMapOf(
                            "nombre" to name,
                            "correo" to email,
                            "fechaRegistro" to FieldValue.serverTimestamp(),
                            "activo" to true
                        )

                        db.collection("Usuarios")
                            .document(uid)
                            .set(userData)
                            .addOnSuccessListener {
                                setLoading(false)
                                Toast.makeText(this, "Cuenta creada correctamente", Toast.LENGTH_SHORT).show()
                                goToHome(name, email)
                            }
                            .addOnFailureListener { e ->
                                setLoading(false)
                                Toast.makeText(this, "ERROR FIRESTORE:\n${e.message}", Toast.LENGTH_LONG).show()
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

    private fun goToHome(name: String, email: String) {
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
            else -> {
                Toast.makeText(this, e.localizedMessage ?: "Error al crear la cuenta", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ==========================================
    // ESTADO DEL BOTÓN
    // ==========================================

    private fun setLoading(loading: Boolean) {
        binding.btnRegister.isEnabled = !loading
        binding.btnRegister.text = if (loading) "CREANDO CUENTA..." else "CONTINUAR"
    }
}