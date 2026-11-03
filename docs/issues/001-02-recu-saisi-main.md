---
id: 001-02
titre: Reçu saisi à la main
prd: docs/prd/001-recus.md
besoin: F-01
statut: Fait
depend_de: [001-01]
criteres: [CA14, CA15, CA16, CA18, CA19, CA20, CA72, CA75, CA76, CA79, CA81, CS17, CS27]
branche: feat/001-02-recu-saisi-main
commits: [bfff609..5464791]
revue: docs/reviews/001-02.md
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
- 2026-11-03 : Mineurs ouverts, détaillés dans `docs/reviews/001-02.md` : M1 à M7, M9, M10 (itération 1), M14, M15, M16 (itération 2). M15 est à reprendre dans 001-03 : le format d'une photo n'est contrôlé qu'à la création.
- 2026-11-03 : À prouver au premier passage sur GitHub : Chrome de l'image du runner, `sysctl`, 6 tests d'écran sous Ubuntu, contrôle du rapport, durées réelles (D-37).
- 2026-11-03 : Revue approuvée à la 3e itération. M12, M17 et M18 corrigés ensuite (45439be, a243a1e, 5464791), vérifiés par l'orchestrateur : 323 tests, 0 échec, 0 ignoré, SpotBugs 0. Captures consultées (validation, panne, conflit), avec images générées et hors dépôt : messages et boutons visibles ; `essai.captures` seul coupe le bas de l'écran à 900 px de haut.
- 2026-11-03 : Pour 001-03 : garder `POST /api/recus` ; `photosParRecuMax` et `formats` sont annoncés à la PWA mais pas appliqués par le serveur. Pour 001-09 : rejouer le test CS17 sur `PUT`.
- 2026-11-03 : Décisions en attente de l'utilisateur, à inscrire au PRD à la clôture : date ou montant illisible en `REQUETE_INVALIDE` sans `champs` ; `VALIDATION` avant `PHOTO_MANQUANTE`, sans `champs` (CA14) ; M14, normalisation d'`objet`. Détail : `docs/reviews/001-02.md`.
