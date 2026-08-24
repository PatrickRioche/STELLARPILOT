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

## 2. Récupérer le projet

```bash
cd ~
git clone https://github.com/PatrickRioche/STELLARPILOT.git
cd STELLARPILOT
git checkout main
```

Pour figer la version POC :

```bash
git checkout v0.5.0-poc
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
.venv/bin/pip install -r requirements.txt
.venv/bin/pip install numpy astropy Pillow
.venv/bin/python -c "import fastapi,numpy,astropy,PIL; print('Python OK')"
```

## 5. Mode matériel réel

Le service doit recevoir `STELLARPILOT_MODE=device`.

## 6. Service systemd

```bash
sudo cp ~/stellarpilot-server/systemd/stellarpilot-server.service   /etc/systemd/system/stellarpilot-server.service
sudo mkdir -p /etc/systemd/system/stellarpilot-server.service.d
sudo tee /etc/systemd/system/stellarpilot-server.service.d/device.conf >/dev/null <<'EOF'
[Service]
Environment=STELLARPILOT_MODE=device
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
