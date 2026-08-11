package com.example.sportapp.feature.nutrition.ui

import android.content.Context
import android.util.Log
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * Lance le scanner de code-barres Google Play services (UI de scan fournie par
 * Google, module téléchargé à la demande — aucune permission caméra à gérer
 * soi-même). Formats restreints aux codes produits (EAN-13/8, UPC-A/E), suffisants
 * pour un lookup Open Food Facts.
 *
 * [onResult] reçoit le code brut scanné ; [onError] couvre un échec du scanner
 * (module Play services indisponible, etc.). L'annulation par l'utilisateur est
 * silencieuse (simple retour à l'écran précédent).
 */
fun launchBarcodeScan(
    context: Context,
    onResult: (String) -> Unit,
    onError: () -> Unit,
) {
    val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(
            Barcode.FORMAT_EAN_13,
            Barcode.FORMAT_EAN_8,
            Barcode.FORMAT_UPC_A,
            Barcode.FORMAT_UPC_E,
        )
        .build()
    GmsBarcodeScanning.getClient(context, options).startScan()
        .addOnSuccessListener { barcode ->
            val raw = barcode.rawValue
            if (raw.isNullOrBlank()) onError() else onResult(raw)
        }
        .addOnCanceledListener { /* annulé par l'utilisateur : no-op */ }
        .addOnFailureListener { e ->
            Log.w("BarcodeScan", "scan failed", e)
            onError()
        }
}
