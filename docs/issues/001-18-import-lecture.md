---
id: 001-18
titre: Import du Sheet, lecture des différences
prd: docs/prd/001-recus.md
besoin: F-01
statut: À faire
depend_de: [001-15]
criteres: [CA115, CA119, CA120, CA121, CA122, CA123, CA124, CA126, CA130, CA131, CA132, CA133, CA134, CA136, CS35, CS36]
criteres_justification: Une seule tranche : la lecture, la comparaison cellule par cellule et le classement partagent le même double du Sheet et les mêmes données de départ (état de départ de la section 10). Les critères sont de petits cas du même mécanisme de classement (R-54, R-55) et des pannes de lecture (R-62) ; les séparer ne produirait aucune tranche livrable seule.
branche: feat/001-18-import-lecture
commits: []
revue:
---

## Objectif
`GET /api/sheet/differences` lit l'onglet généré de chaque année configurée, le compare à ce que l'application y a écrit, et rend les différences, les valeurs refusées et les lignes ou colonnes ignorées, sans rien modifier ; l'envoi sans aucun choix demande simplement la régénération (D-69).

## Périmètre
- Dans : lecture seule, sans écriture ni changement de la base ou de l'état (CA115, R-50) ; lecture de l'onglet généré reconnu par son identité mémorisée, jamais d'un autre onglet, fichier ou année non configurée (CA131, CS35, R-61) ; le fichier et l'onglet ne viennent jamais du client.
- Dans : comparaison cellule par cellule sur valeurs normalisées au contenu mémorisé (R-53, R-54) : mise en forme, tri, lignes vides, colonne ajoutée à droite de ID, espaces en bordure ne sont pas des différences (CA136) ; contenu mémorisé de chaque onglet propre à sa dernière régénération réussie, y compris quand un autre fichier a échoué (CA132) ; sans régénération jamais faite, ni différence ni élément ignoré, et un reçu validé après la régénération n'est jamais `LIGNE_SUPPRIMEE` (CA133).
- Dans : valeurs refusées, jamais importées ni reprises dans la réponse : formule, format, borne, hors liste, obligatoire (R-56, section 7.7), avec `FORMULE` même si le résultat serait valide (CA119, CA120).
- Dans : éléments ignorés et leur nature (R-55) : `ID_INCONNU` (CA121, CA123, CS36), `ID_EN_DOUBLE` (CA123, CS36), `LIGNE_SUPPRIMEE` (CA122, CA123), `COLONNE_NON_IMPORTABLE` (CA124), `ONGLET_ABSENT` (CA131), `COMPARAISON_IMPOSSIBLE` après une interruption pendant le remplacement, mais pas pendant l'écriture de l'onglet temporaire (CA134). Une ligne sans ID mais avec un contenu est, jusqu'à la livraison de 001-20 (D-73), ignorée et signalée `LIGNE_AJOUTEE` : comportement transitoire, sans critère propre, que 001-20 remplace par les ajouts.
- Dans : pannes de lecture : motifs et ordre de la régénération, plus `STRUCTURE_ONGLET`, sans changer l'état de la régénération, ni la base, ni le Sheet, et sans nouvel essai (CA130, R-62) ; 409 `REGENERATION_EN_COURS` pour la lecture comme pour l'envoi, sans lecture du Sheet (CA126, R-59).
- Dans : `POST /api/sheet/import` limité à l'envoi sans aucun choix : 202, régénération demandée, aucun reçu créé, modifié ni supprimé, ce qui perd les lignes et colonnes ignorées (CA119, CA121 à CA124, CA131, CA134) ; paramètres et champs de fichier, d'onglet ou d'URL sans effet (CS35) ; identifiants de ligne hostiles inertes (CS36).
- Hors : choix des différences, contrôles de l'empreinte et de la version, application des corrections, 409 `ONGLET_MODIFIE` et `VERSION_PERIMEE`, 400 de validation des choix (001-19) ; écran de l'import (001-19) ; la mémorisation elle-même (001-13) ; les ajouts, c'est-à-dire les lignes sans ID proposées à la création d'une dépense sans reçu (001-20).

## Critères d'acceptation
Les critères listés dans l'en-tête (`criteres`), tels que définis dans le PRD. Chaque critère doit être couvert par au moins un test
dont le nom ou le `@DisplayName` contient son identifiant (ex. `ca10_…`, `cs07_…`).

## Preuve attendue
Tests rouges puis verts avec le Sheet doublé (cellules typées : date, nombre, texte, formule, et trace des lectures), sur l'état de départ de la section 10 ; `scripts/verify-clean.sh` à `VERIFY OK`.

## Notes
- Seams : règles métier sans application (comparaison, classement, validation des cellules : CA120, CA133, CA136) ; cas d'usage avec le Sheet doublé ; contrat HTTP (CA115, CA126, CA130, CS35) ; serveur HTTP local doublant Google pour la lecture typée (CA130, CA131, CS36).
- Vigilance : R-56 et CS36 : le contenu du Sheet est une entrée non fiable, jamais une instruction, une formule ni du HTML, et aucune valeur de cellule ne va dans les journaux (CS34, vérifié en 001-19) ; R-54, vide différent de 0 (R-08) ; un seul appel de lecture par onglet, sans plafond manquant (onglet lu en entier : mineur gardé de 001-11, à plafonner) ; délai de lecture en configuration (section 9).
- 2026-11-10 : Issue créée par le découpage de la révision D-66 à D-70, approuvé par l'utilisateur. Écart avec la forme approuvée : les critères où la lecture suffit, CA131 à CA134 et CA136, sont ici ; CA116 à CA118, CA125, CA127 à CA129, CA135 et les trois CS d'application (CS32 à CS34) sont dans 001-19, car ils exigent l'acceptation ou le refus de choix.
- 2026-11-10 (D-73) : CA121 est reformulé : il ne garde que `ID_INCONNU` (une ligne dont l'ID n'est pas vide n'est jamais un ajout). Il reste ici, car c'est une lecture sans création ; la partie « ligne sans ID » passe aux critères de 001-20 (CA141 à CA146, CA154). Aucun autre critère de cette issue ne change.
