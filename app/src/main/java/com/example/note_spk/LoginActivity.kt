package com.example.note_spk

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.note_spk.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Botón continuar
        binding.btnContinue.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()

            if (email.isNotEmpty()) {
                Toast.makeText(this, "Correo ingresado: $email", Toast.LENGTH_SHORT).show()

                // 🔹 Ir a HomeActivity después del login
                val intent = Intent(this, HomeActivity::class.java)
                intent.putExtra("USER_EMAIL", email)
                startActivity(intent)

                // Cierra el LoginActivity para que no vuelva atrás
                finish()
            } else {
                Toast.makeText(this, "Por favor ingresa tu correo", Toast.LENGTH_SHORT).show()
            }
        }

        // Botón Google
        binding.btnGoogle.setOnClickListener {
            Toast.makeText(this, "Iniciar con Google (futuro desarrollo)", Toast.LENGTH_SHORT).show()
        }

        // Botón Facebook
        binding.btnFacebook.setOnClickListener {
            Toast.makeText(this, "Iniciar con Facebook (futuro desarrollo)", Toast.LENGTH_SHORT).show()
            }
        }
}