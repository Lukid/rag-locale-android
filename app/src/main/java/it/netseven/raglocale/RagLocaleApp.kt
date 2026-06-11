package it.netseven.raglocale

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp

/** Application class: punto di innesto di Hilt (DI). */
@HiltAndroidApp
class RagLocaleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // PdfBox-Android carica i font dal proprio AAR solo dopo questa init (vedi PdfSource).
        PDFBoxResourceLoader.init(applicationContext)
    }
}
