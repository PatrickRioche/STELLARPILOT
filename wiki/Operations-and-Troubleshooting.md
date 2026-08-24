# Exploitation et dépannage

## Serveur

```bash
systemctl status stellarpilot-server --no-pager -l
sudo systemctl restart stellarpilot-server
sudo journalctl -u stellarpilot-server -f
sudo journalctl -u stellarpilot-server -b
sudo journalctl -u stellarpilot-server -n 100 --no-pager
```

## API

```bash
curl http://127.0.0.1:8000/health
curl http://127.0.0.1:8000/status
sudo ss -ltnp | grep 8000
```

## INDI

```bash
sudo ss -ltnp | grep 7624
ps -ef | grep '[i]ndiserver'
indi_getprop -h 127.0.0.1 -p 7624 -t 2 '*.CONNECTION.*'
```

## GPS

```bash
systemctl status gpsd.socket --no-pager -l
systemctl status gpsd.service --no-pager -l
sudo ss -ltnp | grep 2947
ps -ef | grep '[g]psd'
cgps -s
sudo fuser -v /dev/ttyACM0
```

## Astrometry.net

```bash
command -v solve-field
command -v wcsinfo
find /usr/share/astrometry -type f -name 'index-*.fits' | head
```

Un timeout en plein jour sans étoiles est attendu. Un timeout persistant de nuit avec un champ exploitable doit être diagnostiqué.

## Captures caméra

```bash
ls -lht /tmp/stellarpilot-captures | head
curl -D - http://127.0.0.1:8000/camera/preview.jpg -o /tmp/stellarpilot-preview.jpg
```

Pour la caméra couleur validée, l’aperçu peut annoncer `X-StellarPilot-Bayer: RGGB` et `X-StellarPilot-Preview: color-global-stretch`.

## Android

```powershell
adb devices
adb logcat | Select-String "StellarPilot|StellarPreview"
```

## Réseau

```powershell
Test-NetConnection 192.168.1.46 -Port 8000
Test-NetConnection 10.42.0.1 -Port 8000
```

Ordre conseillé : réseau → service → `/health` → `/status` → gpsd → INDI → matériel → astrometry.net → Android.
