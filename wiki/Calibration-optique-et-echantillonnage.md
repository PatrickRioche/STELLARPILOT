# Calibration optique et échantillonnage

## Objectif

STELLARPILOT utilise les caractéristiques du télescope et de la caméra pour estimer l’échantillonnage théorique avant la première résolution astrométrique. Après une résolution réussie, la valeur mesurée par astrometry.net devient la référence empirique prioritaire du setup réellement utilisé.

Cette calibration sert à :

- accélérer les résolutions suivantes ;
- vérifier la cohérence du setup déclaré ;
- estimer la focale effective réelle ;
- calculer le champ photographié ;
- détecter un changement de réducteur, Barlow ou train optique ;
- adapter automatiquement les bornes de recherche d’astrometry.net.

## Formule de l’échantillonnage

L’échantillonnage théorique est :

```text
S [arcsec/pixel] = 206,265 × p [µm] / F [mm]
```

avec :

- `S` : échantillonnage en arcsecondes par pixel ;
- `p` : taille d’un pixel de la caméra en micromètres ;
- `F` : focale effective en millimètres.

Le diamètre du télescope n’intervient pas directement dans cette formule. Il intervient notamment dans le rapport focal, la résolution instrumentale et la quantité de lumière collectée.

## Référence Uranus-C du 2 septembre 2026

Configuration mesurée :

```text
Caméra      : Player One Uranus-C
Capteur     : 3856 × 2180 pixels
Pixel       : 2,9 µm
Binning     : 1 × 1
FITS        : 16 bits
```

Trois plate-solves indépendants réussis ont mesuré :

```text
1,21870215066 arcsec/pixel
1,21809445265 arcsec/pixel
1,21804919825 arcsec/pixel
```

La valeur de référence retenue est donc :

```text
pixel_scale_reference ≈ 1,2183 arcsec/pixel
```

La forte reproductibilité des trois mesures permet d’utiliser cette valeur pour resserrer la recherche astrométrique des captures suivantes lorsque le même setup est actif.

## Focale effective déduite

La formule précédente peut être inversée :

```text
F [mm] = 206,265 × p [µm] / S [arcsec/pixel]
```

Avec `p = 2,9 µm` et `S ≈ 1,2183 arcsec/pixel` :

```text
F effective ≈ 491 mm
```

Cette valeur est une focale effective mesurée par le ciel. Elle peut différer légèrement de la focale nominale à cause du train optique, des tolérances, d’un correcteur, d’un réducteur ou d’une Barlow.

## Champ photographié

Le champ peut être estimé à partir du nombre de pixels :

```text
champ_degrés = nombre_pixels × échantillonnage_arcsec_pixel / 3600
```

Pour le setup de référence :

```text
Largeur ≈ 3856 × 1,2183 / 3600 ≈ 1,305°
Hauteur ≈ 2180 × 1,2183 / 3600 ≈ 0,738°
```

Les solutions astrométriques réelles ont confirmé un champ voisin de :

```text
1,305° × 0,738°
```

## Rapport focal

Le rapport focal est :

```text
f/# = focale / diamètre
```

Le diamètre n’est donc pas nécessaire pour calculer directement l’échantillonnage, mais il reste nécessaire pour caractériser complètement le setup optique.

## Stratégie STELLARPILOT

### Avant le premier solve

Le logiciel utilise le setup déclaré :

```text
focale_nominale_mm
diametre_mm
pixel_size_um
sensor_width_px
sensor_height_px
binning
```

Il calcule une échelle théorique et un champ théorique afin d’amorcer astrometry.net.

### Après le premier solve

STELLARPILOT conserve :

```text
pixel_scale_measured
field_width_measured
field_height_measured
orientation
ra
dec
focal_length_effective
```

La mesure réelle devient prioritaire tant que la caméra, le binning et le train optique ne changent pas.

## Référence d’exposition astrométrique associée

La session du 2 septembre 2026 a montré plusieurs résolutions réussies avec une pose de 4 s. Cette valeur devient le point de départ empirique du setup pour un champ étoilé, mais elle ne doit pas être interprétée comme une valeur universelle.

Le logiciel devra distinguer :

- exposition insuffisante ;
- champ suffisamment structuré ;
- cible très lumineuse nécessitant une pose beaucoup plus courte ;
- échec causé par un mauvais hint de monture ;
- défaut de mise au point.

Un échec de solve ne doit donc jamais conduire automatiquement à augmenter l’exposition tant que la validité des coordonnées de monture n’a pas été vérifiée.

## Contrôle de cohérence futur

Exemple d’affichage cible :

```text
Focale déclarée       : 500 mm
Focale mesurée        : 491 mm
Écart                 : -1,8 %
Échantillonnage prévu : 1,196 "/px
Échantillonnage mesuré: 1,218 "/px

✓ Configuration optique cohérente
```

Un écart important devra déclencher une vérification de la focale déclarée, du binning, de la caméra ou des accessoires optiques.
