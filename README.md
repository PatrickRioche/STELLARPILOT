<<<<<<< HEAD
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

Le projet est en **phase POC de faisabilité**. La priorité est de démontrer sur matériel réel la chaîne Android -> Raspberry Pi ARM64 -> INDI -> monture/caméra -> capture -> plate solving local -> retour de la solution astrométrique vers Android.

Le focuser motorisé et l'autofocus sont volontairement différés. La séquence fonctionnelle cible utilisera d'abord une mise au point manuelle au masque de Bahtinov, puis des darks réalisés avec le capuchon standard.

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
=======
# StellarPilot POC

POC minimal de la chaîne StellarPilot : client Android → serveur Raspberry Pi → abstraction INDI → capture → plate solving.

## Démarrer le serveur

```bash
cd server
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
./run.sh
```

Puis ouvrir `http://<ip-du-pi>:8000/docs` pour l'interface OpenAPI FastAPI.

## Tester

```bash
cd server
PYTHONPATH=. pytest -q
```

## État

Le serveur API est exécutable. Les couches INDI et plate solving sont volontairement simulées dans ce premier squelette ; elles constituent le prochain lot fonctionnel du POC.
>>>>>>> master
