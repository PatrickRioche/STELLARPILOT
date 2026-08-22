package fr.stellarpilot.app.ui.format

fun statusDisplay(
    status: String?
): String =
    when (status?.lowercase()) {

        "ready" ->
            "PR\u00CAT"

        "online" ->
            "EN LIGNE"

        "offline" ->
            "HORS LIGNE"

        "fix" ->
            "FIX\u00C9"

        "no_fix" ->
            "NON FIX\u00C9"

        "unavailable" ->
            "INDISPONIBLE"

        "connected" ->
            "CONNECT\u00C9"

        "disconnected" ->
            "D\u00C9CONNECT\u00C9"

        "connecting" ->
            "CONNEXION..."

        "error" ->
            "ERREUR"

        "unknown" ->
            "INCONNU"

        "busy" ->
            "OCCUP\u00C9"

        "idle" ->
            "AU REPOS"

        "ok" ->
            "OK"

        null, "" ->
            "INCONNU"

        else ->
            status.uppercase()
    }