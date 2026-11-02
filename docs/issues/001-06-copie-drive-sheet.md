---
id: 001-06
titre: Copie vers Drive et le Sheet
prd: docs/prd/001-recus.md
besoin: F-01
statut: À faire
depend_de: [001-05]
criteres: [CA13, CA17, CA30, CA31, CA32, CA38, CA39, CA40, CA41, CA42, CA43, CA44, CA45, CS12, CS16, CS26]
branche: feat/001-06-copie-drive-sheet
commits: []
revue:
---

## Objectif
Un reçu validé part sur Drive pour ses photos, puis dans le bon fichier et le bon onglet du Sheet pour sa ligne.

## Périmètre
- Dans : Dépôt des photos avant l'écriture de la ligne, sans attendre dans la réponse de validation
- Dans : Fichier et onglet selon l'année, colonnes repérées par leur en-tête, colonne PHOTO, NOTES, protection contre l'injection de formules
- Dans : Placement simple de la ligne dans une section existante
- Hors : Création de sections et placement selon la date (001-07), reprise après panne (001-08).

## Critères d'acceptation
Les critères listés dans l'en-tête (`criteres`), tels que définis dans le PRD. Chaque critère doit être couvert par au moins un test
dont le nom ou le `@DisplayName` contient son identifiant (ex. `ca10_…`, `cs07_…`).

## Preuve attendue
Tests rouges puis verts avec un Sheet doublé en mémoire, `scripts/verify-clean.sh` à `VERIFY OK`.

## Notes
- 2026-11-01 : Q-07 reste à confirmer sur un fichier d'essai (section 13 du PRD).
