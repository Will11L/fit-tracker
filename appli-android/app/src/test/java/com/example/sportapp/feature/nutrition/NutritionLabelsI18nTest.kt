package com.example.sportapp.feature.nutrition

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.example.sportapp.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Locale

/**
 * 2026-06-18 (qa-sport) : couvre le COMPORTEMENT OBSERVABLE de la tâche
 * "Nutrition Android — retouches revue fonctionnelle" (commit 854f50d).
 *
 * La tâche est essentiellement cosmétique : regroupement de la ligne nom+heure
 * dans [MealSectionCard], restyle du [NutritionCalendarSection] (tailles/couleurs/
 * formes). Ces changements de rendu Compose relèvent du test instrumenté/screenshot
 * et asserter "16sp / textTertiary / shapes.small" testerait l'implémentation, pas
 * un comportement vu par l'utilisateur — donc hors périmètre JVM.
 *
 * Ce qui EST un vrai comportement observable et testable en JVM : les 3 libellés
 * raccourcis du journal nutrition, qui doivent rester corrects ET traduits dans
 * les 2 locales (politique #18). Les valeurs FR ≠ EN prouvent que la ressource FR
 * existe réellement (sinon Android retombe sur l'EN par défaut sans erreur).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class NutritionLabelsI18nTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun frContext(): Context {
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale.FRENCH)
        return context.createConfigurationContext(config)
    }

    @Test
    fun `les libelles repas raccourcis s'affichent en anglais`() {
        assertEquals("Add meal", context.getString(R.string.nutrition_add_meal))
        assertEquals("Duplicate meal", context.getString(R.string.nutrition_duplicate_meal))
        assertEquals("No food", context.getString(R.string.nutrition_meal_empty))
    }

    @Test
    fun `les libelles repas raccourcis s'affichent en francais`() {
        val fr = frContext()
        // FR ≠ EN sur les 3 -> prouve que la traduction FR est bien présente.
        assertEquals("Ajouter repas", fr.getString(R.string.nutrition_add_meal))
        assertEquals("Dupliquer repas", fr.getString(R.string.nutrition_duplicate_meal))
        assertEquals("Aucun aliment", fr.getString(R.string.nutrition_meal_empty))
    }

    @Test
    fun `parite EN-FR de toutes les cles nutrition (politique 18)`() {
        val keyRegex = Regex("""name="(nutrition_[a-z0-9_]+)"""")
        fun keysOf(file: File): Set<String> =
            keyRegex.findAll(file.readText(Charsets.UTF_8))
                .map { it.groupValues[1] }
                .toSet()

        val enKeys = keysOf(resFile("values/strings.xml"))
        val frKeys = keysOf(resFile("values-fr/strings.xml"))

        assertTrue("aucune clé nutrition_* trouvée — résolution de fichier cassée ?", enKeys.isNotEmpty())
        assertEquals(
            "parité i18n nutrition rompue (manque en EN ou FR)",
            emptySet<String>(),
            (enKeys - frKeys) + (frKeys - enKeys),
        )
        // Garde explicitement les 3 libellés raccourcis par la tâche.
        assertTrue(
            enKeys.containsAll(
                listOf("nutrition_add_meal", "nutrition_duplicate_meal", "nutrition_meal_empty"),
            ),
        )
    }

    /**
     * Localise un fichier de ressources du module :app indépendamment du
     * répertoire de travail (Gradle = `appli-android/app`, IDE = variable).
     */
    private fun resFile(relative: String): File {
        File("src/main/res/$relative").let { if (it.exists()) return it }
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            File(dir, "app/src/main/res/$relative").let { if (it.exists()) return it }
            File(dir, "src/main/res/$relative").let { if (it.exists()) return it }
            dir = dir.parentFile
        }
        throw IllegalStateException(
            "Ressource introuvable: $relative (user.dir=${System.getProperty("user.dir")})",
        )
    }
}
