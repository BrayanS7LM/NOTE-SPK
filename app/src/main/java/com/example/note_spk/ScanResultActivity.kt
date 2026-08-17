package com.example.note_spk

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.note_spk.databinding.ActivityScanResultBinding
import kotlin.random.Random

class ScanResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScanResultBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Lista de valores simulados de billetes o monedas
        val valoresPosibles = listOf(
            "$1.000","2.000","5.000","10.000","20.000","50.000","100.000"
        )

        // Generar valor aleatorio
        val valorAleatorio = valoresPosibles[Random.nextInt(valoresPosibles.size)]

        // Mostrar en pantalla
        binding.tvResultado.text = "Tu billete es de:\n$valorAleatorio"

        // ✅ Botón "Sí": volver a la cámara (ScanActivity)
        binding.btnSi.setOnClickListener {
            val intent = Intent(this, CameraActivity::class.java)
            startActivity(intent)
            finish() // Cierra esta pantalla para evitar volver atrás
        }

        // ✅ Botón "No": volver al HomeActivity
        binding.btnNo.setOnClickListener {
            val intent = Intent(this, HomeActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}
