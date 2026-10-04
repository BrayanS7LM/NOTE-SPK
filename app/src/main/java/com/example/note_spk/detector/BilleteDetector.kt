package com.example.note_spk
import android.util.Log
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.RectF
import android.speech.tts.TextToSpeech
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import org.tensorflow.lite.Interpreter
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.Locale

class BilleteDetector(
    private val context: Context,
    private val onDetectionConfirmed: (String, Float) -> Unit
) : TextToSpeech.OnInitListener {

    companion object {

        // =========================================================
        // CONFIGURACIÓN DEL MODELO
        // =========================================================

        private const val MODEL_NAME = "best.tflite"

        private const val INPUT_SIZE = 640

        private const val NUM_CLASSES = 6

        private const val NUM_DETECTIONS = 8400

        // =========================================================
        // UMBRALES
        // =========================================================

        private const val CONFIRM_THRESHOLD = 0.80f

        private const val POSSIBLE_THRESHOLD = 0.40f

        // Tiempo para evitar repetir el mismo billete
        private const val ANNOUNCE_COOLDOWN_MS = 2500L
        // =========================================================
// ESTABILIZACIÓN DE DETECCIONES
// =========================================================

        // Cantidad de detecciones consecutivas necesarias
        private const val REQUIRED_STABLE_DETECTIONS = 3

        // Confianza mínima para participar en la confirmación
        private const val STABILITY_MIN_CONFIDENCE = 0.80f

        // Tiempo máximo permitido entre detecciones consecutivas
        private const val STABILITY_TIMEOUT_MS = 1500L

        // =========================================================
        // CLASES DEL MODELO
        // =========================================================

        private val CLASS_NAMES = arrayOf(
            "Cien_Mil",
            "Diez_Mil",
            "Veinte_Mil",
            "Dos_Mil",
            "Cincuenta_Mil",
            "Cinco_Mil"
        )

        // =========================================================
        // NOMBRES PARA TEXT TO SPEECH
        // =========================================================

        private val SPEECH_NAMES = arrayOf(
            "cien mil pesos",
            "diez mil pesos",
            "veinte mil pesos",
            "dos mil pesos",
            "cincuenta mil pesos",
            "cinco mil pesos"
        )
    }
    private val CLASS_VALUES = longArrayOf(
        100000L, // Cien mil
        10000L,  // Diez mil
        20000L,  // Veinte mil
        2000L,   // Dos mil
        50000L,  // Cincuenta mil
        5000L    // Cinco mil
    )

    // =============================================================
    // VARIABLES
    // =============================================================

    private var interpreter: Interpreter? = null

    private lateinit var textToSpeech: TextToSpeech

    private val firestore =
        FirebaseFirestore.getInstance()

    private val auth =
        FirebaseAuth.getInstance()

    private var lastAnnouncedClass = -1

    private var lastAnnouncedTime = 0L

    @Volatile
    private var isProcessing = false

    // =========================================================
    // ÚLTIMO RESULTADO DE DETECCIÓN
    // =========================================================

    data class DetectionResult(
        val valor: String,
        val confianza: Float
    )

    @Volatile
    private var latestResult: DetectionResult? = null

    @Volatile
    private var lastResultTime: Long = 0L

    fun getLatestResult(): DetectionResult? {
        val currentTime = System.currentTimeMillis()
        if (latestResult != null && currentTime - lastResultTime <= 3000L) {
            return latestResult
        }
        return null
    }

// =========================================================
// ESTABILIZACIÓN
// =========================================================

    // Clase que estamos acumulando
    private var stableClassId = -1

    // Cantidad de frames consecutivos con la misma clase
    private var stableDetectionCount = 0

    // Suma de confianzas para calcular el promedio
    private var stableConfidenceSum = 0f

    // Momento de la última detección válida
    private var lastStableDetectionTime = 0L

    init {

        Log.d(
            "BilleteDetector",
            "INICIANDO DETECTOR"
        )

        try {

            // Cargar modelo
            interpreter = Interpreter(
                loadModelFile(
                    context,
                    MODEL_NAME
                )
            )

            Log.d(
                "BilleteDetector",
                "MODELO best.tflite CARGADO CORRECTAMENTE"
            )

            // Inicializar TextToSpeech
            textToSpeech = TextToSpeech(
                context,
                this
            )

            Log.d(
                "BilleteDetector",
                "TEXT TO SPEECH INICIADO"
            )

        } catch (e: Exception) {

            Log.e(
                "BilleteDetector",
                "ERROR AL INICIALIZAR DETECTOR",
                e
            )
        }
    }

    // =============================================================
    // TEXT TO SPEECH
    // =============================================================

    override fun onInit(status: Int) {

        if (status == TextToSpeech.SUCCESS) {

            val result =
                textToSpeech.setLanguage(
                    Locale("es", "CO")
                )

            if (
                result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED
            ) {

                textToSpeech.language =
                    Locale("es")
            }

            textToSpeech.setSpeechRate(0.95f)
        }
    }

    // =============================================================
    // PROCESAR IMAGEN DE CAMERAX
    // =============================================================

    fun processImage(
        imageProxy: ImageProxy
    ) {

        Log.d(
            "BilleteDetector",
            "FRAME RECIBIDO: ${imageProxy.width}x${imageProxy.height}"
        )

        // ---------------------------------------------------------
        // Evitar procesar varias imágenes simultáneamente
        // ---------------------------------------------------------
        // ---------------------------------------------------------

        if (isProcessing) {

            imageProxy.close()

            return
        }

        isProcessing = true

        try {

            // -----------------------------------------------------
            // IMAGEPROXY → BITMAP
            // -----------------------------------------------------

            Log.d(
                "BilleteDetector",
                "INICIANDO CONVERSION A BITMAP"
            )

            val bitmap =
                imageProxyToBitmap(
                    imageProxy
                )

            Log.d(
                "BilleteDetector",
                "CONVERSION A BITMAP TERMINADA"
            )

            if (bitmap == null) {

                return
            }

            // -----------------------------------------------------
            // ROTAR IMAGEN
            // -----------------------------------------------------

            val rotatedBitmap =
                rotateBitmap(
                    bitmap,
                    imageProxy
                        .imageInfo
                        .rotationDegrees
                )

            // -----------------------------------------------------
            // REDIMENSIONAR A 640 x 640
            // -----------------------------------------------------

            val inputBitmap =
                Bitmap.createScaledBitmap(
                    rotatedBitmap,
                    INPUT_SIZE,
                    INPUT_SIZE,
                    true
                )
            Log.d(
                "BilleteDetector",
                "BITMAP ESCALADO A 640x640"
            )
            // -----------------------------------------------------
            // BITMAP → BUFFER
            // -----------------------------------------------------

            val input =
                bitmapToInputBuffer(
                    inputBitmap
                )
            Log.d(
                "BilleteDetector",
                "BUFFER DE ENTRADA CREADO"
            )

            // -----------------------------------------------------
            // SALIDA DEL MODELO
            //
            // best.tflite:
            //
            // (1, 10, 8400)
            //
            // 0 = centerX
            // 1 = centerY
            // 2 = width
            // 3 = height
            //
            // 4 = clase 0
            // 5 = clase 1
            // 6 = clase 2
            // 7 = clase 3
            // 8 = clase 4
            // 9 = clase 5
            // -----------------------------------------------------

            val output =
                Array(1) {
                    Array(10) {
                        FloatArray(
                            NUM_DETECTIONS
                        )
                    }
                }

            // -----------------------------------------------------
            // EJECUTAR TFLITE
            // -----------------------------------------------------

            interpreter?.run(
                input,
                output
            )

            Log.d(
                "BilleteDetector",
                "MODELO EJECUTADO CORRECTAMENTE"
            )

            // -----------------------------------------------------
            // INTERPRETAR RESULTADO
            // -----------------------------------------------------

            val detection =
                parseOutput(output)

            if (detection != null) {

                if (detection.classId in CLASS_NAMES.indices && detection.confidence >= POSSIBLE_THRESHOLD) {
                    latestResult = DetectionResult(
                        valor = SPEECH_NAMES[detection.classId],
                        confianza = detection.confidence
                    )
                    lastResultTime = System.currentTimeMillis()
                }

                handleDetection(
                    detection
                )
            }

        } catch (e: Exception) {

            e.printStackTrace()

        } finally {

            // -----------------------------------------------------
            // IMPORTANTE:
            // cerrar ImageProxy siempre
            // -----------------------------------------------------

            imageProxy.close()

            isProcessing = false
        }
    }

    // =============================================================
    // ESTRUCTURA DE DETECCIÓN
    // =============================================================

    private data class Detection(

        val classId: Int,

        val confidence: Float,

        val box: RectF
    )

    // =============================================================
    // INTERPRETAR SALIDA DEL MODELO
    // =============================================================

    private fun parseOutput(
        output: Array<Array<FloatArray>>
    ): Detection? {

        var bestDetection: Detection? =
            null

        var bestConfidence = 0f

        // ---------------------------------------------------------
        // Recorrer las 8400 detecciones
        // ---------------------------------------------------------

        for (
        i in 0 until NUM_DETECTIONS
        ) {

            // -----------------------------------------------------
            // COORDENADAS
            // -----------------------------------------------------

            val centerX =
                output[0][0][i]

            val centerY =
                output[0][1][i]

            val width =
                output[0][2][i]

            val height =
                output[0][3][i]

            // -----------------------------------------------------
            // BUSCAR CLASE CON MAYOR CONFIANZA
            // -----------------------------------------------------

            var bestClass = -1

            var classConfidence = 0f

            for (
            classId in 0 until NUM_CLASSES
            ) {

                val confidence =
                    output[0][4 + classId][i]

                if (
                    confidence >
                    classConfidence
                ) {

                    classConfidence =
                        confidence

                    bestClass =
                        classId
                }
            }

            // -----------------------------------------------------
            // DESCARTAR BAJA CONFIANZA
            // -----------------------------------------------------

            if (
                bestClass < 0 ||
                classConfidence <
                POSSIBLE_THRESHOLD
            ) {

                continue
            }

            // -----------------------------------------------------
            // CALCULAR CAJA
            // -----------------------------------------------------

            val left =
                centerX -
                        width / 2f

            val top =
                centerY -
                        height / 2f

            val right =
                centerX +
                        width / 2f

            val bottom =
                centerY +
                        height / 2f

            val box =
                RectF(
                    left,
                    top,
                    right,
                    bottom
                )

            // -----------------------------------------------------
            // CONSERVAR LA DETECCIÓN MÁS CONFIABLE
            // -----------------------------------------------------

            if (
                classConfidence >
                bestConfidence
            ) {

                bestConfidence =
                    classConfidence

                bestDetection =
                    Detection(
                        classId =
                            bestClass,

                        confidence =
                            classConfidence,

                        box =
                            box
                    )
            }
        }

        return bestDetection
    }

    // =============================================================
    // MANEJAR DETECCIÓN
    // =============================================================

    // =============================================================
// MANEJAR DETECCIÓN
// =============================================================

    private fun handleDetection(
        detection: Detection
    ) {

        val classId =
            detection.classId

        val confidence =
            detection.confidence

        Log.d(
            "BilleteDetector",
            "DETECCIÓN: clase=$classId confianza=${confidence * 100}%"
        )

        // ---------------------------------------------------------
        // Verificar clase
        // ---------------------------------------------------------

        if (
            classId !in
            CLASS_NAMES.indices
        ) {

            resetStability()

            return
        }

        // ---------------------------------------------------------
        // Confianza demasiado baja
        // ---------------------------------------------------------

        if (
            confidence <
            STABILITY_MIN_CONFIDENCE
        ) {

            Log.d(
                "BilleteDetector",
                "DESCARTADA: confianza insuficiente"
            )

            resetStability()

            return
        }

        val currentTime =
            System.currentTimeMillis()

        // ---------------------------------------------------------
        // Verificar si pasó demasiado tiempo
        // desde la última detección
        // ---------------------------------------------------------

        if (
            lastStableDetectionTime > 0L &&
            currentTime -
            lastStableDetectionTime >
            STABILITY_TIMEOUT_MS
        ) {

            Log.d(
                "BilleteDetector",
                "REINICIANDO ESTABILIDAD POR TIEMPO"
            )

            resetStability()
        }

        lastStableDetectionTime =
            currentTime

        // ---------------------------------------------------------
        // Si es una clase diferente,
        // empezar nuevamente
        // ---------------------------------------------------------

        if (
            classId !=
            stableClassId
        ) {

            stableClassId =
                classId

            stableDetectionCount =
                1

            stableConfidenceSum =
                confidence

            Log.d(
                "BilleteDetector",
                "NUEVA CLASE: $classId | " +
                        "contador=1/$REQUIRED_STABLE_DETECTIONS"
            )

            return
        }

        // ---------------------------------------------------------
        // Misma clase → aumentar estabilidad
        // ---------------------------------------------------------

        stableDetectionCount++

        stableConfidenceSum +=
            confidence

        Log.d(
            "BilleteDetector",
            "CLASE ESTABLE: $classId | " +
                    "contador=$stableDetectionCount/" +
                    "$REQUIRED_STABLE_DETECTIONS"
        )

        // ---------------------------------------------------------
        // ¿Ya tenemos suficientes detecciones?
        // ---------------------------------------------------------

        if (
            stableDetectionCount >=
            REQUIRED_STABLE_DETECTIONS
        ) {

            val averageConfidence =
                stableConfidenceSum /
                        stableDetectionCount

            Log.d(
                "BilleteDetector",
                "BILLETE CONFIRMADO: " +
                        "clase=$stableClassId " +
                        "confianzaPromedio=" +
                        "${averageConfidence * 100}%"
            )

            val message =
                "Billete de " +
                        SPEECH_NAMES[stableClassId]

            announceIfNeeded(
                classId =
                    stableClassId,

                confidence =
                    averageConfidence,

                message =
                    message,

                confirmed =
                    true
            )

            // -----------------------------------------------------
            // IMPORTANTE:
            // reiniciar para no confirmar el mismo billete
            // inmediatamente otra vez
            // -----------------------------------------------------

            resetStability()
        }
    }
// =============================================================
// REINICIAR ESTABILIZACIÓN
// =============================================================

    private fun resetStability() {

        stableClassId =
            -1

        stableDetectionCount =
            0

        stableConfidenceSum =
            0f

        lastStableDetectionTime =
            0L
    }
    // =============================================================
    // ANUNCIAR RESULTADO
    // =============================================================

    private fun announceIfNeeded(
        classId: Int,
        confidence: Float,
        message: String,
        confirmed: Boolean
    ) {

        val currentTime =
            System.currentTimeMillis()

        // ---------------------------------------------------------
        // EVITAR REPETICIONES
        // ---------------------------------------------------------

        if (
            classId ==
            lastAnnouncedClass &&
            currentTime -
            lastAnnouncedTime <
            ANNOUNCE_COOLDOWN_MS
        ) {

            return
        }

        lastAnnouncedClass =
            classId

        lastAnnouncedTime =
            currentTime

        // ---------------------------------------------------------
        // HABLAR
        // ---------------------------------------------------------

        if (
            ::textToSpeech
                .isInitialized
        ) {

            textToSpeech.speak(
                message,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "billete_$classId"
            )
        }

        // ---------------------------------------------------------
        // GUARDAR EN FIRESTORE
        // ---------------------------------------------------------

        saveRecognition(
            classId = classId,
            confidence = confidence,
            confirmed = confirmed
        )

        if (confirmed) {

            onDetectionConfirmed(
                SPEECH_NAMES[classId],
                confidence
            )
        }
    }

    // =============================================================
// FIRESTORE
// =============================================================

    private fun saveRecognition(
        classId: Int,
        confidence: Float,
        confirmed: Boolean
    ) {

        // ---------------------------------------------------------
        // Verificar clase
        // ---------------------------------------------------------

        if (classId !in CLASS_NAMES.indices) {
            Log.e(
                "BilleteDetector",
                "classId inválido: $classId"
            )
            return
        }

        // ---------------------------------------------------------
        // USUARIO ACTUAL
        // ---------------------------------------------------------

        val user = auth.currentUser

        if (user == null) {
            Log.w(
                "BilleteDetector",
                "No hay usuario autenticado. No se guardará el reconocimiento."
            )
            return
        }

        val uid = user.uid

        // ---------------------------------------------------------
        // DATOS
        // ---------------------------------------------------------

        val data = hashMapOf(

            // Nombre interno de la clase
            "billete" to CLASS_NAMES[classId],

            // Valor numérico real
            // Ejemplo: 5000, 10000, 20000...
            "valor" to CLASS_VALUES[classId],

            // Nombre para mostrar/hablar
            "nombre" to SPEECH_NAMES[classId],

            // ID de clase del modelo
            "classId" to classId,

            // Confianza 0.0 - 1.0
            "confianza" to confidence,

            // Confianza 0 - 100
            "confianzaPorcentaje" to confidence * 100f,

            // Confirmación
            "confirmado" to confirmed,

            // Fecha/hora del servidor
            "timestamp" to FieldValue.serverTimestamp()
        )

        // ---------------------------------------------------------
        // GUARDAR
        //
        // Usuarios/{UID}/Reconocimientos
        // ---------------------------------------------------------

        firestore
            .collection("Usuarios")
            .document(uid)
            .collection("Reconocimientos")
            .add(data)
            .addOnSuccessListener { documentReference ->

                Log.d(
                    "BilleteDetector",
                    "Reconocimiento guardado: ${documentReference.id}"
                )

            }
            .addOnFailureListener { exception ->

                Log.e(
                    "BilleteDetector",
                    "Error guardando reconocimiento",
                    exception
                )
            }
    }

    // =============================================================
    // CARGAR MODELO TFLITE
    // =============================================================

    private fun loadModelFile(
        context: Context,
        modelName: String
    ): MappedByteBuffer {

        val fileDescriptor =
            context.assets.openFd(
                modelName
            )

        val inputStream =
            FileInputStream(
                fileDescriptor.fileDescriptor
            )

        val fileChannel =
            inputStream.channel

        val startOffset =
            fileDescriptor.startOffset

        val declaredLength =
            fileDescriptor.declaredLength

        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            startOffset,
            declaredLength
        )
    }

    // =============================================================
    // BITMAP → BUFFER TFLITE
    // =============================================================

    private fun bitmapToInputBuffer(
        bitmap: Bitmap
    ): ByteBuffer {

        val inputBuffer =
            ByteBuffer.allocateDirect(
                4 *
                        INPUT_SIZE *
                        INPUT_SIZE *
                        3
            )

        inputBuffer.order(
            ByteOrder.nativeOrder()
        )

        val pixels =
            IntArray(
                INPUT_SIZE *
                        INPUT_SIZE
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

        // =========================================================
        // MODELO YOLO: [1, 3, 640, 640]
        //
        // Primero todos los R
        // Después todos los G
        // Después todos los B
        // =========================================================

        // CANAL ROJO
        for (pixel in pixels) {

            val r =
                (pixel shr 16) and 0xFF

            inputBuffer.putFloat(
                r / 255.0f
            )
        }

        // CANAL VERDE
        for (pixel in pixels) {

            val g =
                (pixel shr 8) and 0xFF

            inputBuffer.putFloat(
                g / 255.0f
            )
        }

        // CANAL AZUL
        for (pixel in pixels) {

            val b =
                pixel and 0xFF

            inputBuffer.putFloat(
                b / 255.0f
            )
        }

        inputBuffer.rewind()

        return inputBuffer
    }

    // =============================================================
    // IMAGEPROXY → BITMAP
    // =============================================================

    /*
     * ESTA FUNCIÓN ES LA QUE USA imageProxy.image.
     *
     * Por eso la anotamos directamente con:
     *
     * @OptIn(ExperimentalGetImage::class)
     *
     * No usamos @file:OptIn.
     */

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    private fun imageProxyToBitmap(
        imageProxy: ImageProxy
    ): Bitmap? {

        // ---------------------------------------------------------
        // OBTENER IMAGEN
        // ---------------------------------------------------------

        val image =
            imageProxy.image
                ?: return null

        // ---------------------------------------------------------
        // PLANOS YUV
        // ---------------------------------------------------------

        val yBuffer =
            image.planes[0].buffer

        val uBuffer =
            image.planes[1].buffer

        val vBuffer =
            image.planes[2].buffer

        val ySize =
            yBuffer.remaining()

        val uSize =
            uBuffer.remaining()

        val vSize =
            vBuffer.remaining()

        // ---------------------------------------------------------
        // CREAR BUFFER NV21
        // ---------------------------------------------------------

        val nv21 =
            ByteArray(
                ySize +
                        uSize +
                        vSize
            )

        yBuffer.get(
            nv21,
            0,
            ySize
        )

        vBuffer.get(
            nv21,
            ySize,
            vSize
        )

        uBuffer.get(
            nv21,
            ySize + vSize,
            uSize
        )

        // ---------------------------------------------------------
        // YUV → JPEG
        // ---------------------------------------------------------

        val yuvImage =
            android.graphics.YuvImage(
                nv21,
                android.graphics.ImageFormat.NV21,
                image.width,
                image.height,
                null
            )

        val outputStream =
            ByteArrayOutputStream()

        yuvImage.compressToJpeg(
            android.graphics.Rect(
                0,
                0,
                image.width,
                image.height
            ),
            90,
            outputStream
        )

        // ---------------------------------------------------------
        // JPEG → BYTE ARRAY
        // ---------------------------------------------------------

        val imageBytes =
            outputStream.toByteArray()

        // ---------------------------------------------------------
        // BYTE ARRAY → BITMAP
        // ---------------------------------------------------------

        return BitmapFactory.decodeByteArray(
            imageBytes,
            0,
            imageBytes.size
        )
    }

    // =============================================================
    // ROTAR BITMAP
    // =============================================================

    private fun rotateBitmap(
        bitmap: Bitmap,
        rotationDegrees: Int
    ): Bitmap {

        // ---------------------------------------------------------
        // Si no necesita rotación
        // ---------------------------------------------------------

        if (
            rotationDegrees == 0
        ) {

            return bitmap
        }

        // ---------------------------------------------------------
        // MATRIZ
        // ---------------------------------------------------------

        val matrix =
            Matrix()

        matrix.postRotate(
            rotationDegrees.toFloat()
        )

        // ---------------------------------------------------------
        // CREAR BITMAP ROTADO
        // ---------------------------------------------------------

        return Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true
        )
    }

    // =============================================================
    // CERRAR DETECTOR
    // =============================================================

    fun close() {

        try {

            // -----------------------------------------------------
            // CERRAR TFLITE
            // -----------------------------------------------------

            interpreter?.close()

            interpreter = null

            // -----------------------------------------------------
            // CERRAR TEXT TO SPEECH
            // -----------------------------------------------------

            if (
                ::textToSpeech
                    .isInitialized
            ) {

                textToSpeech.stop()

                textToSpeech.shutdown()
            }

        } catch (e: Exception) {

            Log.e(
                "BilleteDetector",
                "ERROR DURANTE EL PROCESAMIENTO",
                e
            )
        }
    }
}