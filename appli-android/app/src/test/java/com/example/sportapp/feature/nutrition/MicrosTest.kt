package com.example.sportapp.feature.nutrition

import com.example.sportapp.core.data.model.MealEntry
import com.example.sportapp.feature.nutrition.domain.MicroKey
import com.example.sportapp.feature.nutrition.domain.microRows
import com.example.sportapp.feature.nutrition.domain.sumMicroTotals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests JVM purs du substrat micros qui alimente les 3 visuels du résumé du jour
 * Nutrition (barres / anneaux / radar, [DaySummaryBanner]).
 *
 * Les transformations de tracé (donut slices, radar axes) sont des @Composable
 * privés ; on verrouille ici la donnée pure dont elles dépendent :
 * - barres : `progress` (borné 0..1) + flag `exceeded`,
 * - anneaux & radar : `% VNR` non borné = `value / key.target * 100`.
 * Complète le test sodium existant de [JournalDomainTest].
 */
class MicrosTest {

    private fun entry(
        qty: Float,
        iron: Float? = null,
        vitC: Float? = null,
        calcium: Float? = null,
    ) = MealEntry(
        uuid = "e-$qty-$iron-$vitC-$calcium",
        mealUUID = "m",
        displayName = "E",
        quantityG = qty,
        kcalPer100g = 0f,
        proteinPer100g = 0f,
        carbsPer100g = 0f,
        fatPer100g = 0f,
        ironPer100g = iron,
        vitaminCPer100g = vitC,
        calciumPer100g = calcium,
    )

    @Test
    fun `sumMicroTotals scales per-100g by quantity and sums entries`() {
        // 250 g d'un aliment à 8 mg/100g de fer + 50 g à 8 mg/100g
        //   = 8*2.5 + 8*0.5 = 20 + 4 = 24 mg. Le facteur qty/100 doit s'appliquer.
        val totals = sumMicroTotals(
            listOf(
                entry(qty = 250f, iron = 8f, vitC = 40f),
                entry(qty = 50f, iron = 8f),
            )
        )
        assertEquals(24f, totals[MicroKey.IRON]!!, 0.001f)
        // Vitamine C uniquement sur la 1re entry : 40 * 2.5 = 100 mg.
        assertEquals(100f, totals[MicroKey.VITAMIN_C]!!, 0.001f)
        // Micro absent des deux entries -> 0 (pas null), les 10 clés sont présentes.
        assertEquals(10, totals.size)
        assertEquals(0f, totals[MicroKey.CALCIUM]!!, 0.001f)
    }

    @Test
    fun `microRows caps bar progress at 1 but keeps the raw value for the rings and radar percent`() {
        // VNR Vitamine C = 80 mg. Apport 160 mg -> 200 % VNR.
        val totals = sumMicroTotals(listOf(entry(qty = 100f, vitC = 160f)))
        val vitC = microRows(totals).first { it.key == MicroKey.VITAMIN_C }

        // Barre : bornée à 1 (pleine).
        assertEquals(1f, vitC.progress, 0.001f)
        // Valeur brute conservée -> anneaux/radar affichent value/target*100 = 200 %.
        assertEquals(160f, vitC.value, 0.001f)
        assertEquals(200f, vitC.value / vitC.key.target * 100f, 0.001f)
        // Dépassement d'un objectif (non plafond) n'est PAS une alerte (seul Sodium l'est).
        assertFalse(vitC.exceeded)
    }

    @Test
    fun `microRows derives partial coverage as value over VNR for every micro key`() {
        // Fer 7 mg sur VNR 14 -> 0.5 ; les 10 lignes sont émises dans l'ordre canonique.
        val rows = microRows(sumMicroTotals(listOf(entry(qty = 100f, iron = 7f))))
        assertEquals(MicroKey.entries, rows.map { it.key })
        val iron = rows.first { it.key == MicroKey.IRON }
        assertEquals(0.5f, iron.progress, 0.001f)
        assertFalse(iron.exceeded)
        // Un micro sans apport -> progress 0 (pas de barre, anneau/axe à 0 %).
        assertTrue(rows.first { it.key == MicroKey.ZINC }.progress == 0f)
    }
}
