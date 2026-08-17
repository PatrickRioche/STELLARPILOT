# Test E2E — Android ↔ Raspberry Pi 5

## But

Valider la communication réelle entre un APK StellarPilot installé sur une tablette Android et `stellarpilot-server` exécuté sur Raspberry Pi 5.

## Configuration validée le 17 août 2026

Raspberry Pi :

```text
192.168.1.46
10.42.0.1
```

La tablette utilisée pendant le test apparaissait dans les logs serveur avec une adresse du réseau hotspot :

```text
10.42.0.178
```

## 1. Démarrer le serveur

```bash
cd ~/stellarpilot-server
source .venv/bin/activate
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

Attendu :

```text
Uvicorn running on http://0.0.0.0:8000
```

## 2. Tester localement sur le Pi

```bash
curl http://127.0.0.1:8000/status
```

Attendu : HTTP 200 avec `service=stellarpilot` et `status=ok`.

## 3. Tester depuis un PC du LAN

```powershell
curl.exe http://192.168.1.46:8000/status
```

## 4. Tester depuis Android avant installation

Dans le navigateur de la tablette :

```text
http://192.168.1.46:8000/status
```

## 5. Installer l’APK

Depuis GitHub Actions, récupérer l’artifact :

```text
StellarPilot-device-debug
```

Extraire l’archive et installer le fichier `.apk`.

## 6. Tester l’application

Dans StellarPilot :

1. vérifier `Backend : DEVICE` ;
2. vérifier l’URL du serveur ;
3. lancer le test de connexion ;
4. vérifier `REST /status : OK` ;
5. vérifier la connexion WebSocket.

## 7. Vérification côté Pi

Exemple de logs validés :

```text
GET /status HTTP/1.1" 200 OK
WebSocket /ws [accepted]
connection open
```

Une reconnexion peut produire :

```text
StellarPilot client disconnected
```

puis une nouvelle ouverture WebSocket. Ce comportement est normal lorsque l’application relance son test de connexion.

## Résultat

La communication Android ↔ Raspberry Pi via REST et WebSocket est validée pour le premier jalon POC.

## Non couvert

- communication INDI réelle ;
- capture caméra réelle ;
- GOTO réel ;
- plate solving réel ;
- transfert d’images ;
- comportement hors réseau local ;
- découverte automatique du serveur.
