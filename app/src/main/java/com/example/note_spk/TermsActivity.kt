package com.example.note_spk

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton

/**
 * Muestra los Términos de Servicio o la Política de Privacidad en una pantalla
 * con scroll. El documento se elige con el extra DOC_TYPE.
 */
class TermsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terms)

        val rootTerms = findViewById<View>(R.id.rootTerms)
        val btnBack = findViewById<MaterialButton>(R.id.btnBack)
        val tvDocTitle = findViewById<TextView>(R.id.tvDocTitle)
        val tvDocUpdated = findViewById<TextView>(R.id.tvDocUpdated)
        val tvDocContent = findViewById<TextView>(R.id.tvDocContent)

        // Respeta barras de estado y navegación (edge-to-edge en Android 15+)
        ViewCompat.setOnApplyWindowInsetsListener(rootTerms) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        val isPrivacy = intent.getStringExtra(EXTRA_DOC_TYPE) == TYPE_PRIVACY
        val titleRes = if (isPrivacy) R.string.privacy_policy else R.string.terms_of_service
        val contentRes = if (isPrivacy) R.string.privacy_content else R.string.terms_content

        title = getString(titleRes)
        tvDocTitle.setText(titleRes)
        tvDocUpdated.setText(R.string.legal_last_update)
        tvDocContent.text = HtmlCompat.fromHtml(
            getString(contentRes),
            HtmlCompat.FROM_HTML_MODE_LEGACY
        )

        // TalkBack: permite saltar por encabezados
        ViewCompat.setAccessibilityHeading(tvDocTitle, true)

        btnBack.setOnClickListener { finish() }
    }

    companion object {
        const val EXTRA_DOC_TYPE = "DOC_TYPE"
        const val TYPE_TERMS = "TERMS"
        const val TYPE_PRIVACY = "PRIVACY"

        fun newIntent(context: Context, docType: String): Intent =
            Intent(context, TermsActivity::class.java).putExtra(EXTRA_DOC_TYPE, docType)
    }
}