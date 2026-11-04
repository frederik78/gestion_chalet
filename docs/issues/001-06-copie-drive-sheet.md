---
id: 001-06
titre: Dépôt Drive et ligne écrite
prd: docs/prd/001-recus.md
besoin: F-01
statut: À faire
depend_de: [001-03, 001-05]
criteres: [CA13, CA17, CA38, CA39, CA40, CA44, CA45, CS12, CS16, CS26]
branche: feat/001-06-depot-drive-ligne
commits: []
revue:
---

## Objectif
Un reçu validé dépose ses photos sur Drive, puis écrit sa ligne dans un onglet au format habituel.

## Périmètre
- Dans : Dépôt des photos avant l'écriture de la ligne, sans attendre dans la réponse de validation
- Dans : Valeurs écrites dans la ligne, colonne PHOTO, NOTES, protection contre l'injection de formules
- Hors : Fichier et onglet selon l'année, colonnes repérées par leur en-tête, colonne PHOTO absente, placement dans une section, blocage sur une structure inattendue (001-11) ; création de sections et placement selon la date (001-07) ; reprise après panne (001-08).
- Limite : tant que 001-11 n'est pas livrée, ne pas utiliser sur un vrai Sheet.

## Critères d'acceptation
Les critères listés dans l'en-tête (`criteres`), tels que définis dans le PRD. Chaque critère doit être couvert par au moins un test
dont le nom ou le `@DisplayName` contient son identifiant (ex. `ca10_…`, `cs07_…`).

## Preuve attendue
Tests rouges puis verts avec un Sheet doublé en mémoire, `scripts/verify-clean.sh` à `VERIFY OK`.

## Notes
- 2026-11-01 : Q-07 reste à confirmer sur un fichier d'essai (section 13 du PRD).
- 2026-11-03 : Dépend aussi de 001-03 : aucune photo ne part vers Drive tant que le contenu des images n'est pas contrôlé sur toutes les routes (D-40).
