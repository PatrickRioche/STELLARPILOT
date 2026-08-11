# Principes d'ingénierie

- **Specification first** : on décrit avant de coder.
- **Feasibility first** : on prouve les hypothèses critiques.
- **Vendor neutral** : aucune marque ne doit devenir une dépendance conceptuelle.
- **INDI first** : INDI est la couche matérielle de référence tant que sa faisabilité est validée.
- **Local first** : le contrôle local doit fonctionner sans service cloud obligatoire.
- **One release version** : une release produit = un numéro commun à toutes les plateformes.
- **Evidence based** : chaque Go/No-Go doit citer une preuve reproductible.
- **Rollback safe** : les scripts de modification doivent pré-vérifier leurs ancres et éviter les écritures partielles.
