# StellarPilot — Protocole API POC

Base URL : `http://<raspberry-pi>:8000`

## REST

- `GET /status` — état du serveur et de la session.
- `GET /devices` — périphériques exposés par la couche INDI.
- `POST /system/location` — latitude, longitude, altitude, horodatage Android.
- `POST /system/mount-type` — `EQ` ou `AZ`.
- `POST /camera/capture` — acquisition d'une image.
- `POST /mount/goto` — ordre de pointage.
- `POST /solve` — résolution astrométrique d'une image.

## WebSocket

- `WS /ws` — canal d'événements asynchrones.

Le POC commence par un canal simple. Les événements cibles seront notamment : `capture_started`, `capture_completed`, `plate_solve_started`, `plate_solved`, `device_connected`, `device_error`.
