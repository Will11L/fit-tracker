package com.example.sportapp.feature.equipment.domain

import com.example.sportapp.core.data.local.AvailableEquipmentDao
import com.example.sportapp.core.data.model.AvailableEquipment
import java.util.UUID

/**
 * Logique de possession « mon matériel » partagée entre la liste et le détail
 * (évite un doublon strict entre les deux ViewModels, politique 9).
 *
 * La possession est modélisée côté wire comme dans le client web : posséder un
 * équipement = avoir une ligne [AvailableEquipment] (user-scoped) homonyme du
 * catalogue global, comparaison insensible à la casse. Le toggle crée / retire
 * cette ligne perso ; le catalogue global (`Equipment`, Type C admin) n'est
 * jamais écrit ici (politique 8 — aucune écriture admin nécessaire).
 *
 * Ces fonctions ne font QUE la mutation Room locale (synced=false). Le push de
 * `AvailableEquipment` est déclenché par l'appelant (ViewModel) après coup.
 */
object EquipmentOwnership {

    /** Bascule la possession de [name] pour [userId]. */
    suspend fun toggle(dao: AvailableEquipmentDao, name: String, userId: Int) {
        val all = dao.getAllOnce()
        val active = all.firstOrNull { it.name.equals(name, ignoreCase = true) && !it.pendingDeletion }
        if (active != null) {
            // Possédé → retirer : marque la ligne perso pour suppression + à pousser.
            dao.markAsPendingDeletion(active.uuid)
            dao.markAsUnsynced(active.uuid)
        } else {
            createOrReactivate(dao, all, name, userId)
        }
    }

    /**
     * Garantit que [name] est possédé par [userId] (no-op si déjà le cas).
     * Utilisé par l'ajout de matériel perso (bouton +).
     */
    suspend fun addPersonal(dao: AvailableEquipmentDao, name: String, userId: Int) {
        val all = dao.getAllOnce()
        val alreadyOwned = all.any { it.name.equals(name, ignoreCase = true) && !it.pendingDeletion }
        if (alreadyOwned) return
        createOrReactivate(dao, all, name, userId)
    }

    /**
     * Crée la ligne perso, ou réactive une ligne homonyme en attente de
     * suppression (évite les doublons côté serveur sur un toggle off→on).
     */
    private suspend fun createOrReactivate(
        dao: AvailableEquipmentDao,
        all: List<AvailableEquipment>,
        name: String,
        userId: Int,
    ) {
        val reusable = all.firstOrNull { it.name.equals(name, ignoreCase = true) && it.pendingDeletion }
        if (reusable != null) {
            dao.updateAvailableEquipment(reusable.copy(pendingDeletion = false))
        } else {
            dao.insert(
                AvailableEquipment(
                    uuid = UUID.randomUUID().toString(),
                    userId = userId,
                    name = name.trim(),
                )
            )
        }
    }
}
