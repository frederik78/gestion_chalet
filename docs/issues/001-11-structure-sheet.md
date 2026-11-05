---
id: 001-11
titre: Structure du Sheet
prd: docs/prd/001-recus.md
besoin: F-01
statut: À faire
depend_de: [001-06]
criteres: [CA30, CA31, CA32, CA41, CA42, CA43]
branche: feat/001-11-structure-sheet
commits: []
revue:
---

## Objectif
La copie suit le bon fichier et le bon onglet, s'adapte aux colonnes du Sheet, et se bloque si la structure est inattendue.

## Périmètre
- Dans : Fichier et onglet selon l'année, année non configurée, colonnes repérées par leur en-tête, ajout de la colonne PHOTO
- Dans : Placement simple de la ligne dans une section existante, mise en forme reprise, blocage `STRUCTURE_SHEET`
- Hors : Création de sections et placement selon la date (001-07), reprise après panne (001-08).

## Critères d'acceptation
Les critères listés dans l'en-tête (`criteres`), tels que définis dans le PRD. Chaque critère doit être couvert par au moins un test
dont le nom ou le `@DisplayName` contient son identifiant (ex. `ca10_…`, `cs07_…`).

## Preuve attendue
Tests rouges puis verts avec un Sheet doublé en mémoire, `scripts/verify-clean.sh` à `VERIFY OK`.

## Notes
- 2026-11-04 : Issue issue du découpage de 001-06, approuvé par l'utilisateur. Q-07 reste à confirmer sur un fichier d'essai (section 13 du PRD).
- 2026-11-04 : Architecture (D-60) : le choix du fichier, de l'onglet et les blocages de structure passent par `RecuCopyService` et son adapter du Sheet.
