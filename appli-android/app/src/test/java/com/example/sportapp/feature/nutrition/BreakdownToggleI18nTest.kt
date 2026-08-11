package com.example.sportapp.feature.nutrition

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.example.sportapp.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * 2026-06-18 (qa-sport) : couvre le COMPORTEMENT OBSERVABLE de la tâche
 * "Nutrition Android — Objectifs : toggle 4 modes sur la répartition des calories
 * (+ radar)" (commit a00baf2).
 *
 * Le cadre « Répartition des calories » de [NutritionGoalsScreen] regroupe ses 3
 * visuels (donut / radar / g·kg) sous un seul `SegmentedIconToggle`
 * (donut par défaut). Le défaut DONUT et le basculement sont un état Compose privé
 * (`BreakdownView`, mutableStateOf privé) → testable seulement en instrumenté/
 * Compose-UI, hors périmètre JVM, et trivialement vrai au compile. La logique
 * derrière chaque mode (parts kcal du donut/%, g·kg, densité fibres) est déjà
 * verrouillée dans GoalAnalysisTest — on ne la re-teste pas (politique simplicité).
 *
 * Ce qui EST un comportement observable et JVM-testable ici : les 3 libellés des
 * boutons du toggle, qui servent aussi de `description` a11y des segments
 * (`SegmentItem.description`). Ils doivent rester corrects ET, pour ceux qui se
 * traduisent, distincts EN/FR (politique 18). NutritionLabelsI18nTest ne vérifie
 * que la PRÉSENCE des clés ; ici on verrouille leurs VALEURS (une FR cassée à
 * "Donut" passerait la parité mais pas ce test).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class BreakdownToggleI18nTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun frContext(): Context {
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale.FRENCH)
        return context.createConfigurationContext(config)
    }

    @Test
    fun `les 3 libelles du toggle repartition s'affichent en anglais`() {
        assertEquals("Donut", context.getString(R.string.nutrition_goals_breakdown_view_donut))
        assertEquals("Radar", context.getString(R.string.nutrition_goals_breakdown_view_radar))
        assertEquals(
            "Grams per kg of body weight",
            context.getString(R.string.nutrition_goals_breakdown_view_per_kg),
        )
    }

    @Test
    fun `les 3 libelles du toggle repartition s'affichent en francais`() {
        val fr = frContext()
        assertEquals("Anneau", fr.getString(R.string.nutrition_goals_breakdown_view_donut))
        // "Radar" est un terme propre non traduit -> identique EN/FR (attendu).
        assertEquals("Radar", fr.getString(R.string.nutrition_goals_breakdown_view_radar))
        assertEquals(
            "Grammes par kg de poids de corps",
            fr.getString(R.string.nutrition_goals_breakdown_view_per_kg),
        )
    }

    @Test
    fun `les libelles traduisibles different bien entre EN et FR (la ressource FR existe)`() {
        val fr = frContext()
        // FR ≠ EN sur les libellés traduisibles -> prouve que la FR n'est pas un
        // simple repli sur l'EN par défaut (Donut/Grams...).
        assertNotEquals(
            context.getString(R.string.nutrition_goals_breakdown_view_donut),
            fr.getString(R.string.nutrition_goals_breakdown_view_donut),
        )
        assertNotEquals(
            context.getString(R.string.nutrition_goals_breakdown_view_per_kg),
            fr.getString(R.string.nutrition_goals_breakdown_view_per_kg),
        )
    }
}
