package com.example.note_spk

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.note_spk.databinding.ActivityHomeBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding

    private val auth by lazy {
        FirebaseAuth.getInstance()
    }

    private val db by lazy {
        FirebaseFirestore.getInstance()
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
        // PERFIL
        // =====================================================

        binding.btnPerfil.setOnClickListener {

            Toast.makeText(
                this,
                "Perfil",
                Toast.LENGTH_SHORT
            ).show()
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