# PRECHECK

Toute automatisation qui modifie plusieurs fichiers doit :

1. vérifier l'existence des fichiers attendus ;
2. vérifier toutes les ancres/contextes avant la première écriture ;
3. refuser l'opération si une hypothèse est fausse ;
4. écrire de manière atomique lorsque possible ;
5. prévoir rollback ou restauration ;
6. afficher un résumé des changements prévus.

Cette règle s'applique notamment aux scripts de migration, versionnement et release.
