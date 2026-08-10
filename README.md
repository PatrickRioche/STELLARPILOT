# StellarPilot

StellarPilot est un projet libre visant à fournir une couche de pilotage intelligente
et indépendante du constructeur pour l'astronomie amateur, au-dessus d'INDI.

Objectif à terme : permettre à un ensemble composé d'une monture AZ ou EQ, d'un
focuser et d'une caméra compatible INDI de se comporter comme un télescope
intelligent : autofocus, astrométrie, centrage, acquisition, observation assistée,
empilement et identification du ciel.

## Cibles prévues

- Linux ARM64 : Astroberry / Raspberry Pi et systèmes compatibles.
- Android : application APK de contrôle.
- Interface matérielle : INDI.
- Licence : GPL-3.0-or-later.

## Phase actuelle

Le projet démarre volontairement par des sprints de **spécification** et de
**vérification de faisabilité**. Aucun choix d'architecture irréversible ne doit
être considéré comme acquis avant validation documentée.

## Organisation

- `docs/04-sprints/` : définition, preuves et résultats de chaque sprint.
- `docs/05-releases/` : politique de versions et artefacts publiés.
- `docs/06-decisions/` : Architecture Decision Records (ADR).
- `docs/03-feasibility/` : preuves de faisabilité et critères Go/No-Go.
- `src/` : futur code commun.
- `platform/arm64/` : intégration Linux ARM64.
- `platform/android/` : intégration Android.
- `packaging/` : futurs paquets de release.

La version de travail est stockée dans `VERSION`. Les numéros de sprint ne sont
jamais utilisés comme numéros de version du produit.
