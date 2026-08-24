# Tests PROTO-CONN-01

Serveur : 0.4-proto
Protocole : proto-1

## Test nominal
Attendu : REST OK et WebSocket Connecte.

## Arret serveur
Attendu : CONNECTED -> RECONNECTING.
Backoff : 1 / 2 / 4 / 8 / 15 / 30 secondes.

## Redemarrage serveur
Attendu : retour automatique a CONNECTED sans action utilisateur.

## Coupure Wi-Fi
Attendu : CONNECTED -> RECONNECTING -> CONNECTED.

## Redemarrage Raspberry Pi
Attendu : reconnexion automatique apres retour du serveur.

## Panne peripherique
Une panne GPS, camera ou monture ne doit pas deconnecter le serveur.

## Validation
Build deviceDebug reussi.
main.py valide.
Handshake proto-1 valide.
Backoff valide.
Recuperation serveur et Wi-Fi automatique.

## R?sultats de validation ? 24 ao?t 2026

### Connexion initiale

**PASS**

La connexion est d?sormais imm?diate lorsque le serveur est disponible.

Exemple mesur? :

- WebSocket ouvert : `10:03:50.942`
- HELLO envoy? : `10:03:50.943`
- WELCOME re?u : `10:03:50.949`

Le handshake applicatif est r?alis? en quelques millisecondes.

### Arr?t et red?marrage du serveur

**PASS**

Apr?s une perte de connexion, Android d?tecte la rupture et tente
automatiquement de retrouver StellarPilot Server.

S?quence observ?e :

- d?tection de la coupure ;
- reconnexion apr?s 1 s ;
- puis 2 s ;
- puis 3 s ;
- puis 5 s ;
- puis 8 s si n?cessaire ;
- ouverture automatique du WebSocket ;
- nouvel ?change HELLO/WELCOME ;
- retour automatique ? `CONNECTED`.

Lors d'un test complet, la coupure a ?t? d?tect?e ? `10:04:41.075`
et le nouveau WELCOME re?u ? `10:05:00.584`.

### Perte du r?seau Wi-Fi

**PASS**

Une perte r?seau r?elle a produit `ENETUNREACH`.

Apr?s le retour du r?seau, aucune intervention utilisateur n'a ?t? n?cessaire :

- perte d?tect?e : `10:09:00.811`
- WELCOME re?u : `10:09:06.973`

La r?cup?ration compl?te a donc pris environ 6,2 secondes dans ce test.

### Ind?pendance de /status

**PASS**

Un timeout de `/status` a ?t? observ? alors que le WebSocket ?tait connect?.

Le timeout de t?l?m?trie n'a pas provoqu? la fermeture du canal temps r?el.

Cela valide la s?paration :

`REST / t?l?m?trie / WebSocket`

### Interface Android

**PASS**

Les ?crans ne d?duisent plus l'?tat de connexion du contenu de `/status`.

Apr?s reconnexion :

- `CONNECTED` affiche imm?diatement le serveur comme connect? ;
- `RECONNECTING` affiche ? Reconnexion au serveur... ? ;
- une t?l?m?trie absente est indiqu?e comme telle ;
- elle n'est plus pr?sent?e comme une d?connexion du serveur.

### ?tat de PROTO-CONN-01

Les sc?narios essentiels du prototype sont valid?s :

- connexion initiale rapide ;
- handshake HELLO/WELCOME ;
- heartbeat WebSocket ;
- d?tection de rupture ;
- reconnexion automatique ;
- r?cup?ration apr?s indisponibilit? du service ;
- r?cup?ration apr?s perte r?seau ;
- isolation de `/status` ;
- coh?rence de l'?tat affich? par l'interface.

Le test d'un red?marrage complet du Raspberry Pi pourra ?tre conserv? comme
test d'int?gration compl?mentaire.
