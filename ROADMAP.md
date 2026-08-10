# Roadmap StellarPilot

## Phase 0 — Spécification et faisabilité

1. S0.1 — Cadre projet, périmètre, conventions et critères de validation.
2. S0.2 — Faisabilité INDI / Astroberry / architecture client-serveur.
3. S0.3 — Faisabilité abstraction monture + caméra + focuser.
4. S0.4 — Faisabilité autofocus indépendant du constructeur.
5. S0.5 — Faisabilité astrométrie / plate solving / synchronisation du ciel.
6. S0.6 — Faisabilité déploiement et packaging Linux ARM64.
7. S0.7 — Faisabilité Android APK et protocole de contrôle distant.
8. S0.8 — Consolidation, analyse des risques et gel de l'architecture MVP.

## Gate G0 — Autorisation de commencer l'implémentation

Le passage à la Phase 1 exige :
- une preuve de faisabilité pour chaque fonction critique ;
- une architecture documentée ;
- les risques bloquants identifiés ;
- une matrice de compatibilité initiale ;
- une stratégie de test ;
- une décision explicite Go/No-Go.

## Phase 1 — MVP

La numérotation détaillée des sprints d'implémentation ne sera définie qu'après G0.

## Vision ultérieure

- autofocus ;
- plate solving ;
- GoTo et centrage automatique ;
- acquisition et suivi ;
- empilement temps réel ;
- catalogue et identification d'objets ;
- expérience de télescope intelligent ouverte et multi-constructeurs.
