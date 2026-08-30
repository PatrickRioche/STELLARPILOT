# Installation du serveur sur Raspberry Pi / Astroberry

Configuration de référence : utilisateur `astroberry`, déploiement `/home/astroberry/stellarpilot-server`.

## 1. Pré-requis

Vérifier :

```bash
python3 --version
git --version
command -v indi_getprop
command -v indi_setprop
command -v solve-field
command -v wcsinfo
```

Le Pi doit disposer de Python 3, venv, Git, INDI, astrometry.net, index astrométriques et gpsd.

### Dépendances Python du serveur

La source de vérité est `server/requirements.txt`.

Elle installe notamment :

- `fastapi` : API REST et WebSocket ;
- `uvicorn[standard]` : serveur ASGI ;
- `pydantic` : validation des données ;
- `httpx` : client HTTP et tests ;
- `numpy` : calcul numérique et traitement d'image ;
- `astropy` : FITS et fonctions astronomiques ;
- `Pillow` : traitement des images (`PIL`) ;
- `scipy` : traitement scientifique et analyse d'image ;
- `pytest` : tests automatisés.

Ne pas installer ces bibliothèques une par une.
Utiliser toujours `pip install -r requirements.txt`.


## 2. Récupérer le projet

```bash
cd ~
git clone https://github.com/PatrickRioche/STELLARPILOT.git
cd STELLARPILOT
git checkout main
```

## 3. Déployer le serveur

```bash
mkdir -p ~/stellarpilot-server
rsync -a --delete --exclude '.venv' --exclude '__pycache__'   ~/STELLARPILOT/server/ ~/stellarpilot-server/
```

## 4. Environnement Python

```bash
cd ~/stellarpilot-server
python3 -m venv .venv
.venv/bin/python -m pip install --upgrade pip
.venv/bin/python -m pip install -r requirements.txt
.venv/bin/python -c "import fastapi,numpy,astropy,PIL,scipy; print('Python StellarPilot OK')"
```

## 5. Serveur matériel réel

Le serveur StellarPilot fonctionne uniquement avec le matériel réel.

La variable `STELLARPILOT_MODE` n'est plus utilisée.

Le mode Démonstration est entièrement local dans l'application Android
et fonctionne sans Raspberry Pi, sans serveur et sans INDI.

## 6. Service systemd

```bash
sudo cp ~/stellarpilot-server/systemd/stellarpilot-server.service   /etc/systemd/system/stellarpilot-server.service
sudo mkdir -p /etc/systemd/system/stellarpilot-server.service.d
sudo rm -f /etc/systemd/system/stellarpilot-server.service.d/device.conf
sudo tee /etc/systemd/system/stellarpilot-server.service.d/runtime.conf >/dev/null <<'EOF'
[Service]
Environment=PYTHONUNBUFFERED=1
EOF
sudo systemctl daemon-reload
sudo systemctl enable stellarpilot-server
sudo systemctl restart stellarpilot-server
```

## 7. Vérification

```bash
systemctl status stellarpilot-server --no-pager -l
curl http://127.0.0.1:8000/health
curl http://127.0.0.1:8000/status
```

OpenAPI : `http://<IP-DU-PI>:8000/docs` et `http://<IP-DU-PI>:8000/openapi.json`.

## 8. Vérification INDI

```bash
indi_getprop -h 127.0.0.1 -p 7624 -t 2 '*.CONNECTION.*'
```

## 9. Vérification astrometry.net

```bash
solve-field --help >/dev/null
wcsinfo --help >/dev/null
find /usr/share/astrometry -type f -name 'index-*.fits' | head
```

Un solveur installé sans index ne peut pas résoudre les images.

## 10. Script automatisé

Depuis la racine du dépôt :

```bash
chmod +x scripts/install/install-server-astroberry.sh
./scripts/install/install-server-astroberry.sh
```

Le script effectue les préchecks, déploie `server/`, crée le venv, configure le mode `device`, installe systemd et vérifie `/health`.
