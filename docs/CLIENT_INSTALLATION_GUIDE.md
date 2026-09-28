# STORE — VIBE

## Installation privée Windows

Guide préparatoire : ne livrer qu’avec l’installateur final validé et son fichier SHA256.txt.

Windows 10/11 x64 compatible requis. Aucun Node, npm ou dépôt de développement n’est nécessaire. Les tablettes Windows compatibles sont concernées ; cet installateur ne fonctionne pas sur Android.

1. Obtenez l’installateur et SHA256.txt par le canal de confiance convenu avec VIBE.
2. Comparez son empreinte avec celle transmise, par exemple dans PowerShell : `Get-FileHash -Algorithm SHA256 -LiteralPath '.\STORE Setup 2.0.1-x64.exe'`.
3. L’installateur privé n’est pas signé : Windows peut afficher un éditeur inconnu ou SmartScreen. En cas d’origine ou d’empreinte incertaine, ne poursuivez pas et contactez votre interlocuteur VIBE.
4. Lancez l’installation puis STORE. Sur une machine sans profil STORE, suivez la configuration initiale, créez le propriétaire principal et configurez la boutique. Aucun mot de passe par défaut n’est fourni.
5. Redémarrez STORE et vérifiez votre configuration.

## Sauvegarder et mettre à jour

Accédez aux sauvegardes dans l’administration/les paramètres avec un compte autorisé. Conservez régulièrement une copie sur un support externe sécurisé, distinct de l’ordinateur. Une copie uniquement sur le même disque ne protège pas d’une panne du disque.

Avant une mise à jour, effectuez une sauvegarde vérifiée puis fermez STORE. Installez uniquement une version fournie par votre canal VIBE. La mise à jour doit conserver votre profil et migrer sa base si nécessaire ; ne supprimez pas les données pour mettre à jour. Si un écran de première configuration apparaît alors qu’une boutique existait, arrêtez-vous et demandez assistance avant de créer un nouveau compte.

La désinstallation normale est configurée pour conserver les données locales. Leur suppression complète est une opération distincte et destructive. L’emplacement effectif des données est consultable dans les diagnostics de STORE.

Aucune adresse de support n’est définie ici : utilisez le contact VIBE communiqué lors de la livraison.
