package com.example.note_spk

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.example.note_spk.databinding.ActivityHomeBinding

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Recuperar datos del usuario desde Login
        val userEmail = intent.getStringExtra("USER_EMAIL") ?: "Usuario"
        binding.tvSaludo.text = "¡Hola,$userEmail!"

        // Datos simulados (pueden venir de BD o API luego)
        binding.tvBilletes.text = "55"
        binding.tvDinero.text = "$177.910"

        // Acción del botón de escaneo (asegúrate ScanActivity existe)
        binding.btnScan.setOnClickListener {
            val i = Intent(this, CameraActivity::class.java)
            startActivity(i)
        }

        // Botón de perfil
        binding.btnPerfil.setOnClickListener {
            // abrir perfil
        }

        // Botón de menú lateral
        binding.btnMenu.setOnClickListener {
            // abrir menú lateral
        }

        // Navegación inferior: especifica el tipo para evitar inferencia fallida
        binding.bottomNavigationView.setOnItemSelectedListener { item: MenuItem ->
            when (item.itemId) {
                R.id.nav_home -> {
                    // navegar a home (opcional)
                    true
                }
                R.id.nav_search -> {
                    // acción buscar
                    true
                }
                R.id.nav_wallet -> {
                    // acción billetera
                    true
                }
                R.id.nav_notifications -> {
                    // acción notificaciones
                    true
                }
                else -> false
            }
        }
    }
}