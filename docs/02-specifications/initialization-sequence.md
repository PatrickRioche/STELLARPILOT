# Spécification — séquence initiale StellarPilot

## Objectif

Définir la séquence utilisateur cible à partir des essais réels du 5 au 6 septembre 2026.

Le principe retenu est désormais le suivant : StellarPilot ne demande plus à l'utilisateur d'orienter manuellement la monture vers un point cardinal, le pôle céleste ou le zénith avant la première astrométrie. La monture peut être positionnée avec une autre console, l'interface OnStep, un mobile connecté en Wi-Fi ou tout autre client compatible.

StellarPilot lit la monture via INDI et utilise, lorsqu'elles sont disponibles, les coordonnées équatoriales AD/DEC comme information de départ pour le solveur. Le plate solving reste l'autorité pour déterminer le champ réellement observé.

## 1. Démarrage du serveur

Le Raspberry Pi démarre StellarPilot Server et initialise :
- le service local ;
- la communication avec INDI ;
- la détection de la monture ;
- la détection de la caméra ;
- l'API utilisée par Android.

## 2. Connexion Android

L'utilisateur ouvre StellarPilot sur Android et se connecte au serveur local du Raspberry Pi.

L'application affiche au minimum :
- serveur connecté ;
- caméra détectée ;
- monture détectée ;
- état INDI de la monture ;
- type AZ/EQ si disponible.

Le type de monture reste une information de contexte matériel. Il ne doit plus imposer une étape d'orientation initiale dans l'assistant.

## 3. Contexte local

L'application et le serveur disposent, lorsque possible, de :
- date ;
- heure locale ;
- fuseau horaire ;
- latitude ;
- longitude ;
- altitude si disponible et utile.

La localisation sert notamment au catalogue céleste et aux calculs de visibilité. Elle ne doit pas bloquer la première résolution astrométrique lorsque le solveur peut fonctionner sans elle.

## 4. Positionnement de la monture

Il n'existe plus d'étape interactive « Orientation initiale » dans StellarPilot.

L'utilisateur peut positionner la monture avant ou pendant la préparation avec :
- l'interface OnStep ;
- un téléphone ou une tablette en Wi-Fi ;
- une autre console de commande ;
- tout autre client INDI / LX200 compatible.

StellarPilot ne doit pas envoyer automatiquement la monture vers le nord, le sud, le zénith ou une direction intermédiaire uniquement pour préparer l'astrométrie.

Lorsque INDI expose les coordonnées de la monture, StellarPilot lit en priorité une propriété équatoriale disponible, par exemple :
- `EQUATORIAL_EOD_COORD` ;
- à défaut `EQUATORIAL_COORD`.

Ces coordonnées constituent un **hint** facultatif pour astrometry.net. Elles ne remplacent jamais la solution astrométrique issue de l'image.

## 5. Capture et astrométrie

StellarPilot :
1. lit si possible la position AD/DEC actuelle de la monture via INDI ;
2. capture une image ;
3. réalise un plate solving local ;
4. utilise la position INDI comme hint lorsqu'elle est disponible et cohérente ;
5. conserve une stratégie de résolution sans hint comme solution de repli ;
6. récupère la position réelle du centre du champ ;
7. récupère l'échelle angulaire ;
8. associe la solution au contexte de session.

Résultat minimal affiché à l'utilisateur :
- AD/RA du centre ;
- DEC du centre ;
- échelle angulaire ;
- statut de résolution ;
- nombre d'étoiles détectées lorsque disponible.

L'orientation WCS peut rester enregistrée dans les données techniques du serveur, mais elle n'est plus un élément nécessaire dans l'interface de préparation.

### Échelle de référence validée le 5 septembre 2026

Les 25 solutions obtenues lors du retest de la nuit donnent :
- échelle moyenne : **1,217212 arcsec/pixel** ;
- dispersion : **0,002095 arcsec/pixel** ;
- valeur de référence à utiliser pour ce setup : **environ 1,218 arcsec/pixel**.

Pour ce profil instrument, une fenêtre rapide autour de `0,90–1,50 arcsec/pixel` est adaptée à la première tentative.

## 6. Affichage de la capture sur tablette

L'aperçu de la caméra doit exploiter au maximum la largeur disponible, notamment sur tablette en mode portrait.

Exigences :
- ne plus limiter l'aperçu principal à une hauteur fixe de 240 dp ;
- conserver le ratio du capteur / de la preview ;
- utiliser toute la largeur disponible de la tablette ;
- permettre le zoom par pincement à deux doigts ;
- permettre le déplacement de l'image lorsqu'elle est zoomée ;
- revenir à l'échelle 1:1 lorsque le zoom revient à 1× ;
- conserver le réticule de centrage indépendant du zoom.

Pour l'Uranus-C utilisée pendant les essais, le ratio nominal est `3856 / 2180 ≈ 1,769`.

## 7. Mise au point

StellarPilot propose une étoile brillante.

Après pointage :
- installation du masque de Bahtinov ;
- affichage agrandi de l'étoile ;
- zoom tactile disponible ;
- réglage manuel ;
- validation ;
- retrait du masque.

## 8. Darks

StellarPilot demande de remettre le capuchon standard, acquiert les darks nécessaires, puis demande de retirer le capuchon.

Cette étape peut rester secondaire pendant les phases de validation de la motorisation, de l'astrométrie et de la mise au point.

## 9. Observation

Le système passe à l'état `READY_FOR_OBSERVATION`.

La chaîne d'observation pourra ensuite comprendre :
- choix d'une cible ;
- GoTo ;
- attente de fin de mouvement ;
- capture ;
- plate solving ;
- recentrage ;
- acquisition ;
- calibration ;
- empilement ;
- affichage progressif.

## Règle importante concernant les mouvements

Une capture destinée à l'astrométrie ne doit pas être déclenchée pendant un mouvement actif de la monture. Si INDI indique que la monture est en déplacement, StellarPilot doit attendre la stabilisation ou avertir l'utilisateur avant de lancer la pose.