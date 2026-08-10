# Interfaces à spécifier

## Matériel
- INDI server ↔ pilotes de périphériques.
- StellarPilot ↔ INDI server.

## Interne
- Orchestrateur ↔ monture.
- Orchestrateur ↔ caméra.
- Orchestrateur ↔ focuser.
- Orchestrateur ↔ plate solver.

## Externe
- Android ↔ serveur ARM64.
- Future interface Web ↔ serveur ARM64.

Pour chaque interface : protocole, erreurs, timeouts, reprise, sécurité,
versionnement et observabilité devront être précisés.
