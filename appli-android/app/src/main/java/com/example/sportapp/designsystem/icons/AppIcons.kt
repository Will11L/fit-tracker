package com.example.sportapp.designsystem.icons

import com.example.sportapp.R

/**
 * Registre central nomme : "tel concept = tel R.drawable".
 *
 * Permet de changer l'icone d'un concept une seule fois pour TOUTE l'app
 * (drawer, bottom nav, screens, dialogs...). Sinon on a R.drawable.ic_xxx
 * eparpille partout et changer une icone = chercher tous les callsites.
 *
 * Convention : 1 const par "concept metier" (UNREAD_NOTIFS, SYNC_PENDING...).
 * Les variantes ON/OFF (icones binaires) sont 2 const distincts.
 *
 * Usage typique :
 *   Image(painter = painterResource(id = AppIcons.SYNC_PENDING), ...)
 *
 * Quand on ajoute un nouvel usage d'icone : verifier si le concept existe
 * deja ici. Si oui, reutiliser. Si non, ajouter une const + l'utiliser.
 */
object AppIcons {

    // === Notifications (envelope/mail) ===
    val NOTIFICATIONS_UNREAD: Int = R.drawable.ic_rounded_mail

    // === Sync state (cloud) ===
    /** Donnees locales en attente de push serveur (pending > 0). */
    val SYNC_PENDING: Int = R.drawable.ic_cloud_off
    /** Tout est synchronise (pending == 0). */
    val SYNC_DONE: Int = R.drawable.ic_cloud_done

    // === Network state (signal HTTP/wifi) ===
    val NETWORK_OFF: Int = R.drawable.ic_rounded_signal_cellular_off
    val NETWORK_ON: Int = R.drawable.ic_baseline_signal_cellular_alt

    // === WebSocket realtime (router) ===
    val WS_ON: Int = R.drawable.ic_rounded_router
    val WS_OFF: Int = R.drawable.ic_rounded_router_off
}
