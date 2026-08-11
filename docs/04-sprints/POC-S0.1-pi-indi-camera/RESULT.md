# Résultat — POC-S0.1-pi-indi-camera

## Statut

PASS — Validé le 11 août 2026.

## Environnement

* Plateforme : Raspberry Pi 5 Model B Rev 1.0
* Architecture : ARM64 / aarch64
* Système : Debian GNU/Linux 13 (trixie)
* Noyau : Linux 6.12.47+rpt-rpi-2712
* INDI Library : 2.2.1
* INDI Protocol : 1.7
* Driver caméra : `indi_playerone_ccd` 1.22
* Serveur INDI : port TCP 7624

## Matériel

* Caméra : Player One Uranus-C
* Identifiant USB : `a0a0:5850`
* Résolution détectée : 3856 × 2180 pixels
* Taille de pixel : 2,9 µm
* Matrice de Bayer : RGGB
* Mode d'acquisition testé : RAW16
* Binning : 1 × 1

## Procédure

1. Vérification de l'environnement ARM64 et de l'installation INDI.
2. Détection USB de la Player One Uranus-C avec `lsusb`.
3. Vérification de la présence du driver `indi_playerone_ccd`.
4. Vérification du serveur INDI existant sur le port 7624.
5. Découverte des propriétés de la caméra avec `indi_getprop`.
6. Vérification de la connexion effective de la caméra à INDI.
7. Sélection du transfert local et du format FITS.
8. Configuration du répertoire de capture StellarPilot.
9. Déclenchement d'une exposition réelle de 1 seconde avec `indi_setprop`.
10. Vérification du fichier FITS généré.

## Résultats

La Player One Uranus-C est correctement détectée par Linux et exposée par INDI sous le nom :

`PlayerOne CCD Uranus-C`

La connexion INDI a été confirmée :

`PlayerOne CCD Uranus-C.CONNECTION.CONNECT=On`

Une exposition réelle de 1 seconde a produit avec succès :

`STELLARPILOT_001.fits`

Le système Linux identifie le fichier comme une image FITS valide :

`FITS image data, 16-bit, two's complement binary integer, 2 axes, 3856 x 2180`

## Mesures

* Temps d'exposition : 1 s
* Dimensions : 3856 × 2180 pixels
* Profondeur : 16 bits
* Taille du fichier : environ 17 MB
* SHA-256 : `050d816a5fe839751c6d56536133926f4c740018ce8f32b6895ee17b772ec065`

## Limites

* Le contenu détaillé de l'en-tête FITS n'a pas été inspecté, aucun utilitaire `fitsheader` ou `funhead` n'étant installé.
* Ce POC valide l'acquisition d'une image unique ; il ne mesure pas encore les performances d'acquisitions répétées ou longues.
* Le POC utilise l'instance `indiserver` déjà fournie par l'environnement Astroberry.
* Aucun test Android, astrométrique ou de pilotage de monture n'entre dans le périmètre de ce sprint.

## Preuves

* Détection USB de la Player One Uranus-C.
* Driver `indi_playerone_ccd` présent et actif.
* `indiserver` actif sur le port 7624.
* Caméra connectée via INDI.
* Format FITS sélectionné.
* Acquisition réelle réussie.
* Fichier `STELLARPILOT_001.fits` généré et reconnu comme FITS 16 bits 3856 × 2180.
* Empreinte SHA-256 enregistrée pour la capture de référence.

## Décision

* [x] Go
* [ ] Go avec réserve
* [ ] No-Go

Le POC-S0.1 démontre que la chaîne Raspberry Pi 5 ARM64 → INDI → Player One Uranus-C → acquisition FITS est techniquement fonctionnelle.

Le projet peut passer au POC-S0.2 consacré au pilotage de la monture via INDI.
