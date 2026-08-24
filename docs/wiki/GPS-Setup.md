# Configuration du GPS USB avec gpsd

Le GPS G-MOUSE validé pendant le POC apparaît comme `/dev/ttyACM0`. StellarPilot ne lit pas directement le port série : il dialogue avec gpsd sur `127.0.0.1:2947`.

## Détection

```bash
ls -l /dev/ttyACM*
ls -l /dev/ttyUSB*
ls -l /dev/serial/by-id/ 2>/dev/null
dmesg | tail -n 50
```

## Installation

```bash
sudo apt update
sudo apt install -y gpsd gpsd-clients
sudo usermod -aG dialout astroberry
```

## Configuration automatisée

```bash
sudo ./scripts/install/configure-gpsd.sh
```

Pour imposer le périphérique validé :

```bash
sudo ./scripts/install/configure-gpsd.sh /dev/ttyACM0
```

Le script sauvegarde `/etc/default/gpsd` avant modification.

## Services validés

```bash
sudo systemctl start gpsd.socket
sudo systemctl restart gpsd.service
sleep 3
systemctl status gpsd.socket --no-pager -l
systemctl status gpsd.service --no-pager -l
```

## Diagnostic

```bash
sudo ss -ltnp | grep 2947
ps -ef | grep '[g]psd'
sudo fuser -v /dev/ttyACM0
cgps -s
gpspipe -w -n 20
```

Un TPV `mode: 2` indique au minimum un fix 2D ; `mode: 3` un fix 3D.

## StellarPilot

```bash
curl -s http://127.0.0.1:8000/status
```

Le GPS peut être `unavailable`, `no_fix` ou `fix`. Lorsque le GPS est débranché ou sans fix, StellarPilot peut utiliser la position de session saisie manuellement.
