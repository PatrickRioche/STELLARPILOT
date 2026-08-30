# Scripts d'installation StellarPilot

## Serveur Raspberry Pi / Astroberry

Installation :

```bash
./scripts/install/install-server-astroberry.sh
```

Pré-requis principaux :

- Python 3 avec `venv` ;
- Git ;
- `rsync` ;
- INDI ;
- astrometry.net avec index astrométriques ;
- gpsd si un GPS est utilisé.

Les dépendances Python du serveur sont définies dans `server/requirements.txt`.

Ce fichier constitue la source de vérité et contient notamment :

- `fastapi`
- `uvicorn[standard]`
- `pydantic`
- `httpx`
- `numpy`
- `astropy`
- `Pillow`
- `scipy`
- `pytest`

Ne pas installer NumPy, Astropy, Pillow ou SciPy séparément.
Utiliser toujours `pip install -r requirements.txt`.

Le serveur StellarPilot fonctionne uniquement avec le matériel réel.
`STELLARPILOT_MODE` n'est plus utilisé.

Le mode Démonstration est entièrement local à l'application Android
et ne nécessite ni Raspberry Pi, ni serveur, ni INDI.

Documentation détaillée : `wiki/Server-Installation-Astroberry.md`.

## GPS

```bash
sudo ./scripts/install/configure-gpsd.sh
```

ou :

```bash
sudo ./scripts/install/configure-gpsd.sh /dev/ttyACM0
```

## Tablette Android depuis Windows

```powershell
.\scripts\install\install-tablet.ps1
```
