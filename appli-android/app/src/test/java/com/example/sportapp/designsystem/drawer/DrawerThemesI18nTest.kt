package com.example.sportapp.designsystem.drawer

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.example.sportapp.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Locale

/**
 * 2026-06-17 (qa-sport) : couvre le COMPORTEMENT OBSERVABLE de la tâche
 * "Android — Réorganisation du drawer par thèmes" (commit 5ee0ddf).
 *
 * Le drawer ([DrawerContent]) est un Composable dépendant de Hilt : son rendu
 * réel relève du test instrumenté. Ce qui EST testable en JVM/Robolectric et
 * qui constitue le comportement vu par l'utilisateur :
 *
 *  1. les 4 libellés de section thématique (Général / Sport / Nutrition /
 *     Compte & Réglages) s'affichent correctement en EN ET en FR,
 *  2. les 2 entrées NOUVELLES de la tâche (Accueil, Chrono) ont un libellé
 *     dans les 2 locales,
 *  3. la section obsolète `drawer_section_activity` a bien été retirée,
 *  4. parité i18n EN/FR (politique #18) sur TOUTES les clés `drawer_*` —
 *     garde-fou contre l'ajout d'une entrée drawer sans sa traduction FR.
 *
 * Les valeurs FR≠EN (Général, Accueil, Compte & Paramètres) prouvent que la
 * ressource FR existe réellement (sinon Android retombe sur l'EN par défaut).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class DrawerThemesI18nTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun frContext(): Context {
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale.FRENCH)
        return context.createConfigurationContext(config)
    }

    @Test
    fun `les 4 libelles de section s'affichent en anglais`() {
        assertEquals("General", context.getString(R.string.drawer_section_general))
        assertEquals("Sport", context.getString(R.string.drawer_section_sport))
        assertEquals("Nutrition", context.getString(R.string.drawer_section_nutrition))
        assertEquals(
            "Account & Settings",
            context.getString(R.string.drawer_section_account_settings),
        )
    }

    @Test
    fun `les 4 libelles de section s'affichent en francais`() {
        val fr = frContext()
        assertEquals("Général", fr.getString(R.string.drawer_section_general))
        assertEquals("Sport", fr.getString(R.string.drawer_section_sport))
        assertEquals("Nutrition", fr.getString(R.string.drawer_section_nutrition))
        assertEquals(
            "Compte & Paramètres",
            fr.getString(R.string.drawer_section_account_settings),
        )
    }

    @Test
    fun `les nouvelles entrees Accueil et Chrono sont traduites EN et FR`() {
        assertEquals("Home", context.getString(R.string.drawer_item_home))
        assertEquals("Chrono", context.getString(R.string.drawer_item_chrono))

        val fr = frContext()
        assertEquals("Accueil", fr.getString(R.string.drawer_item_home))
        assertEquals("Chrono", fr.getString(R.string.drawer_item_chrono))
    }

    @Test
    fun `la section obsolete drawer_section_activity a ete retiree`() {
        // Lookup par nom : la clé supprimée par la tâche ne doit plus exister
        // dans aucune locale (id == 0). Référencer R.string.drawer_section_activity
        // ne compilerait pas — on passe donc par getIdentifier.
        val id = context.resources.getIdentifier(
            "drawer_section_activity", "string", context.packageName,
        )
        assertEquals(
            "drawer_section_activity aurait dû être supprimé (devenu inutilisé)",
            0, id,
        )
    }

    @Test
    fun `parite EN-FR de toutes les cles drawer (politique 18)`() {
        val keyRegex = Regex("""name="(drawer_[a-z_]+)"""")
        fun keysOf(file: File): Set<String> =
            keyRegex.findAll(file.readText(Charsets.UTF_8))
                .map { it.groupValues[1] }
                .toSet()

        val enKeys = keysOf(resFile("values/strings.xml"))
        val frKeys = keysOf(resFile("values-fr/strings.xml"))

        assertTrue("aucune clé drawer_* trouvée — résolution de fichier cassée ?", enKeys.isNotEmpty())
        assertEquals(
            "parité i18n drawer rompue (manque en EN ou FR)",
            emptySet<String>(),
            enKeys.symmetricDifference(frKeys),
        )
        // Garde explicitement les libellés ajoutés/conservés par la tâche.
        assertTrue(enKeys.containsAll(listOf("drawer_section_general", "drawer_section_sport", "drawer_item_home", "drawer_item_chrono")))
        assertFalse("la clé obsolète ne doit plus figurer dans le XML", enKeys.contains("drawer_section_activity"))
    }

    private fun Set<String>.symmetricDifference(other: Set<String>): Set<String> =
        (this - other) + (other - this)

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
