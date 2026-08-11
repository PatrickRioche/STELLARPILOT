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
