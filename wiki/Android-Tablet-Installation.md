# Installation sur tablette Android

La version actuelle utilise `minSdk = 26`, soit Android 8.0 ou supérieur.

## Pré-requis ADB

Activer les options développeur et le débogage USB, puis accepter la clé RSA du PC.

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-toolsdb.exe" devices
```

La tablette doit apparaître avec l’état `device`.

## Variante matériel réel

Installer `deviceDebug`, configurée pour `http://10.42.0.1:8000/`.

## Méthode A — APK de la release

```powershell
$dir = "$env:LOCALAPPDATA\StellarPiloteleases0.5.0-poc"
New-Item -ItemType Directory -Path $dir -Force | Out-Null
gh release download v0.5.0-poc --repo PatrickRioche/STELLARPILOT `
  --pattern "app-device-debug.apk" --dir $dir
& "$env:LOCALAPPDATA\Android\Sdk\platform-toolsdb.exe" install -r `
  "$dirpp-device-debug.apk"
```

## Méthode B — Build local

```powershell
cd android
.\gradlew.bat assembleDeviceDebug --no-daemon
cd ..
```

APK : `%LOCALAPPDATA%\StellarPilotndroid-buildpp\outputspk\device\debugpp-device-debug.apk`.

Installation :

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-toolsdb.exe" install -r `
"$env:LOCALAPPDATA\StellarPilotndroid-buildpp\outputspk\device\debugpp-device-debug.apk"
```

Résultat attendu : `Success`.

## Connexion au Pi

En utilisation autonome : démarrer le Pi, connecter la tablette au hotspot StellarPilot, puis lancer l’application. Le backend `device` utilise `10.42.0.1:8000`.

Sur le LAN de développement, le Pi testé est accessible à `192.168.1.46`, mais cette adresse n’est pas une constante produit.

## Script fourni

```powershell
.\scripts\install\install-tablet.ps1
```
