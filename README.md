# TOUCHDROP 0.8 — Wi-Fi / Golden Wind
Prototype Android 10+, services Google Play pour la découverte et la confirmation.

## Installer et utiliser
Installer la version 0.8 sur les deux téléphones ; les protocoles 0.1/0.2 ne sont pas compatibles.
1. Connecter les deux téléphones au même Wi-Fi. Autre solution : activer le point d’accès du destinataire dans Android puis y connecter l’expéditeur.
2. Si disponible, choisir une bande 5 GHz ou 6 GHz dans les réglages du point d’accès. L’application ne force aucune bande ni norme Wi-Fi. La portée, les interférences, le routeur, le stockage et le matériel limitent le débit.
3. Ouvrir TOUCHDROP sur les deux, activer Bluetooth pour la découverte, toucher Recevoir sur le destinataire.
4. Sélectionner les photos sur l’expéditeur, Envoyer, puis le destinataire trouvé.
5. Confirmer le même code sur les écrans ou rapprocher les antennes NFC au moment de la confirmation. Après confirmation NFC, le lot est automatiquement accepté si le destinataire a activé Recevoir.
6. Accepter le lot en mode code manuel. Garder les applications ouvertes.
7. L’animation plein écran suit le transfert ; 100 % attend la sauvegarde vérifiée dans Download/TouchDrop.

La version ne crée pas automatiquement le point d’accès. Il ne s’agit pas de Wi-Fi Direct P2P Android : c’est un transfert TCP sur le réseau Wi-Fi local ou le hotspot. Le réseau invité d’un routeur peut isoler les appareils ; utiliser alors un réseau non isolé ou le point d’accès d’un téléphone.
Sur certains constructeurs l’adresse du hotspot n’est pas exposée ; utiliser un routeur Wi-Fi commun si la détection de l’adresse échoue.

## Transport
Nearby n’envoie plus les originaux. Il transporte les métadonnées et les messages de contrôle sur une connexion vérifiée.
Le client TCP utilise explicitement la socketFactory d’un réseau déclaré TRANSPORT_WIFI par Android. Sans réseau Wi-Fi, le transfert est refusé : aucun repli des photos vers le Bluetooth ou le réseau mobile.
Le destinataire ouvre un port temporaire et communique les paramètres dans la session Nearby vérifiée.
Les originaux sont transportés en blocs de 256 Kio, sans Base64 et sans aller-retour applicatif par bloc. Tampons de 1 Mio, chiffrement AES-256-GCM indépendant par bloc, clé aléatoire par fichier, nonce à compteur avec préfixe aléatoire et données associées liées au lot, au fichier et au numéro de bloc.
La réception contrôle taille, intégrité GCM, SHA-256 et format avant publication MediaStore. Un fichier partiel reste dans le cache privé et est supprimé en cas d’erreur. Aucun service cloud TOUCHDROP n’est utilisé. La permission Android INTERNET est nécessaire aux sockets, même sur un réseau local.
Le débit est une moyenne applicative du lot, pas le débit radio négocié. Côté expéditeur, les octets peuvent encore être en tampon réseau. La confirmation finale vient du destinataire après sauvegarde. Aucune vitesse minimale ou utilisation « maximale » du matériel n’est garantie.

## Animation
Photo sur une scène plein écran ; 5 500 particules colorées/dorées, dissolution et vent à l’envoi, reconstruction à la réception. Rafraîchissement demandé à la cadence de l’écran, fluidité dépendante du GPU/appareil.
Progression des particules pilotée par les octets traités ; les mises à jour de mesure sont limitées à environ 12/s pour préserver le débit et interpolées à l’affichage.
Le destinataire reçoit d’abord une miniature pour l’effet, puis affiche l’original vérifié à la fin. L’original sauvegardé n’est pas recompressé.
Les grains sont un effet graphique : ce ne sont pas les octets physiques qui traversent visuellement les deux écrans.

## Limites et confidentialité
50 fichiers par lot, 1 Gio par fichier, 2 Gio par lot. Fichiers non vides : images, vidéos, PDF, audio et autres documents. Aucun fichier reçu n’est exécuté ni ouvert automatiquement.
Premier plan obligatoire ; pas de reprise après interruption. Les photos déjà publiées restent dans la galerie.
NFC/HCE exige matériel compatible, NFC actif, écrans déverrouillés. Choisir d’abord le destinataire : la proximité radio ne détecte pas un contact exact. Le code manuel reste disponible.
Les fichiers temporaires sont nettoyés à l’arrêt ou au prochain lancement. Les traitements propres à Google Play Services restent indépendants de l’application.
Version de test, sans qualification commerciale ni audit indépendant.

## Compiler
JDK 17, Gradle 8.11.1, Android SDK 35, AGP 8.9.1, Kotlin 2.1.10, Nearby 19.3.0.
Commande : gradle testDebugUnitTest lintDebug assembleDebug
APK : app/build/outputs/apk/debug/app-debug.apk

## Qualification sur téléphones
- Comparer un JPEG de 20 Mio et un lot de 10 images sur le même routeur puis sur un hotspot 5 GHz.
- Noter modèles, bande configurée, durée complète, Mo/s et état galerie ; comparer SHA-256 des originaux.
- Tester Wi-Fi absent, réseau invité isolé, NFC absent/refusé, clé incorrecte, coupure Wi-Fi, annulation et stockage plein.
- La compilation, les tests cryptographiques et un test TCP local ne remplacent pas ces essais physiques.

Concept : LABED ABDENOUR.

## Introduction 0.4
Au lancement de l’activité : scène plein écran durant 7 000 ms, grains dorés assemblant TOUCHDROP, puis dispersion et ouverture automatique de l’accueil.
Texte demandé conservé littéralement, sur deux lignes : BNET COMPANY / ENGINEERING BY LABED ABDNOUR.
Le minuteur part au lancement de l’activité ; un retour au premier plan ne relance pas l’introduction si l’activité existe encore.
Transport identique à la version 0.3 : même Wi-Fi ou point d’accès préalablement configuré.



## Version 0.5 — tous fichiers
Installer 0.5 sur les deux téléphones (protocole files.v5).
Le sélecteur Android permet de sélectionner plusieurs fichiers de tous types, y compris vidéos, PDF, audio et archives.
Les originaux sont copiés sans conversion. Le type MIME est traité comme métadonnée, pas comme preuve de sécurité. Aucune extraction d’archive ni exécution automatique.
Tous les fichiers sont enregistrés via MediaStore Downloads dans Téléchargements/TouchDrop, avec leur nom nettoyé préfixé par un identifiant anticollision.
La carte animée affiche le type et le nom lorsque le fichier n’a pas de miniature décodable.
La réception contrôle toujours les blocs AES-GCM et l’empreinte SHA-256. Le stockage libre doit couvrir le lot et une copie temporaire du plus grand fichier.
L’introduction de 7 secondes comporte désormais des anneaux lumineux, un halo, des étoiles dorées et un logo métallique formé par les grains. Texte : BNET COMPANY / ENGINEERING BY LABED ABDNOUR.
Signature identique à 0.4 : mise à jour directe depuis 0.4. Depuis 0.3 ou plus ancien, désinstaller l’ancienne version d’abord.
Les essais physiques de vidéos de 1 Gio, stockage plein, interruptions et rendu graphique restent à effectuer.

## Version 0.6 — Gold Edition
Interface d’accueil restructurée en trois cartes : sélection, connexion, transfert. Fond noir chaud, titres ivoire, contours et boutons dorés, progression or et boutons inactifs atténués.
Icône de lancement adaptative : monogramme TD doré sur fond noir.
Introduction : apparition de l’accueil en fondu croisé sur les dernières 1,8 secondes de la séquence de 7 secondes, avec un léger agrandissement de 96 % à 100 %. La durée des animations respecte l’échelle d’animation du système Android.
Transport et limites identiques à 0.5. Compatible avec le protocole 0.5. Signature identique à 0.4/0.5 : mise à jour directe.
Rendu et fluidité sur les appareils physiques restent à vérifier.

## Version 0.7 — Golden Letters
Les particules dorées entrent depuis des positions aléatoires et composent TOUCHDROP lettre après lettre. Le monogramme BNET géométrique se compose ensuite, puis les grains se dispersent avec des trajectoires de vent indépendantes. Signature corrigée : ENGINEERING BY LABED ABDNOUR. Introduction nominale de 7 secondes avec fondu croisé final de 1,8 seconde. Masque de particules limité à 640 pixels de largeur pour limiter le coût de préparation. Le monogramme apparaît également en pied d’accueil. Transport de fichiers inchangé.

## Version 0.8 — Slow Golden Sand
TOUCHDROP centré verticalement, largeur réduite à 56 % de l’écran. Trois grains par échantillon, tailles et éclats variables pour une impression de volume. Formation individuelle étalée sur 2,8 secondes, dispersion sur 3,3 secondes avec trajectoires courtes et lentes. Durée nominale totale 12 secondes, fondu final 1,8 seconde. Animation procédurale stylisée, pas une simulation physique du sable. Transfert de fichiers inchangé.

## Version 0.9 — GPU Sand Intro
Le fond de l’introduction utilise désormais un `RuntimeShader` AGSL sur Android 13+ (API 33) pour produire un champ de grains dorés animé par le GPU. Un rendu Canvas de secours est conservé pour les appareils plus anciens. La plume et la typographie restent dessinées localement afin que l’introduction fonctionne sans téléchargement d’asset ni connexion réseau. Rive pourra remplacer la plume lorsque le fichier `.riv` final sera validé ; Lottie reste une alternative pour une animation précomposée After Effects. Le transfert de fichiers n’est pas modifié.

## Version 0.9.1 — Aperçus premium
Les fichiers non décodables en image (vidéo, audio, PDF et documents) reçoivent une carte d’aperçu dédiée : icône de lecture ou document, couleurs par type, nom nettoyé et poussière dorée décorative. La scène de transfert utilise 8 200 grains avec variation de profondeur, halos et traînées de vent. L’effet reste une visualisation : les octets passent toujours par le Wi-Fi chiffré.
