package com.example.sportapp.core.data.repository

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

object StorageManager {
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun initUserMuscleStorage() {
        val dir = File(appContext.filesDir, "images/muscles")
        if (!dir.exists()) dir.mkdirs()
    }

    fun getUserMuscleDir(): File {
        val dir = File(appContext.filesDir, "images/muscles")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun loadMuscleImage(muscleName: String): ImageBitmap? {
        val formattedName = muscleName
            .trim()
            .lowercase()
            .replace(" ", "_")

        // 1️⃣ Vérifier si image personnalisée existe dans filesDir
        val customImageFile = File(getUserMuscleDir(), "$formattedName.png")
        if (customImageFile.exists()) {
            val bitmap = BitmapFactory.decodeFile(customImageFile.absolutePath)
            if (bitmap == null) {
                Log.w("StorageManager", "Corrupt custom image: ${customImageFile.absolutePath}")
            }
            return bitmap?.asImageBitmap()
        }

        // 2️⃣ Vérifier dans les assets
        try {
            appContext.assets.open("images/muscles/$formattedName.png").use { input ->
                val bitmap = BitmapFactory.decodeStream(input)
                if (bitmap == null) {
                    Log.w("StorageManager", "Corrupt asset image: images/muscles/$formattedName.png")
                }
                return bitmap?.asImageBitmap()
            }
        } catch (_: Exception) {}

        // ❌ Si rien trouvé → retourner null
        return null
    }

    fun loadDefaultMuscleImage(): ImageBitmap {
        appContext.assets.open("images/muscles/default_muscle.png").use { input ->
            return BitmapFactory.decodeStream(input).asImageBitmap()
        }
    }

}

