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

Après validation de la faisabilité, la séquence d'initialisation visée est :

1. démarrage du serveur StellarPilot sur le Raspberry Pi ;
2. connexion depuis l'application Android StellarPilot ;
3. récupération de l'heure et de la localisation ;
4. identification/sélection du type de monture AZ ou EQ ;
5. positionnement libre de la monture avec OnStep, une application mobile ou une autre console de commande ;
6. lecture facultative des coordonnées AD/DEC publiées par INDI afin d'accélérer la recherche astrométrique ;
7. capture et plate solving pour déterminer précisément le champ réel, avec blind solve obligatoire en secours ;
8. sélection d'une étoile brillante ;
9. mise au point avec masque de Bahtinov ;
10. remise du capuchon et acquisition des darks ;
11. retrait du capuchon et démarrage des observations.

Aucune orientation initiale imposée (nord céleste, zénith, N/NE/E/...) n'est requise. Les coordonnées de monture ne sont qu'un indice : la solution astrométrique obtenue à partir de l'image reste la référence.

## Paramètres optiques validés sur le setup de test

Les essais terrain du 5 septembre 2026 et leur retest hors ligne ont mesuré une échelle moyenne proche de **1,2172 arcsec/pixel**, cohérente avec la valeur théorique d'environ **1,218 arcsec/pixel**. Le solveur utilise donc en première intention une fenêtre étroite autour de cette valeur, puis élargit la recherche si nécessaire.

## Hors périmètre du premier POC

- focuser motorisé ;
- autofocus ;
- bibliothèque avancée de darks ;
- cloud obligatoire ;
- marketplace ;
- architecture de production figée.

Certaines fonctions initialement classées hors POC (recentrage, live stacking, interface Android avancée, aide Bahtinov) ont depuis commencé à être intégrées au prototype v0.6 ; elles ne doivent plus être considérées comme absentes du code actuel.

## Principe matériel

Le POC et le premier MVP doivent rester indépendants d'un constructeur particulier lorsque la capacité nécessaire est accessible via INDI.

## Principe de distribution serveur

Le Raspberry Pi cible n'est pas un poste de développement et ne contient pas de dépôt Git. Les mises à jour du serveur StellarPilot sont produites depuis le poste de développement sous forme de kit de distribution puis déployées sur le Pi en préservant les données persistantes et l'environnement propre à la machine.
