# Résultat — POC-S0.2-indi-mount

## Statut

PASS — Validé le 12 août 2026.

## Environnement

* Plateforme : Raspberry Pi 5 Model B Rev 1.0
* Architecture : ARM64 / aarch64
* Système : Debian GNU/Linux 13 (trixie)
* INDI Library : 2.2.1
* INDI Protocol : 1.7
* Serveur INDI : port TCP 7624
* Driver monture : `indi_lx200_OnStep`
* Version driver : 1.26
* Firmware OnStep : 10.28u
* Type de monture détecté : German Equatorial Mount

## Matériel

* Contrôleur : OnStep
* Liaison : USB série
* Convertisseur USB/série : QinHeng Electronics CH340
* Identifiant USB : `1a86:7523`
* Port Linux : `/dev/ttyUSB0`
* Chemin persistant : `/dev/serial/by-id/usb-1a86_USB_Serial-if00-port0`
* Débit série : 9600 bauds

## Procédure

1. Vérification du serveur INDI et du driver `indi_lx200_OnStep`.
2. Vérification de la liaison série et du périphérique `/dev/ttyUSB0`.
3. Vérification de l'absence de simulation.
4. Vérification de la connexion effective de la monture.
5. Lecture des coordonnées équatoriales et des états de suivi, parcage et côté du pied.
6. Vérification de la commande d'arrêt `TELESCOPE_ABORT_MOTION`.
7. Vérification de l'heure système du Raspberry Pi et de la synchronisation NTP.
8. Synchronisation de l'heure OnStep avec l'horloge UTC du Raspberry Pi.
9. Mise à jour du site d'observation via `GEOGRAPHIC_COORD`.
10. Vérification des commandes de guidage temporisé.
11. Test d'une impulsion de guidage Ouest de 500 ms.
12. Lecture détaillée des vitesses de déplacement exposées par le driver OnStep.
13. Sélection de la vitesse 48×.
14. Commande d'un mouvement manuel Ouest pendant environ 1 seconde.
15. Arrêt explicite du mouvement.
16. Envoi d'une commande `ABORT` de sécurité.
17. Vérification de l'absence de mouvement résiduel.

## Résultats

La monture OnStep est correctement détectée et connectée via INDI :

`LX200 OnStep.CONNECTION.CONNECT=On`

La simulation est désactivée :

`LX200 OnStep.SIMULATION.DISABLE=On`

Les coordonnées équatoriales sont lisibles depuis INDI.

Le driver expose notamment :

* coordonnées équatoriales ;
* suivi ;
* parcage ;
* côté du pied ;
* mouvements N/S et W/E ;
* vitesses de slew ;
* guidage temporisé ;
* arrêt d'urgence ;
* heure UTC ;
* localisation géographique.

La monture a correctement accepté la synchronisation temporelle depuis le Raspberry Pi.

Avant synchronisation :

`2026-07-26T03:55:41 / UTC+1`

Après synchronisation :

`2026-08-12T08:29:42 / UTC+2`

La localisation de test a été configurée avec :

* latitude : 47,47°
* longitude INDI : 359,45°
* élévation : 0 m

La commande `TELESCOPE_ABORT_MOTION` a été testée avec succès.

Le driver expose les vitesses suivantes :

* 0 : 0.25×
* 1 : 0.5×
* 2 : 1×
* 3 : 2×
* 4 : 4×
* 5 : 8×
* 6 : 20×
* 7 : 48×
* 8 : Half-Max
* 9 : Max

Un mouvement manuel Ouest à 48× pendant environ 1 seconde a été commandé via INDI.

Le mouvement physique de la monture a été confirmé auditivement.

Après la commande d'arrêt et l'ABORT de sécurité :

`LX200 OnStep.TELESCOPE_MOTION_WE.MOTION_WEST=Off`

`LX200 OnStep.TELESCOPE_MOTION_WE.MOTION_EAST=Off`

`LX200 OnStep.TELESCOPE_ABORT_MOTION.ABORT=Off`

Aucun mouvement résiduel n'a été constaté.

## Mesures

* Débit série : 9600 bauds
* Impulsion de guidage testée : 500 ms Ouest
* Vitesse du mouvement physique de validation : 48×
* Durée approximative du mouvement manuel : 1 s
* Latitude de test : 47,47°
* Longitude de test : 359,45° au format INDI
* Élévation de test : 0 m
* Heure UTC OnStep synchronisée : 2026-08-12T08:29:42
* Offset local configuré : UTC+2

## Limites

* Le déplacement physique a été confirmé auditivement mais n'a pas fait l'objet d'une mesure angulaire externe indépendante.
* Aucun GOTO complet n'a été exécuté dans ce POC.
* Aucun test de précision de pointage ou de suivi longue durée n'a été réalisé.
* Aucun alignement multi-étoiles n'a été réalisé.
* Le test a été effectué avec une monture de type German Equatorial Mount.
* La localisation utilisée est une valeur de test représentative d'Angers et non une mesure GPS précise du site.
* L'élévation a été laissée à 0 m pour ce POC.
* La validation de l'astrométrie et de la synchronisation du pointage appartient au POC-S0.3.

## Preuves

* `indiserver` actif sur le port 7624.
* Driver `indi_lx200_OnStep` actif.
* Liaison série CH340 détectée.
* Port `/dev/ttyUSB0` résolu correctement.
* Monture connectée à INDI.
* Simulation désactivée.
* Coordonnées et états de la monture lisibles.
* Synchronisation temporelle réussie.
* Localisation modifiable via INDI.
* Commande `ABORT` fonctionnelle.
* Guidage temporisé disponible et modifiable.
* Vitesses de slew exposées par le driver.
* Vitesse 48× sélectionnée.
* Mouvement Ouest réel commandé.
* Mouvement physique confirmé auditivement.
* Arrêt explicite exécuté.
* Aucun mouvement résiduel après arrêt.

## Décision

* [x] Go
* [ ] Go avec réserve
* [ ] No-Go

Le POC-S0.2 démontre que StellarPilot peut communiquer avec une monture OnStep réelle via INDI, lire son état, synchroniser ses paramètres de site et d'heure, transmettre des commandes de mouvement au matériel et arrêter proprement le mouvement.

Le projet peut passer au POC-S0.3 consacré à l'astrométrie locale.
