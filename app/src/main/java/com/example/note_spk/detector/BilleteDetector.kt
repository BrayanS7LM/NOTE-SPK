package com.example.note_spk.detector

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class BilleteDetector(
    private val context: Context,
    private val onGuidance: (String) -> Unit,
    private val onConfirmed: (classId: Int, confidence: Float) -> Unit
) : ImageAnalysis.Analyzer {

    companion object {

        private const val TAG = "BilleteDetector"

        private const val INPUT_SIZE = 640

        /*
  * Detección inicial.
  */
        private const val POSSIBLE_CONFIDENCE = 0.40f
        private const val CONFIRMED_CONFIDENCE = 0.70f

        /*
         * Cantidad de fotogramas consecutivos necesarios
         * para considerar estable una detección.
         */
        private const val REQUIRED_STABLE_FRAMES = 5

        /*
         * Área aproximada del billete dentro de la imagen.
         */
        private const val TOO_FAR_AREA = 0.12f
        private const val TOO_CLOSE_AREA = 0.75f

        /*
         * Tiempo mínimo entre mensajes de voz.
         */
        private const val GUIDANCE_INTERVAL_MS = 1800L

        /*
         * Tiempo mínimo antes de permitir una nueva confirmación.
         */
        private const val CONFIRMATION_COOLDOWN_MS = 3000L
    }

    private val interpreter: Interpreter

    private val tts: TextToSpeech

    private var lastGuidanceTime = 0L
    private var lastConfirmationTime = 0L

    private var lastClassId = -1
    private var stableFrames = 0

    private var alreadyConfirmed = false

    init {

        /*
         * Cargar el modelo desde assets.
         *
         * Debe existir:
         *
         * app/src/main/assets/best.tflite
         */
        val model = context.assets.open("best.tflite").use {
            it.readBytes()
        }

        val buffer = ByteBuffer.allocateDirect(model.size)
        buffer.order(ByteOrder.nativeOrder())
        buffer.put(model)
        buffer.rewind()

        interpreter = Interpreter(buffer)

        /*
         * TextToSpeech
         */
        tts = TextToSpeech(context) { status ->

            if (status == TextToSpeech.SUCCESS) {

                tts.language = Locale("es", "ES")

                tts.setSpeechRate(0.95f)

                Log.d(TAG, "TextToSpeech inicializado")

            } else {

                Log.e(TAG, "No se pudo inicializar TextToSpeech")
            }
        }
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {

        try {

            if (alreadyConfirmed) {
                imageProxy.close()
                return
            }

            val image = imageProxy.image

            if (image == null) {
                imageProxy.close()
                return
            }

            /*
             * IMPORTANTE:
             *
             * Utilizamos la función existente del proyecto
             * para convertir ImageProxy -> Bitmap.
             */
            val bitmap = imageProxy.toBitmap()

            val resizedBitmap = Bitmap.createScaledBitmap(
                bitmap,
                INPUT_SIZE,
                INPUT_SIZE,
                true
            )

            val input = bitmapToByteBuffer(resizedBitmap)

            /*
             * YOLOv8:
             *
             * [1, 10, 8400]
             *
             * 4 valores de bounding box
             * + 6 clases
             */
            val output = Array(
                1
            ) {
                Array(
                    10
                ) {
                    FloatArray(8400)
                }
            }

            interpreter.run(input, output)

            val detection = processOutput(output)

            if (detection == null) {

                stableFrames = 0
                lastClassId = -1

                speakGuidance(
                    "Buscando el billete"
                )

            } else {

                handleDetection(
                    detection
                )
            }

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Error analizando imagen",
                e
            )

        } finally {

            imageProxy.close()
        }
    }

    private fun bitmapToByteBuffer(
        bitmap: Bitmap
    ): ByteBuffer {

        val imageSize =
            INPUT_SIZE * INPUT_SIZE * 3 * 4

        val buffer =
            ByteBuffer.allocateDirect(imageSize)

        buffer.order(
            ByteOrder.nativeOrder()
        )

        val pixels =
            IntArray(
                INPUT_SIZE * INPUT_SIZE
            )

        bitmap.getPixels(
            pixels,
            0,
            INPUT_SIZE,
            0,
            0,
            INPUT_SIZE,
            INPUT_SIZE
        )

        for (pixel in pixels) {

            val r =
                ((pixel shr 16) and 0xFF) / 255.0f

            val g =
                ((pixel shr 8) and 0xFF) / 255.0f

            val b =
                (pixel and 0xFF) / 255.0f

            buffer.putFloat(r)
            buffer.putFloat(g)
            buffer.putFloat(b)
        }

        buffer.rewind()

        return buffer
    }

    private data class Detection(
        val classId: Int,
        val confidence: Float,
        val width: Float,
        val height: Float
    )

    private fun processOutput(
        output: Array<Array<FloatArray>>
    ): Detection? {

        var bestClass = -1
        var bestConfidence = 0f

        var bestWidth = 0f
        var bestHeight = 0f

        /*
         * El modelo tiene 8400 candidatos.
         */
        for (i in 0 until 8400) {

            val x =
                output[0][0][i]

            val y =
                output[0][1][i]

            val width =
                output[0][2][i]

            val height =
                output[0][3][i]

            /*
             * Las 6 clases comienzan en el índice 4.
             */
            var localClass = -1
            var localConfidence = 0f

            for (classIndex in 0 until 6) {

                val confidence =
                    output[0][4 + classIndex][i]

                if (confidence > localConfidence) {

                    localConfidence =
                        confidence

                    localClass =
                        classIndex
                }
            }

            if (
                localClass >= 0 &&
                localConfidence > bestConfidence
            ) {

                bestConfidence =
                    localConfidence

                bestClass =
                    localClass

                bestWidth =
                    width

                bestHeight =
                    height
            }
        }

        if (
            bestClass < 0 ||
            bestConfidence < POSSIBLE_CONFIDENCE
        ) {
            return null
        }

        return Detection(
            classId = bestClass,
            confidence = bestConfidence,
            width = bestWidth,
            height = bestHeight
        )
    }

    private fun handleDetection(
        detection: Detection
    ) {

        val classId =
            detection.classId

        val confidence =
            detection.confidence

        val width =
            detection.width

        val height =
            detection.height

        /*
         * Aproximación del tamaño del billete
         * dentro del fotograma.
         */
        val area =
            (width * height)
                .coerceIn(0f, 1f)

        Log.d(
            TAG,
            "DETECCIÓN: clase=$classId " +
                    "confianza=${confidence * 100}% " +
                    "area=$area"
        )

        /*
         * Primero damos orientación física.
         */

        if (area < TOO_FAR_AREA) {

            resetStability()

            speakGuidance(
                "El billete está muy lejos. " +
                        "Acerca la cámara lentamente."
            )

            return
        }

        if (area > TOO_CLOSE_AREA) {

            resetStability()

            speakGuidance(
                "El billete está muy cerca. " +
                        "Aleja un poco la cámara."
            )

            return
        }

        /*
         * Si llegó hasta aquí, el tamaño
         * del billete es razonable.
         */

        if (classId == lastClassId) {

            stableFrames++

        } else {

            lastClassId =
                classId

            stableFrames = 1
        }

        /*
         * Confianza baja.
         */
        if (confidence < POSSIBLE_CONFIDENCE) {

            speakGuidance(
                "No puedo identificar bien el billete. " +
                        "Muévelo un poco y mantenlo centrado."
            )

            return
        }

        /*
         * Detección posible.
         */
        if (confidence < CONFIRMED_CONFIDENCE) {

            speakGuidance(
                "Billete detectado. " +
                        "Mantén la cámara estable."
            )

            return
        }

        /*
         * Confianza >= 80%.
         *
         * Ahora exigimos varios fotogramas
         * consecutivos con la misma clase.
         */
        if (
            confidence >= CONFIRMED_CONFIDENCE &&
            stableFrames >= REQUIRED_STABLE_FRAMES
        ) {

            val now =
                SystemClock.elapsedRealtime()

            if (
                now - lastConfirmationTime
                >= CONFIRMATION_COOLDOWN_MS
            ) {

                alreadyConfirmed = true

                lastConfirmationTime =
                    now

                Log.d(
                    TAG,
                    "DETECCIÓN CONFIRMADA: " +
                            "clase=$classId " +
                            "confianza=${confidence * 100}%"
                )

                onConfirmed(
                    classId,
                    confidence
                )
            }
        } else {

            speakGuidance(
                "Billete detectado. " +
                        "Mantén la cámara estable."
            )
        }
    }

    private fun resetStability() {

        stableFrames = 0
        lastClassId = -1
    }

    private fun speakGuidance(
        message: String
    ) {

        val now =
            SystemClock.elapsedRealtime()

        if (
            now - lastGuidanceTime
            < GUIDANCE_INTERVAL_MS
        ) {
            return
        }

        lastGuidanceTime =
            now

        onGuidance(message)

        tts.speak(
            message,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "guidance"
        )
    }

    fun close() {

        try {
            interpreter.close()
        } catch (_: Exception) {
        }

        try {
            tts.stop()
            tts.shutdown()
        } catch (_: Exception) {
        }
    }
}