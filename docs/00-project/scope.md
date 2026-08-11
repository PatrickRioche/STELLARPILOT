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
5. orientation initiale approximative : nord céleste pour EQ, zénith pour AZ ;
6. capture et plate solving pour déterminer précisément le champ ;
7. sélection d'une étoile brillante ;
8. mise au point manuelle avec masque de Bahtinov ;
9. remise du capuchon et acquisition des darks ;
10. retrait du capuchon et démarrage des observations.

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
