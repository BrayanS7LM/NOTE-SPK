package com.example.note_spk

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {

    private lateinit var viewFinder: PreviewView
    private lateinit var btnTakePhoto: ImageButton

    private var imageCapture: ImageCapture? = null

    private var camera: androidx.camera.core.Camera? = null

    // Analizador de imágenes
    private var imageAnalysis: ImageAnalysis? = null

    // Detector de billetes
    private lateinit var billeteDetector: BilleteDetector

    // Hilo para procesar las imágenes de CameraX
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_camera)

        viewFinder = findViewById(R.id.viewFinder)
        btnTakePhoto = findViewById(R.id.btnTakePhoto)

        // =========================================================
        // INICIALIZAR DETECTOR
        // =========================================================

        billeteDetector = BilleteDetector(this)

        // =========================================================
        // EXECUTOR PARA CAMERA X
        // =========================================================

        cameraExecutor = Executors.newSingleThreadExecutor()

        // =========================================================
        // PERMISO DE CÁMARA
        // =========================================================

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(
                Manifest.permission.CAMERA
            )
        }

        // =========================================================
        // BOTÓN DE FOTO
        // =========================================================

        btnTakePhoto.setOnClickListener {
            takePhoto()
        }
    }

    // =============================================================
    // INICIAR CÁMARA
    // =============================================================

    private fun startCamera() {

        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({

            val cameraProvider =
                cameraProviderFuture.get()

            // =====================================================
            // PREVIEW
            // =====================================================

            val preview =
                Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(
                            viewFinder.surfaceProvider
                        )
                    }

            // =====================================================
            // CAPTURA DE FOTO
            // =====================================================

            imageCapture =
                ImageCapture.Builder()
                    .build()

            // =====================================================
            // ANÁLISIS DE IMAGEN
            // =====================================================

            imageAnalysis =
                ImageAnalysis.Builder()
                    .setBackpressureStrategy(
                        ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                    )
                    .build()

            imageAnalysis?.setAnalyzer(
                cameraExecutor
            ) { imageProxy ->

                // Enviar cada frame al detector
                billeteDetector.processImage(
                    imageProxy
                )
            }

            // =====================================================
            // CÁMARA TRASERA
            // =====================================================

            val cameraSelector =
                CameraSelector.DEFAULT_BACK_CAMERA

            try {

                // Eliminar configuraciones anteriores
                cameraProvider.unbindAll()

                // =================================================
                // CONECTAR TODO
                // =================================================

                camera =
                    cameraProvider.bindToLifecycle(
                        this,
                        cameraSelector,
                        preview,
                        imageCapture,
                        imageAnalysis
                    )

                Log.d(
                    "CameraDebug",
                    "Cámara iniciada correctamente"
                )

                Log.d(
                    "CameraDebug",
                    "Lens facing: ${camera?.cameraInfo?.lensFacing}"
                )

                Log.d(
                    "CameraDebug",
                    "Tiene flash: ${camera?.cameraInfo?.hasFlashUnit()}"
                )

                // =================================================
                // FLASH
                // =================================================

                if (
                    camera?.cameraInfo?.hasFlashUnit() == true
                ) {

                    camera?.cameraControl
                        ?.enableTorch(true)

                } else {

                    Toast.makeText(
                        this,
                        "Este dispositivo no tiene flash",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (exc: Exception) {

                Log.e(
                    "CameraDebug",
                    "Error al iniciar cámara",
                    exc
                )

                Toast.makeText(
                    this,
                    "Error al iniciar la cámara: ${exc.message}",
                    Toast.LENGTH_LONG
                ).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    // =============================================================
    // TOMAR FOTO
    // =============================================================

    private fun takePhoto() {

        val imageCapture =
            imageCapture ?: return

        try {

            Toast.makeText(
                this,
                "Foto capturada correctamente",
                Toast.LENGTH_SHORT
            ).show()

            val intent =
                Intent(
                    this,
                    ProcessingActivity::class.java
                )

            startActivity(intent)

            finish()

        } catch (exc: Exception) {

            Toast.makeText(
                this,
                "Error al tomar foto: ${exc.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // =============================================================
    // VERIFICAR PERMISO
    // =============================================================

    private fun allPermissionsGranted(): Boolean {

        return ContextCompat.checkSelfPermission(
            baseContext,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    // =============================================================
    // SOLICITAR PERMISO
    // =============================================================

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->

            if (isGranted) {

                startCamera()

            } else {

                Toast.makeText(
                    this,
                    "Permiso de cámara denegado",
                    Toast.LENGTH_SHORT
                ).show()

                finish()
            }
        }

    // =============================================================
    // LIBERAR RECURSOS
    // =============================================================

    override fun onDestroy() {

        super.onDestroy()

        if (::cameraExecutor.isInitialized) {
            cameraExecutor.shutdown()
        }
    }
}