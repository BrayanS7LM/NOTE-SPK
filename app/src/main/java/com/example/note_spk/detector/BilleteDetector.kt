
package com.example.note_spk.detector

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BilleteDetector(context: Context) {

    companion object {

        const val INPUT_SIZE = 640
        const val UMBRAL_CONFIRMADO = 0.80f
        const val UMBRAL_POSIBLE = 0.40f

    }

    private val interpreter: Interpreter

    private val labels = listOf(
        "100_k",
        "10_k",
        "20_k",
        "2_k",
        "50_k",
        "5_k"
    )

    init {
        val modelo = FileUtil.loadMappedFile(context, "best.tflite")
        interpreter = Interpreter(modelo)
    }

    fun detectar(bitmap: Bitmap): List<Detection> {

        val imagen = ImageUtils.resize(bitmap)

        val input = convertirBitmap(imagen)

        val output = Array(1) { Array(10) { FloatArray(8400) } }

        interpreter.run(input, output)

        return procesarSalida(output)

    }


    private fun convertirBitmap(bitmap: Bitmap): ByteBuffer {

        val buffer = ByteBuffer.allocateDirect(
            4 * INPUT_SIZE * INPUT_SIZE * 3
        )

        buffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)

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

            buffer.putFloat(((pixel shr 16) and 0xFF) / 255f)
            buffer.putFloat(((pixel shr 8) and 0xFF) / 255f)
            buffer.putFloat((pixel and 0xFF) / 255f)

        }

        buffer.rewind()

        return buffer
    }

    private fun procesarSalida(
        salida: Array<Array<FloatArray>>
    ): List<Detection> {

        val detecciones = mutableListOf<Detection>()

        for (i in 0 until 8400) {

            var mejorClase = -1
            var mejorConfianza = 0f

            for (c in labels.indices) {

                val conf = salida[0][4 + c][i]

                if (conf > mejorConfianza) {

                    mejorConfianza = conf
                    mejorClase = c

                }

            }

            if (mejorConfianza >= UMBRAL_POSIBLE) {

                val x = salida[0][0][i]
                val y = salida[0][1][i]
                val w = salida[0][2][i]
                val h = salida[0][3][i]

                detecciones.add(

                    Detection(

                        label = labels[mejorClase],
                        confidence = mejorConfianza,
                        box = RectF(
                            x - w / 2,
                            y - h / 2,
                            x + w / 2,
                            y + h / 2
                        )

                    )

                )

            }

        }

        return detecciones.sortedByDescending {
            it.confidence
        }

    }

}