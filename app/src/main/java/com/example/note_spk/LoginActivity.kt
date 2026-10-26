package com.example.note_spk

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import com.example.note_spk.databinding.ActivityLoginBinding
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var credentialManager: CredentialManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        credentialManager = CredentialManager.create(this)

        setupBiometricPrompt()
        setupClickListeners()
        setupTermsLinks()

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
            signInWithGoogle()
        }

        // Ir a la pantalla de registro
        binding.tvCreateAccount.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    // ---------- TÉRMINOS Y POLÍTICA DE PRIVACIDAD ----------

    /**
     * Convierte "Términos de Servicio" y "Política de Privacidad" dentro del aviso
     * en enlaces tocables que abren TermsActivity con el documento correspondiente.
     */
    private fun setupTermsLinks() {
        val fullText = getString(R.string.login_terms_notice)
        val spannable = SpannableString(fullText)

        fun addLink(label: String, action: () -> Unit) {
            val start = fullText.indexOf(label)
            if (start < 0) return
            spannable.setSpan(
                object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        action()
                    }

                    override fun updateDrawState(ds: TextPaint) {
                        super.updateDrawState(ds)
                        ds.color = Color.WHITE
                        ds.isUnderlineText = true
                        ds.typeface = Typeface.DEFAULT_BOLD
                    }
                },
                start,
                start + label.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        addLink(getString(R.string.terms_of_service)) {
            startActivity(TermsActivity.newIntent(this, TermsActivity.TYPE_TERMS))
        }
        addLink(getString(R.string.privacy_policy)) {
            startActivity(TermsActivity.newIntent(this, TermsActivity.TYPE_PRIVACY))
        }

        binding.tvTerms.text = spannable
        binding.tvTerms.movementMethod = LinkMovementMethod.getInstance()
        binding.tvTerms.highlightColor = Color.TRANSPARENT
    }

    // ---------- GOOGLE SIGN-IN ----------

    private fun signInWithGoogle() {
        val signInOption = GetSignInWithGoogleOption.Builder(
            serverClientId = getString(R.string.default_web_client_id)
        ).build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(
                    context = this@LoginActivity,
                    request = request
                )
                handleGoogleCredential(result.credential)
            } catch (e: GetCredentialException) {
                Toast.makeText(
                    this@LoginActivity,
                    "No se pudo iniciar sesión con Google",
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
                val googleIdTokenCredential =
                    GoogleIdTokenCredential.createFrom(credential.data)

                val idToken = googleIdTokenCredential.idToken
                val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)

                auth.signInWithCredential(firebaseCredential)
                    .addOnSuccessListener { result ->
                        val esUsuarioNuevo = result.additionalUserInfo?.isNewUser == true

                        if (esUsuarioNuevo) {
                            // Esta cuenta de Google nunca se ha registrado: no la dejamos entrar
                            // a medias, cerramos la sesión recién creada y mandamos a Registro.
                            auth.signOut()

                            Toast.makeText(
                                this,
                                "Esta cuenta no está registrada. Crea tu cuenta primero.",
                                Toast.LENGTH_LONG
                            ).show()

                            startActivity(Intent(this, RegisterActivity::class.java))
                        } else {
                            val user = result.user
                            goToHome(user?.displayName.orEmpty(), user?.email.orEmpty())
                        }
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Error al iniciar sesión con Google", Toast.LENGTH_SHORT).show()
                    }

            } catch (e: GoogleIdTokenParsingException) {
                Toast.makeText(this, "Credencial de Google no válida", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Tipo de credencial no reconocido", Toast.LENGTH_SHORT).show()
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