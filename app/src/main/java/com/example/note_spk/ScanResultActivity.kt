package com.example.note_spk

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.note_spk.databinding.ActivityScanResultBinding

class ScanResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScanResultBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding =
            ActivityScanResultBinding.inflate(layoutInflater)

        setContentView(binding.root)

        // =====================================================
        // RECIBIR RESULTADO REAL DEL DETECTOR
        // =====================================================

        val valorDetectado =
            intent.getStringExtra("BILLETE_DETECTADO")
                ?: "No identificado"

        val confianza =
            intent.getFloatExtra(
                "CONFIANZA",
                0f
            )

        // =====================================================
        // MOSTRAR RESULTADO
        // =====================================================

        binding.tvResultado.text =
            "Tu billete es de:\n$valorDetectado"

        // =====================================================
        // BOTÓN SÍ
        // Volver a escanear
        // =====================================================

        binding.btnSi.setOnClickListener {

            val intent =
                Intent(
                    this,
                    CameraActivity::class.java
                )

            startActivity(intent)

            finish()
        }

        // =====================================================
        // BOTÓN NO
        // Volver al Home
        // =====================================================

        binding.btnNo.setOnClickListener {

            val intent =
                Intent(
                    this,
                    HomeActivity::class.java
                )

            startActivity(intent)

            finish()
        }
    }
}