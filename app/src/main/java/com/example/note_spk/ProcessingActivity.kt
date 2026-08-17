package com.example.note_spk

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class ProcessingActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_processing)

        // Simular "procesamiento" durante 3 segundos
        android.os.Handler().postDelayed({
            val intent = Intent(this, ScanResultActivity::class.java)
            intent.putExtra("RESULT_VALUE", "$100.000") // valor simulado
            startActivity(intent)
            finish()
        },3000)
    }
}