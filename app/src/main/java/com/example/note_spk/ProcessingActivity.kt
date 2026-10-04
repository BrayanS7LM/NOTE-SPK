package com.example.note_spk

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class ProcessingActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_processing)

        // =========================================================
        // RECIBIR EL RESULTADO REAL DEL DETECTOR
        // =========================================================

        val billeteDetectado =
            intent.getStringExtra("BILLETE_DETECTADO")
                ?: "No identificado"

        val confianza =
            intent.getFloatExtra(
                "CONFIANZA",
                0f
            )

        // =========================================================
        // PASAR EL RESULTADO A LA PANTALLA FINAL
        // =========================================================

        val intentResultado =
            Intent(
                this,
                ScanResultActivity::class.java
            )

        intentResultado.putExtra(
            "BILLETE_DETECTADO",
            billeteDetectado
        )

        intentResultado.putExtra(
            "CONFIANZA",
            confianza
        )

        startActivity(intentResultado)

        finish()
    }
}