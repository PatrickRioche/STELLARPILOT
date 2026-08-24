# Connexion robuste Android - StellarPilot Server

## Reference
Lot : PROTO-CONN-01
Serveur : 0.4-proto
Protocole : proto-1

## Objectif
La connexion Android-serveur doit recuperer automatiquement une coupure reseau ou un redemarrage du serveur.

## Architecture
Android verifie GET /health puis GET /status avant d'ouvrir /ws.
Le heartbeat utilise le ping/pong WebSocket natif OkHttp toutes les 10 secondes.

## Etats
DISCONNECTED -> CONNECTING -> CONNECTED -> RECONNECTING

## Reconnexion
Backoff : 1 s, 2 s, 4 s, 8 s, 15 s, puis 30 s.
Une connexion reussie remet le compteur a zero.

## Handshake
Le serveur annonce connected / proto-1.
Android envoie hello avec client=android.
Le serveur repond welcome / proto-1.

## Isolation du materiel
Une panne GPS, camera ou monture ne doit pas etre interpretee comme une perte du serveur.

## Compatibilite
Le champ poc reste temporairement conserve.
Le prototype ajoute prototype=true et protocol=proto-1.

## Validation PROTO-CONN-01 ? 24 ao?t 2026

La connexion robuste Android ? StellarPilot Server est valid?e pour le prototype.

### Architecture retenue

Les communications Android utilisent trois clients OkHttp distincts :

- client REST principal pour `/health` et les API classiques ;
- client d?di? ? `/status` pour la t?l?m?trie mat?rielle ;
- client d?di? au WebSocket `/ws` pour le canal temps r?el.

Cette s?paration emp?che une requ?te `/status` lente ou bloqu?e de perturber
le WebSocket et son heartbeat.

### Handshake applicatif

Chaque ouverture ou r?ouverture du WebSocket utilise le protocole `proto-1` :

1. ouverture du WebSocket ;
2. envoi d'un message `HELLO` par Android ;
3. r?ponse `WELCOME` du serveur ;
4. passage ? l'?tat `CONNECTED` uniquement apr?s validation du protocole.

Un d?lai maximal de 5 secondes est appliqu? au handshake.

### Heartbeat

Le client WebSocket utilise le ping/pong natif d'OkHttp avec un intervalle
de 10 secondes.

La t?l?m?trie `/status` est ind?pendante du heartbeat.

### Reconnexion automatique

La strat?gie de reconnexion utilise le backoff suivant :

`1 s ? 2 s ? 3 s ? 5 s ? 8 s ? 10 s`

Le d?lai reste plafonn? ? 10 secondes pour conserver une r?cup?ration rapide
sur le r?seau local StellarPilot.

### ?tats de connexion

La connexion technique au serveur est repr?sent?e uniquement par
`ConnectionState` :

- `DISCONNECTED`
- `CONNECTING`
- `CONNECTED`
- `RECONNECTING`
- `STOPPED`

La pr?sence de `ServerStatus` ne repr?sente pas la connexion r?seau.
Elle indique uniquement que la t?l?m?trie `/status` est disponible.

Ainsi :

- WebSocket + `WELCOME` = serveur connect? ;
- `/status` indisponible = t?l?m?trie indisponible ;
- une panne de t?l?m?trie ne provoque plus un faux ?tat ? serveur non connect? ?.

Les ?crans Connexion, Statut et Pr?paration suivent cette r?gle.
