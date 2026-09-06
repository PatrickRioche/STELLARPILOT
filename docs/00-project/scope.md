# Périmètre actuel

## Priorité immédiate : POC de faisabilité

Le premier objectif n'est pas de construire le produit complet. Il consiste à prouver que StellarPilot peut relier de bout en bout :

- une application Android ;
- un serveur StellarPilot sur Raspberry Pi ARM64 ;
- INDI ;
- une monture AZ ou EQ ;
- une caméra astronomique ;
- une capture réelle ;
- un moteur d'astrométrie local ;
- un retour de solution astrométrique vers Android.

## Séquence fonctionnelle cible après le POC

Après validation de la faisabilité et les essais réels du 5 au 6 septembre 2026, la séquence d'initialisation visée est :

1. démarrage du serveur StellarPilot sur le Raspberry Pi ;
2. connexion depuis l'application Android StellarPilot ;
3. récupération de l'heure, de la localisation et de l'état INDI ;
4. lecture du type de monture et, si disponibles, de ses coordonnées AD/DEC via INDI ;
5. positionnement libre de la monture avec OnStep, une console externe, un mobile Wi-Fi ou un autre client compatible ;
6. capture et plate solving pour déterminer précisément le champ ;
7. sélection d'une étoile brillante ;
8. mise au point manuelle avec masque de Bahtinov ;
9. remise du capuchon et acquisition des darks ;
10. retrait du capuchon et démarrage des observations.

StellarPilot ne demande plus une orientation initiale obligatoire vers le nord céleste pour une monture EQ ou vers le zénith pour une monture AZ. Les coordonnées INDI servent de hint facultatif au solveur ; la solution astrométrique de l'image reste la référence réelle.

## Interface tablette

L'interface Android doit être prioritairement exploitable sur tablette en mode portrait :

- aperçu caméra utilisant toute la largeur disponible ;
- conservation du ratio de l'image ;
- zoom par pincement à deux doigts ;
- déplacement tactile de l'image zoomée ;
- réticule de centrage restant indépendant du zoom.

## Hors périmètre du premier POC

- focuser motorisé ;
- autofocus ;
- analyse automatique du motif de Bahtinov ;
- bibliothèque avancée de darks ;
- recentrage automatique complet ;
- live stacking ;
- interface Android définitive ;
- cloud obligatoire ;
- marketplace ;
- architecture de production figée.

## Principe matériel

Le POC et le premier MVP doivent rester indépendants d'un constructeur particulier lorsque la capacité nécessaire est accessible via INDI.