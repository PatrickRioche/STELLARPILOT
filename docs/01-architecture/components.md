# Composants envisagés

- `core` : logique indépendante de la plateforme.
- `indi-adapter` : accès abstrait aux périphériques INDI.
- `mount-service` : mouvement, coordonnées et synchronisation.
- `camera-service` : acquisition et métadonnées.
- `focus-service` : autofocus.
- `solve-service` : astrométrie.
- `orchestrator` : enchaînement des opérations.
- `api-server` : protocole avec Android / Web.
- `android-client` : expérience utilisateur mobile.

Tous les noms et frontières sont provisoires jusqu'au Gate G0.
