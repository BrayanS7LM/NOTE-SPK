package com.example.note_spk

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.lifecycle.lifecycleScope
import com.example.note_spk.databinding.ActivityHomeBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding

    private val auth by lazy {
        FirebaseAuth.getInstance()
    }

    private val db by lazy {
        FirebaseFirestore.getInstance()
    }

    private val credentialManager by lazy {
        CredentialManager.create(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding =
            ActivityHomeBinding.inflate(layoutInflater)

        setContentView(binding.root)

        // =====================================================
        // SALUDO
        // =====================================================

        mostrarSaludo()

        // =====================================================
        // BOTÓN ESCANEAR
        // =====================================================

        binding.btnScan.setOnClickListener {

            val intent =
                Intent(
                    this,
                    CameraActivity::class.java
                )

            startActivity(intent)
        }

        // =====================================================
        // PERFIL -> CERRAR SESIÓN
        // =====================================================

        binding.btnPerfil.setOnClickListener {
            mostrarDialogoCerrarSesion()
        }

        // =====================================================
        // MENÚ
        // =====================================================

        binding.btnMenu.setOnClickListener {

            Toast.makeText(
                this,
                "Menú",
                Toast.LENGTH_SHORT
            ).show()
        }

        // =====================================================
        // NAVEGACIÓN INFERIOR
        // =====================================================

        binding.bottomNavigationView.setOnItemSelectedListener {

                item: MenuItem ->

            when (item.itemId) {

                R.id.nav_home -> {
                    true
                }

                R.id.nav_search -> {

                    Toast.makeText(
                        this,
                        "Buscar",
                        Toast.LENGTH_SHORT
                    ).show()

                    true
                }

                R.id.nav_wallet -> {

                    Toast.makeText(
                        this,
                        "Billetera",
                        Toast.LENGTH_SHORT
                    ).show()

                    true
                }

                R.id.nav_notifications -> {

                    Toast.makeText(
                        this,
                        "Notificaciones",
                        Toast.LENGTH_SHORT
                    ).show()

                    true
                }

                else -> false
            }
        }
    }

    // =========================================================
    // ACTUALIZAR HOME CADA VEZ QUE VOLVEMOS A LA PANTALLA
    // =========================================================

    override fun onResume() {

        super.onResume()

        cargarEstadisticas()
    }

    // =========================================================
    // SALUDO
    // =========================================================

    private fun mostrarSaludo() {

        val usuario =
            auth.currentUser

        val email =
            usuario?.email

        val nombre =
            if (!email.isNullOrBlank()) {

                email.substringBefore("@")

            } else {

                intent.getStringExtra("USER_EMAIL")
                    ?: "Usuario"
            }

        binding.tvSaludo.text =
            "¡Hola, $nombre!"
    }

    // =========================================================
    // CERRAR SESIÓN
    // =========================================================

    private fun mostrarDialogoCerrarSesion() {

        AlertDialog.Builder(this)
            .setTitle("Cerrar sesión")
            .setMessage("¿Seguro que quieres cerrar sesión?")
            .setPositiveButton("Cerrar sesión") { _, _ ->
                cerrarSesion()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun cerrarSesion() {

        // Cierra la sesión de Firebase
        auth.signOut()

        // Limpia también el estado del selector de cuentas de Google,
        // para que la próxima vez vuelva a mostrar la lista de cuentas
        lifecycleScope.launch {
            try {
                credentialManager.clearCredentialState(
                    ClearCredentialStateRequest()
                )
            } catch (e: Exception) {
                // No es crítico si esto falla; la sesión de Firebase ya se cerró
            }

            val intent = Intent(this@HomeActivity, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    // =========================================================
    // CARGAR ESTADÍSTICAS DESDE FIRESTORE
    // =========================================================

    private fun cargarEstadisticas() {

        val usuario =
            auth.currentUser

        if (usuario == null) {

            binding.tvBilletes.text = "0"
            binding.tvDinero.text = "$0"

            return
        }

        val uid =
            usuario.uid

        db.collection("Usuarios")
            .document(uid)
            .collection("Reconocimientos")
            .whereEqualTo("confirmado", true)
            .get()
            .addOnSuccessListener { resultado ->

                var cantidadBilletes = 0

                var dineroTotal = 0L

                for (documento in resultado.documents) {

                    cantidadBilletes++

                    val valorObj = documento.get("valor")

                    val valor: Long? = when (valorObj) {

                        is Number -> valorObj.toLong()

                        is String -> valorObj.toLongOrNull()
                            ?: valorObj.filter { it.isDigit() }.toLongOrNull()

                        else -> null
                    }

                    if (valor != null) {

                        dineroTotal += valor
                    }
                }

                // =============================================
                // MOSTRAR BILLETES
                // =============================================

                binding.tvBilletes.text =
                    cantidadBilletes.toString()

                // =============================================
                // MOSTRAR DINERO
                // =============================================

                binding.tvDinero.text =
                    formatearPesos(dineroTotal)
            }
            .addOnFailureListener { error ->

                binding.tvBilletes.text = "0"
                binding.tvDinero.text = "$0"

                Toast.makeText(
                    this,
                    "No se pudieron cargar los reconocimientos",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    // =========================================================
    // FORMATO DE PESOS COLOMBIANOS
    // =========================================================

    private fun formatearPesos(valor: Long): String {

        return "$" +
                String.format(
                    "%,d",
                    valor
                ).replace(",", ".")
    }
}