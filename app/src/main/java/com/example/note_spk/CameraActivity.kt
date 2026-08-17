package com.example.note_spk

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat

class CameraActivity : AppCompatActivity() {

    private lateinit var viewFinder: PreviewView
    private lateinit var btnTakePhoto: ImageButton
    private var imageCapture: ImageCapture? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        viewFinder = findViewById(R.id.viewFinder)
        btnTakePhoto = findViewById(R.id.btnTakePhoto)

        // Verificar permisos de cámara antes de iniciar
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        // Acción del botón para tomar la foto
        btnTakePhoto.setOnClickListener {
            takePhoto()
        }
    }

    // Función para capturar foto (simulada)
    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        try {
            // Aquí normalmente guardarías la foto o la procesarías.
            // Por ahora, simulamos que se tomó correctamente.
            Toast.makeText(this, "Foto capturada correctamente", Toast.LENGTH_SHORT).show()

            // Ir a la pantalla de procesamiento
            val intent = Intent(this, ProcessingActivity::class.java)
            startActivity(intent)
            finish()

        } catch (exc: Exception) {
            Toast.makeText(this, "Error al tomar foto: ${exc.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Configurar la cámara
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder().build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
            } catch (exc: Exception) {
                Toast.makeText(this, "Error al iniciar la cámara", Toast.LENGTH_SHORT).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    // Verificar permiso de cámara
    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        baseContext, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    // Solicitar permisos con launcher
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startCamera()
            } else {
                Toast.makeText(this, "Permiso de cámara denegado", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
}