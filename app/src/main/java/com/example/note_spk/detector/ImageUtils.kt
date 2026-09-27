
package com.example.note_spk.detector

import android.graphics.Bitmap
import androidx.core.graphics.scale

object ImageUtils {

    const val INPUT_SIZE = 640

    fun resize(bitmap: Bitmap): Bitmap {
        return bitmap.scale(INPUT_SIZE, INPUT_SIZE)
    }
}