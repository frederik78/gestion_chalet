---
id: 001-11
titre: Structure du Sheet
prd: docs/prd/001-recus.md
besoin: F-01
statut: Fait
depend_de: [001-06]
criteres: [CA30, CA41, CA42, CA43]
branche: feat/001-11-structure-sheet
commits: [703e678..f69d3f3]
revue: docs/reviews/001-11.md
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
- 2026-11-06 : Fait. `FIN-ISSUE OK 001-11 b043975 : 1011 tests, 0 échec, 0 ignoré` (tests Chrome compris), lancé par la session. Relecture n° 1 `APPROUVÉ`, 8 mutations tuées. Choix du développeur acceptés par l'utilisateur (D-65). Mineurs gardés : onglet lu en entier sans plafond ; commit 116f5f8 non atomique (tests, code, docs). Variables : `RECUS_COPIE_ANNEES_<année>_FICHIER` et `RECUS_COPIE_ANNEES_<année>_ONGLET` remplacent `RECUS_COPIE_FICHIER` et `RECUS_COPIE_ONGLET`. Essai réel à faire sur une copie jetable du Sheet avant les vrais comptes.
- 2026-11-10 : CA31 et CA32, reformulés par D-66 et D-68 (seul l'onglet généré est écrit ; année non configurée signalée dans l'état), déplacés vers 001-13 (D-66). Les critères restants, CA30, CA41, CA42 et CA43, sont retirés par D-66 mais restent rattachés ici (le PRD : « couvert par 001-11, code à retirer ») ; leur code (lecture de l'onglet de l'utilisateur, colonnes repérées par en-tête, mise en forme reprise, motif `STRUCTURE_SHEET`) est retiré par 001-17. Le statut de cette issue ne change pas.
