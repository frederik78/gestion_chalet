---
id: 001-07
titre: Sections de mois
prd: docs/prd/001-recus.md
besoin: F-01
statut: À faire
depend_de: [001-11]
criteres: [CA33, CA34, CA35, CA36, CA37]
branche: feat/001-07-sections-mois
commits: []
revue:
---

## Objectif
La section d'un mois est créée au besoin, et la ligne est placée selon sa date sans déplacer les lignes existantes.

## Périmètre
- Dans : Règles de placement, y compris les lignes sans date et les dates saisies en texte
- Dans : Création d'une section à sa place parmi les mois
- Hors : Déplacement d'une ligne à la correction (001-09).

## Critères d'acceptation
Les critères listés dans l'en-tête (`criteres`), tels que définis dans le PRD. Chaque critère doit être couvert par au moins un test
dont le nom ou le `@DisplayName` contient son identifiant (ex. `ca10_…`, `cs07_…`).

## Preuve attendue
Tests rouges puis verts sur les suites de dates du PRD, `scripts/verify-clean.sh` à `VERIFY OK`.

## Notes
- 2026-11-01 : Parallélisable avec 001-08.
- 2026-11-10 : En pause (D-66) : le Sheet devient une projection régénérée à la demande ; issue à revoir après le nouveau PRD.
