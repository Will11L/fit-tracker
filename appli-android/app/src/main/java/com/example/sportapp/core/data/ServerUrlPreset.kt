package com.example.sportapp.core.data

/**
 * Preset de l'URL serveur (admin users uniquement, cf. ServerUrlRepository).
 *
 * Convention politique 11 : valeurs en UPPER_CASE cross-stack.
 * - PC_LAN  : serveur FastAPI sur le PC Windows (dev local LAN).
 * - PI_PROD : Raspberry Pi via Tailscale MagicDNS (prod HTTPS).
 * - CUSTOM  : URL libre saisie par le user (champ texte).
 */
enum class ServerUrlPreset {
    PC_LAN,
    PI_PROD,
    CUSTOM,
}
