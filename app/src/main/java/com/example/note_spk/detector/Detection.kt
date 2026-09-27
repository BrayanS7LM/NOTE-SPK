package com.example.note_spk.detector

import android.graphics.RectF

data class Detection(
    val label: String,
    val confidence: Float,
    val box: RectF
)