# Architecture POC Android ↔ StellarPilot Server

## Principe

L’application Android n’accède jamais directement à INDI.

```text
┌─────────────────────────┐
│ StellarPilot Android    │
│ Kotlin / Compose        │
└────────────┬────────────┘
             │
             │ REST + WebSocket
             ▼
┌─────────────────────────┐
│ stellarpilot-server     │
│ FastAPI / Python        │
└────────────┬────────────┘
             │
      services métier
             │
       ┌─────┴─────┐
       ▼           ▼
 Simulation       INDI
                    │
               matériel réel
```

## Android

Organisation du premier jalon :

```text
MainActivity
   |
ConnectionScreen
   |
ConnectionViewModel
   |
StellarPilotApiClient
   |
REST / WebSocket
```

Le modèle `ServerStatus` représente le contrat minimal retourné par `/status`.

## Contrat minimal

### GET /status

Réponse attendue :

```json
{
  "service": "stellarpilot",
  "status": "ok",
  "poc": true,
  "session": {
    "latitude": null,
    "longitude": null,
    "altitude": null,
    "timestamp": null,
    "mount_type": null
  }
}
```

### WebSocket /ws

Le client ouvre un WebSocket après validation du statut REST.

Le serveur envoie un événement de connexion, puis peut transporter des événements asynchrones futurs : capture, mouvement monture, plate solving, erreurs matérielles, progression de séquence, etc.

## Réseau

Le serveur écoute sur :

```text
0.0.0.0:8000
```

Le POC a été validé avec une tablette connectée au réseau Wi-Fi/hotspot du Raspberry Pi.

## Évolutions prévues

- découverte mDNS ;
- URL serveur configurable ;
- authentification/sécurisation réseau au-delà du POC ;
- modèles d’équipements ;
- état INDI réel ;
- capture d’image ;
- transfert FITS/JPEG ;
- plate solving réel ;
- commandes monture ;
- séquences d’observation.
