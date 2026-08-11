package com.example.sportapp.feature.nutrition

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.example.sportapp.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Locale

/**
 * 2026-06-18 (qa-sport) : couvre le COMPORTEMENT OBSERVABLE de la tâche
 * "Nutrition Android — Objectifs : toggle Radar/Barres sur le profil macros
 * (radar par défaut)" (commit 0c0970a).
 *
 * La section « Profil macros » de [NutritionGoalsScreen] fusionne le radar et les
 * barres comparatives sous un seul `SegmentedIconToggle` Radar/Barres (radar par
 * défaut). Le défaut RADAR et le basculement sont un état Compose privé
 * (`MacroProfileView`, mutableStateOf privé) → testable seulement en instrumenté/
 * Compose-UI, hors périmètre JVM, et trivialement vrai au compile.
 *
 * Ce qui EST un comportement observable et JVM-testable : les deux libellés du
 * toggle servent de `description` a11y (content description) des segments
 * (`SegmentItem.description`) → ils doivent rester corrects ET distincts dans les
 * 2 locales (politique 18). Le test global de parité (NutritionLabelsI18nTest) ne
 * vérifie que la PRÉSENCE des clés ; ici on verrouille leurs VALEURS (une FR
 * cassée à "Bars" passerait la parité mais pas ce test) + le titre de section qui
 * a changé de sens (radar-only -> profil macros) + le nettoyage de l'orphelin
 * `nutrition_goals_section_week` retiré des 2 fichiers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class MacroProfileToggleI18nTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun frContext(): Context {
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale.FRENCH)
        return context.createConfigurationContext(config)
    }

    @Test
    fun `libelles a11y du toggle Radar-Barres et titre de section en anglais`() {
        assertEquals("Radar", context.getString(R.string.nutrition_summary_view_radar))
        assertEquals("Bars", context.getString(R.string.nutrition_summary_view_bars))
        // Le titre de section porte désormais le profil macros (et non "radar" seul).
        assertEquals(
            "Macro profile",
            context.getString(R.string.nutrition_goals_section_radar),
        )
    }

    @Test
    fun `libelles a11y du toggle Radar-Barres et titre de section en francais`() {
        val fr = frContext()
        assertEquals("Radar", fr.getString(R.string.nutrition_summary_view_radar))
        // "Bars" -> "Barres" : FR ≠ EN prouve que la ressource FR existe réellement.
        assertEquals("Barres", fr.getString(R.string.nutrition_summary_view_bars))
        assertNotEquals(
            context.getString(R.string.nutrition_summary_view_bars),
            fr.getString(R.string.nutrition_summary_view_bars),
        )
        assertEquals(
            "Profil macro",
            fr.getString(R.string.nutrition_goals_section_radar),
        )
    }

    @Test
    fun `l'orphelin nutrition_goals_section_week est retire des deux locales`() {
        val orphan = "name=\"nutrition_goals_section_week\""
        assertFalse(
            "nutrition_goals_section_week subsiste en EN (devait être supprimé par la tâche)",
            resFile("values/strings.xml").readText(Charsets.UTF_8).contains(orphan),
        )
        assertFalse(
            "nutrition_goals_section_week subsiste en FR (devait être supprimé par la tâche)",
            resFile("values-fr/strings.xml").readText(Charsets.UTF_8).contains(orphan),
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
