package com.example.note_spk

import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.example.note_spk.databinding.ActivityScanResultBinding
import java.util.Locale

class ScanResultActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CLASS_ID = "BILLETE_CLASE"
        const val EXTRA_CONFIDENCE = "EXTRA_CONFIDENCE"
    }

    private lateinit var binding: ActivityScanResultBinding
    private lateinit var textToSpeech: TextToSpeech

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityScanResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Recibir el resultado enviado por BilleteDetector
        val clase = intent.getIntExtra(EXTRA_CLASS_ID, -1)
        val confidence = intent.getFloatExtra(EXTRA_CONFIDENCE, 0f)

        Log.d("ScanResultActivity", "Clase recibida: $clase, Confianza: $confidence")

        val valorBillete = obtenerValorBillete(clase)

        if (valorBillete != null) {

            binding.tvResultado.text =
                "Tu billete es de:\n$valorBillete"

            textToSpeech = TextToSpeech(this) { status ->

                if (status == TextToSpeech.SUCCESS) {

                    textToSpeech.language = Locale("es", "CO")

                    textToSpeech.speak(
                        "Billete de $valorBillete pesos",
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        "resultado_billete"
                    )
                }
            }

        } else {

            binding.tvResultado.text =
                "No se pudo determinar el billete."
        }

        // Botón SÍ
        binding.btnSi.setOnClickListener {

            val intent = Intent(this, CameraActivity::class.java)

            startActivity(intent)
            finish()
        }

        // Botón NO
        binding.btnNo.setOnClickListener {

            val intent = Intent(this, HomeActivity::class.java)

            startActivity(intent)
            finish()
        }
    }

    private fun obtenerValorBillete(clase: Int): String? {

        return when (clase) {

            0 -> "100.000"
            1 -> "5.000"
            2 -> "50.000"
            3 -> "10.000"
            4 -> "2.000"
            5 -> "20.000"

            else -> null
        }
    }

    override fun onDestroy() {

        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }

        super.onDestroy()
    }
}