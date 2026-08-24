# Architecture StellarPilot

```text
┌───────────────────────────────┐
│ Tablette Android              │
│ Kotlin + Jetpack Compose      │
└──────────────┬────────────────┘
               │ REST + WebSocket
               v
┌───────────────────────────────┐
│ StellarPilot Server           │
│ Raspberry Pi 5 / Astroberry   │
│ Python + FastAPI + Uvicorn    │
└───┬──────────┬──────────┬─────┘
    │          │          └──> gpsd : 127.0.0.1:2947
    │          └─────────────> astrometry.net
    └────────────────────────> INDI : 127.0.0.1:7624
```

## Réseau

Hotspot : serveur réel `10.42.0.1:8000`, simulation future `10.42.0.1:8008`.
LAN de développement validé : `192.168.1.46:8000`.

## INDI

StellarPilot utilise les outils INDI, notamment `indi_getprop` et `indi_setprop`, contre `127.0.0.1:7624`.

## GPS

Le serveur utilise le protocole JSON de gpsd sur `127.0.0.1:2947`. États : `fix`, `no_fix`, `unavailable`. Un fix valide est prioritaire en mode `device`; sinon la position manuelle de session est utilisée.

## Capture

Les FITS sont stockés temporairement dans `/tmp/stellarpilot-captures/`.

```text
INDI → FITS RAW16 ─┬→ astrometry.net
                   └→ débayer / stretch → JPEG couleur → Android
```

## Plate solving

Le serveur lance localement `solve-field` et `wcsinfo`. Aucun service Internet n’est requis une fois les index installés.

## Mode serveur

`STELLARPILOT_MODE=device` active l’accès au matériel réel. Sans cette variable, le serveur retombe sur le mode `simulation`.
