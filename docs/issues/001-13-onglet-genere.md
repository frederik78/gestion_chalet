---
id: 001-13
titre: Onglet généré du Sheet
prd: docs/prd/001-recus.md
besoin: F-01
statut: À faire
depend_de: [001-11]
criteres: [CA31, CA32, CA85, CA86, CA87, CA88, CA89, CA90, CA96, CS16]
branche: feat/001-13-onglet-genere
commits: []
revue:
---

## Objectif
Une régénération demandée par `POST /api/sheet/regeneration` écrit, dans le fichier de chaque année configurée, un onglet généré qui reproduit les reçus de la base, par un remplacement tout ou rien, et mémorise ce qu'elle a écrit (D-66, D-68, D-69).

## Périmètre
- Dans : fichier choisi selon l'année du reçu (D-18) ; seul l'onglet généré reçoit des écritures, aucun autre onglet n'est touché, aucun autre fichier n'est ouvert (CA31, CA87, R-41) ; année sans fichier configuré : reçus ignorés, année signalée dans l'état, pas d'échec (CA32, Q-22).
- Dans : contenu et tri (R-39, section 7.4) : ligne d'en-têtes DATE, BOUTIQUE, OBJET, TYPE, MONTANT HT, TPS, TVQ, MONTANT TOTAL, NOTES, PHOTO, ID dans cet ordre fixe, une ligne par reçu de l'année, trié par date puis ordre de validation, sans section de mois, sans total, sans ligne vide, valeurs littérales, DATE en vraie date, montants en nombres, colonne ID en texte littéral et grisée (CA85, CA86, CS16, R-51).
- Dans : remplacement sûr (R-40) : onglet temporaire rempli en entier puis remplacement de l'onglet généré, qui garde la même identité ; en cas de panne, l'onglet généré antérieur est intact, l'onglet temporaire est retiré et l'état est `ECHEC` `GOOGLE_INDISPONIBLE` sans changer `derniereReussiteLe` (CA88, CA89, CA90). Première régénération : création de l'onglet. Régénération directe, sans import, qui réécrit tout (CA96).
- Dans : mémorisation (R-53) : à la réussite du remplacement d'un onglet, l'application enregistre ligne par ligne l'identifiant du reçu et le contenu (ou l'empreinte) de chaque cellule DATE à PHOTO qu'elle a écrit, en remplaçant le précédent contenu du même onglet, jamais avant la réussite ; elle note aussi, avant de demander le remplacement, qu'il est en cours, pour que l'issue inconnue d'un arrêt soit détectable (utilisé par 001-15 et 001-18). Rien n'est exposé par l'API ; aucun numéro d'inscription (R-25).
- Dans : le minimum d'état persisté que CA32 et CA89 observent (états, motif, dates, `anneesNonConfigurees`, lisible par `GET /api/sheet/etat`) ; la machine d'états complète, la concurrence et le bouton sont dans 001-15.
- Hors : contenu détaillé de NOTES, PHOTO et des numéros (001-14), bouton, concurrence, motifs et priorité, redémarrage (001-15), import (001-18, 001-19), retrait de l'ancien code d'insertion (001-17).

## Critères d'acceptation
Les critères listés dans l'en-tête (`criteres`), tels que définis dans le PRD. Chaque critère doit être couvert par au moins un test
dont le nom ou le `@DisplayName` contient son identifiant (ex. `ca10_…`, `cs07_…`).

## Preuve attendue
Tests rouges puis verts avec le Sheet doublé en mémoire (onglets, cellules typées, identifiants d'onglet, trace des requêtes), `scripts/verify-clean.sh` à `VERIFY OK`.

## Notes
- Seam : Google Sheets (interface `client/`) doublé, y compris par un serveur HTTP local pour le format exact des requêtes et le littéral d'écriture (section 12). Drive n'est jamais appelé par une régénération (R-45).
- Vigilance : R-38 à R-41, R-46 (fichiers traités l'un après l'autre, un onglet remplacé en entier ou pas du tout), R-53 ; CS16 : injection de formule (`=`, `+`, `-`, `@`) écrite en texte littéral, dans l'onglet temporaire comme dans l'onglet généré. Que l'identité de l'onglet soit conservée par le remplacement reste à confirmer à l'essai réel (section 13, CA90).
- 2026-11-10 : Issue créée par le découpage de la révision D-66 à D-70, approuvé par l'utilisateur. CA31 et CA32 viennent de 001-11, CS16 de 001-06. Branche à partir de `develop` (après fusion de `docs/sheet-genere`).
