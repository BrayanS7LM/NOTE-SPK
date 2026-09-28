package com.example.note_spk

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.example.note_spk.detector.BilleteDetector
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {

    private lateinit var viewFinder: PreviewView

    private lateinit var btnTakePhoto: ImageButton

    private var camera: Camera? = null

    private var imageAnalysis: ImageAnalysis? = null

    private lateinit var cameraExecutor: ExecutorService

    private lateinit var billeteDetector: BilleteDetector

    private var resultAlreadyOpened = false

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_camera
        )

        viewFinder =
            findViewById(R.id.viewFinder)

        btnTakePhoto =
            findViewById(R.id.btnTakePhoto)

        /*
         * Executor para análisis de imágenes.
         */
        cameraExecutor =
            Executors.newSingleThreadExecutor()

        /*
         * Crear detector.
         */
        billeteDetector =
            BilleteDetector(
                context = this,

                onGuidance = { message ->

                    runOnUiThread {

                        Toast.makeText(
                            this,
                            message,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },

                onConfirmed = {
                        classId,
                        confidence ->

                    runOnUiThread {

                        openScanResult(
                            classId,
                            confidence
                        )
                    }
                }
            )

        /*
         * El botón físico/visual de captura
         * queda disponible, pero durante esta
         * prueba la detección es automática.
         */
        btnTakePhoto.setOnClickListener {

            Toast.makeText(
                this,
                "Mantén el billete frente a la cámara",
                Toast.LENGTH_SHORT
            ).show()
        }

        /*
         * Permisos.
         */
        if (allPermissionsGranted()) {

            startCamera()

        } else {

            requestPermissionLauncher.launch(
                Manifest.permission.CAMERA
            )
        }
    }

    @OptIn(ExperimentalGetImage::class)
    private fun startCamera() {

        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(
                this
            )

        cameraProviderFuture.addListener({

            val cameraProvider =
                cameraProviderFuture.get()

            /*
             * Preview.
             */
            val preview =
                Preview.Builder()
                    .build()
                    .also {

                        it.setSurfaceProvider(
                            viewFinder.surfaceProvider
                        )
                    }

            /*
             * Análisis continuo.
             */
            imageAnalysis =
                ImageAnalysis.Builder()
                    .setBackpressureStrategy(
                        ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                    )
                    .setImageQueueDepth(1)
                    .build()
                    .also { analysis ->

                        analysis.setAnalyzer(
                            cameraExecutor,
                            billeteDetector
                        )
                    }

            /*
             * Cámara trasera.
             */
            val cameraSelector =
                CameraSelector.DEFAULT_BACK_CAMERA

            try {

                cameraProvider.unbindAll()

                camera =
                    cameraProvider.bindToLifecycle(
                        this,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )

                Log.d(
                    "CameraDebug",
                    "Cámara iniciada correctamente"
                )

                /*
                 * Flash encendido.
                 *
                 * Lo dejamos como estaba en
                 * nuestro flujo anterior.
                 */
                if (
                    camera
                        ?.cameraInfo
                        ?.hasFlashUnit() == true
                ) {

                    camera
                        ?.cameraControl
                        ?.enableTorch(true)

                } else {

                    Log.d(
                        "CameraDebug",
                        "El dispositivo no tiene flash"
                    )
                }

            } catch (exc: Exception) {

                Log.e(
                    "CameraDebug",
                    "Error al iniciar cámara",
                    exc
                )

                Toast.makeText(
                    this,
                    "Error al iniciar la cámara",
                    Toast.LENGTH_SHORT
                ).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun openScanResult(
        classId: Int,
        confidence: Float
    ) {

        /*
         * Evitar abrir varias veces
         * ScanResultActivity.
         */
        if (resultAlreadyOpened) {
            return
        }

        resultAlreadyOpened = true

        /*
         * Detener análisis.
         */
        imageAnalysis?.clearAnalyzer()

        /*
         * Crear Intent con resultado real.
         */
        val intent =
            Intent(
                this,
                ScanResultActivity::class.java
            ).apply {

                putExtra(
                    ScanResultActivity.EXTRA_CLASS_ID,
                    classId
                )

                putExtra(
                    ScanResultActivity.EXTRA_CONFIDENCE,
                    confidence
                )
            }

        startActivity(intent)

        finish()
    }

    private fun allPermissionsGranted():
            Boolean {

        return ContextCompat.checkSelfPermission(
            baseContext,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

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

    override fun onDestroy() {

        super.onDestroy()

        try {

            imageAnalysis?.clearAnalyzer()

            billeteDetector.close()

            cameraExecutor.shutdown()

        } catch (e: Exception) {

            Log.e(
                "CameraDebug",
                "Error cerrando detector",
                e
            )
        }
    }
}