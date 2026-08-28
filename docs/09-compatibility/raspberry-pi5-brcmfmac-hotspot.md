# Raspberry Pi 5 — hotspot Broadcom `brcmfmac`

## Objet

Cette note documente le problème de stabilité du point d'accès Wi-Fi intégré du Raspberry Pi 5 observé avec StellarPilot, ainsi que la procédure de mise à jour, de diagnostic, de correction et de vérification validée le 28 août 2026.

Le symptôme principal était trompeur : les captures caméra et les appels FastAPI réussissaient côté Raspberry Pi, mais les réponses HTTP n'arrivaient pas toujours jusqu'à la tablette Android. Cela provoquait ensuite des timeouts de preview, de `/status`, de WebSocket et des faux messages d'erreur d'astrométrie.

## Configuration ayant présenté le problème

Configuration validée pendant le diagnostic :

- Raspberry Pi 5 ;
- Debian GNU/Linux 13.6 `trixie` ;
- noyau `6.18.39+rpt-rpi-2712` ;
- pilote Wi-Fi `brcmfmac` ;
- chipset détecté comme `BCM4345/6` ;
- firmware chargé : branche `7.45.265` ;
- paquet `firmware-brcm80211` : `1:20260519-1~bpo13+1+rpt1` ;
- point d'accès NetworkManager sur `wlan0` ;
- tablette Android connectée au hotspot StellarPilot/astroberry ;
- serveur StellarPilot sur `10.42.0.1:8000`.

## Symptômes observés

Les symptômes suivants peuvent indiquer ce problème Wi-Fi plutôt qu'un défaut de caméra ou d'astrométrie :

- `POST /camera/capture` retourne `200 OK` côté FastAPI, mais Android ne reçoit pas `CAPTURE HTTP 200` ;
- `GET /camera/preview.jpg` retourne `200 OK` côté FastAPI, mais Android ne reçoit pas les en-têtes HTTP ;
- un petit endpoint tel que `/health` peut fonctionner alors qu'une preview JPEG ne revient pas ;
- après dégradation du lien, même `/status` peut finir en timeout ;
- M103 fonctionne une première fois puis échoue lors d'un second essai ;
- plusieurs captures successives deviennent aléatoires ;
- les logs Android montrent `SocketTimeoutException` ou `InterruptedIOException: timeout` ;
- `iw dev wlan0 station dump` montre une augmentation rapide de `tx failed`.

Exemples mesurés pendant l'incident :

```text
tx packets: 1286
tx failed: 201
```

soit environ 15,6 % d'échecs d'émission.

Un autre état fortement dégradé a donné :

```text
tx packets: 319
tx failed: 192
```

Le serveur FastAPI continuait pourtant à journaliser des réponses `200 OK`.

## Diagnostic rapide

### 1. Vérifier que le problème est réseau

Sur le Raspberry Pi :

```bash
sudo journalctl -u stellarpilot-server \
  --since "10 minutes ago" \
  --no-pager | grep -E "camera/capture|camera/preview|status|ERROR|Exception"
```

Si FastAPI journalise :

```text
POST /camera/capture ... 200 OK
GET /camera/preview.jpg ... 200 OK
```

mais qu'Android ne journalise pas :

```text
CAPTURE HTTP 200
PREVIEW HTTP 200
```

le problème se situe après FastAPI, dans le transport réseau vers la tablette.

### 2. Vérifier les erreurs radio

```bash
sudo iw dev wlan0 station dump
```

Surveiller en priorité :

- `tx packets` ;
- `tx failed` ;
- `tx bitrate` ;
- `rx bitrate` ;
- `inactive time`.

`tx failed` doit rester proche de zéro et ne doit pas augmenter rapidement pendant quelques captures et transferts JPEG.

## Mise à jour du firmware Broadcom

Mettre d'abord les index APT à jour :

```bash
sudo apt update
```

Vérifier la version installée et la version candidate :

```bash
apt-cache policy firmware-brcm80211
```

Mettre à jour le paquet si nécessaire :

```bash
sudo apt install --only-upgrade firmware-brcm80211
```

La version Raspberry Pi utilisée pendant la validation était :

```text
1:20260519-1~bpo13+1+rpt1
```

## Vérifier les paramètres de stabilité `brcmfmac`

Le paquet Raspberry Pi récent installe :

```text
/usr/lib/modprobe.d/rpi-brcmfmac.conf
```

Vérifier :

```bash
cat /usr/lib/modprobe.d/rpi-brcmfmac.conf
```

Résultat attendu :

```text
options brcmfmac roamoff=1 feature_disable=0x282000
```

Vérifier que `roamoff` est effectivement chargé :

```bash
sudo cat /sys/module/brcmfmac/parameters/roamoff
```

Résultat attendu :

```text
1
```

Vérifier les paramètres supportés par le module :

```bash
modinfo -p brcmfmac
```

`feature_disable` doit apparaître dans la liste des paramètres supportés.

Référence Raspberry Pi : commit `348a96721f568451a38286bedaf1f2daa4681c78`, « Add brcmfmac module parameters for stability » :

https://github.com/RPi-Distro/firmware-nonfree/commit/348a96721f568451a38286bedaf1f2daa4681c78

## Vérifier le domaine réglementaire

Vérifier le pays configuré :

```bash
sudo raspi-config nonint get_wifi_country
```

Résultat attendu en France :

```text
FR
```

Vérifier le paramètre noyau :

```bash
grep -o 'cfg80211.ieee80211_regdom=[A-Z][A-Z]' /boot/firmware/cmdline.txt || true
```

Résultat attendu :

```text
cfg80211.ieee80211_regdom=FR
```

Puis :

```bash
iw reg get
```

Pendant le diagnostic, le domaine global était correctement réglé sur `FR`, tandis que `phy#0` affichait `country 99: DFS-UNSET`. Ce seul affichage ne suffit pas à diagnostiquer la panne ; le compteur `tx failed` reste l'indicateur opérationnel principal.

## Profil hotspot connu comme fonctionnel pendant la validation

La configuration suivante a été utilisée pendant la validation finale :

- mode AP ;
- bande 5 GHz ;
- canal 36 ;
- power save désactivé ;
- MTU 1400.

Vérifier le profil actif :

```bash
HOTSPOT="$(nmcli -g GENERAL.CONNECTION device show wlan0)"

nmcli -f \
802-11-wireless.mode,802-11-wireless.band,802-11-wireless.channel,802-11-wireless.mtu,802-11-wireless.powersave \
connection show "$HOTSPOT"
```

Pour réappliquer ce profil de validation :

```bash
sudo nmcli connection modify "$HOTSPOT" \
  802-11-wireless.band a \
  802-11-wireless.channel 36 \
  802-11-wireless.powersave 2 \
  802-11-wireless.mtu 1400

sudo iw dev wlan0 set power_save off
```

Vérifier :

```bash
iw dev wlan0 info
iw dev wlan0 get power_save
```

Le MTU 1400 et le canal 36 font partie de la configuration connue comme fonctionnelle lors de ce diagnostic. Ils ne doivent pas être considérés comme la cause unique ni comme obligatoires sur toutes les installations.

## Correctif validé : firmware BCM43455 `minimal`

Le paquet Raspberry Pi fournit deux variantes du firmware BCM43455 : `standard` et `minimal`.

Afficher la sélection actuelle :

```bash
sudo update-alternatives --display cyfmac43455-sdio.bin
```

Pendant l'incident, la variante active était :

```text
cyfmac43455-sdio-standard.bin
```

Basculer vers la variante `minimal` :

```bash
sudo update-alternatives --set \
  cyfmac43455-sdio.bin \
  /lib/firmware/cypress/cyfmac43455-sdio-minimal.bin
```

Vérifier :

```bash
readlink -f /lib/firmware/cypress/cyfmac43455-sdio.bin
```

Résultat attendu :

```text
/usr/lib/firmware/cypress/cyfmac43455-sdio-minimal.bin
```

## Power-cycle obligatoire après changement de firmware

Après le changement de variante, effectuer un arrêt complet :

```bash
sudo poweroff
```

Puis :

1. couper physiquement l'alimentation du Raspberry Pi ;
2. attendre quelques secondes ;
3. remettre sous tension ;
4. reconnecter la tablette au hotspot.

Un simple redémarrage logiciel ne garantit pas que le chipset Wi-Fi a complètement quitté son état précédent.

## Vérification après redémarrage

Vérifier immédiatement :

```bash
iw dev wlan0 info
iw dev wlan0 get power_save
sudo iw dev wlan0 station dump
```

Juste après le passage au firmware `minimal` et le power-cycle, la mesure obtenue était :

```text
tx packets: 167
tx failed: 0
tx bitrate: 72.2 MBit/s
rx bitrate: 96.1 MBit/s
```

C'est très différent des dizaines ou centaines de `tx failed` observés avec le firmware standard dans l'état dégradé.

## Test fonctionnel StellarPilot

Effacer les logs Android :

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb logcat -c
```

Puis tester dans cet ordre :

1. M103 une première fois ;
2. M103 une deuxième fois ;
3. capture à `1 ms` ;
4. capture à `23 ms` ;
5. affichage de la preview ;
6. lancement de l'astrométrie.

Récupérer ensuite les logs :

```powershell
& $adb logcat -d |
    Select-String "StellarPreview|StellarPilotConnection|SocketTimeoutException|AndroidRuntime"
```

Le chemin nominal doit contenir des lignes du type :

```text
CAPTURE ...
CAPTURE HTTP 200
PREVIEW ATTEMPT 1/3
PREVIEW HTTP 200
PREVIEW BYTES ...
SOLVE ...
```

Après passage au firmware `minimal` et power-cycle, les tests M103 successifs et les captures à `1 ms` et `23 ms` ont fonctionné correctement.

## Vérification serveur si le problème réapparaît

Sur le Pi :

```bash
sudo journalctl -u stellarpilot-server \
  --since "10 minutes ago" \
  --no-pager | grep -E "camera/capture|camera/preview|status|solve|ERROR|Exception"
```

Puis :

```bash
sudo iw dev wlan0 station dump
```

Règle pratique :

- serveur `200 OK` + Android timeout + `tx failed` qui augmente rapidement => problème Wi-Fi/firmware ;
- image reçue + log `SOLVE` + échec solve => problème d'astrométrie réel ;
- ne pas modifier la caméra ou le solveur tant que la chaîne HTTP n'est pas fiable.

## Retour arrière vers le firmware standard

Si une future mise à jour rend le firmware `minimal` inutile ou problématique :

```bash
sudo update-alternatives --set \
  cyfmac43455-sdio.bin \
  /lib/firmware/cypress/cyfmac43455-sdio-standard.bin
```

Vérifier :

```bash
readlink -f /lib/firmware/cypress/cyfmac43455-sdio.bin
```

Puis refaire obligatoirement :

```bash
sudo poweroff
```

et effectuer un power-cycle physique.

## Contrôle après chaque mise à jour système

Après une mise à jour de :

- `firmware-brcm80211` ;
- noyau Raspberry Pi ;
- NetworkManager ;
- firmware système Raspberry Pi ;

vérifier au minimum :

```bash
apt-cache policy firmware-brcm80211
uname -a
cat /usr/lib/modprobe.d/rpi-brcmfmac.conf
sudo update-alternatives --display cyfmac43455-sdio.bin
readlink -f /lib/firmware/cypress/cyfmac43455-sdio.bin
iw dev wlan0 get power_save
sudo iw dev wlan0 station dump
```

Puis refaire le test fonctionnel M103 x2 + capture 1 ms + capture 23 ms.

## Conclusion

Le problème observé le 28 août 2026 ne provenait ni de la caméra Player One, ni de FastAPI, ni du JPEG, ni d'OkHttp, ni du solveur astrométrique. Les journaux serveur montraient des réponses HTTP `200 OK` tandis que la tablette expirait en lecture, et `iw` révélait un nombre anormalement élevé de transmissions échouées.

La combinaison validée comme fonctionnelle est :

- paquet Raspberry Pi récent `firmware-brcm80211` ;
- paramètres `brcmfmac` de stabilité présents ;
- réglementation `FR` correctement configurée ;
- power save désactivé ;
- firmware BCM43455 sélectionné sur la variante `minimal` ;
- power-cycle physique après le changement ;
- contrôle de `tx failed` ;
- validation M103 x2 et captures 1 ms / 23 ms.
