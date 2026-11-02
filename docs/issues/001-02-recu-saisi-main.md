---
id: 001-02
titre: Reçu saisi à la main
prd: docs/prd/001-recus.md
besoin: F-01
statut: À faire
depend_de: [001-01]
criteres: [CA14, CA15, CA16, CA18, CA19, CA20, CA72, CA75, CA76, CA79, CA81, CS17, CS27]
branche: feat/001-02-recu-saisi-main
commits: []
revue:
---

## Objectif
L'utilisateur prend ou importe une photo, saisit les valeurs, valide, et le reçu est enregistré avec sa photo.

## Périmètre
- Dans : Écran de prise de vue, formulaire de validation, abandon, nouvel essai après une panne réseau
- Dans : Enregistrement du reçu et de ses photos à la validation, idempotence, champs obligatoires et liste des types de dépense
- Dans : Contrat JSON de l'objet reçu, absence de suppression
- Hors : Lecture et contrôle des images (001-03), alertes (001-04), copie vers Google (001-06).

## Critères d'acceptation
Les critères listés dans l'en-tête (`criteres`), tels que définis dans le PRD. Chaque critère doit être couvert par au moins un test
dont le nom ou le `@DisplayName` contient son identifiant (ex. `ca10_…`, `cs07_…`).

## Preuve attendue
Tests rouges puis verts, capture de l'écran de validation, `scripts/verify-clean.sh` à `VERIFY OK`.

## Notes
- 2026-11-01 : Le reçu naît à l'état `A_COPIER` sans copie réelle. Les images ne sont pas encore contrôlées : sans risque tant que rien n'est déployé, et traité par 001-03.
