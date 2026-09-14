# DevOps du POC StellarPilot

## Objectif

Le POC doit permettre de développer et tester indépendamment :

1. `stellarpilot-server` sur Linux/Raspberry Pi ;
2. `StellarPilot-app` sur Android ;
3. l’intégration REST + WebSocket entre les deux ;
4. ultérieurement la chaîne matérielle INDI réelle.

## CI actuelle

Deux workflows assurent le contrôle continu :

- `Server POC CI` : installation Python, tests `pytest`, export OpenAPI ;
- `Android APK Debug` : build/test Android et génération des APK `simulationDebug` et `deviceDebug`.

## Variantes Android

### simulationDebug

Destinée au développement avec l’émulateur Android.

```text
http://10.0.2.2:8000/
```

### deviceDebug

Destinée au POC sur appareil Android réel et Raspberry Pi.

Pour le jalon courant :

```text
http://192.168.1.46:8000/
```

Cette adresse est provisoire. Une découverte mDNS/configuration dynamique doit la remplacer dans une étape ultérieure.

## Exploitation du serveur sur Raspberry Pi 5

Installation type :

```bash
cd ~/stellarpilot-server
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

Lancement manuel :

```bash
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

Test :

```bash
curl http://127.0.0.1:8000/status
```

## Service systemd

Fichier recommandé :

```text
/etc/systemd/system/stellarpilot-server.service
```

Contenu de référence :

```ini
[Unit]
Description=StellarPilot Server
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=astroberry
WorkingDirectory=/home/astroberry/stellarpilot-server
ExecStart=/home/astroberry/stellarpilot-server/.venv/bin/uvicorn app.main:app --host 0.0.0.0 --port 8000
Restart=always
RestartSec=3

[Install]
WantedBy=multi-user.target
```

Activation :

```bash
sudo systemctl daemon-reload
sudo systemctl enable stellarpilot-server
sudo systemctl start stellarpilot-server
```

État :

```bash
systemctl status stellarpilot-server --no-pager -l
```

Logs temps réel :

```bash
sudo journalctl -u stellarpilot-server -f
```

Logs du boot courant :

```bash
sudo journalctl -u stellarpilot-server -b
```

Dernières lignes :

```bash
sudo journalctl -u stellarpilot-server -n 100 --no-pager
```

## Politique de release POC

Une release taggée doit :

1. tester le serveur ;
2. exporter `openapi.json` ;
3. construire les deux variantes APK ;
4. empaqueter le répertoire `server/` ;
5. publier les artifacts dans une GitHub Release.
