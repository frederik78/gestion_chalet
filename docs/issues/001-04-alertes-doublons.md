---
id: 001-04
titre: Alertes et doublons
prd: docs/prd/001-recus.md
besoin: F-01
statut: Fait
depend_de: [001-02]
criteres: [CA09, CA10, CA11, CA12, CA21, CA22, CA23, CA24, CA25, CA26, CA27, CA29, CA80]
branche: feat/001-04-alertes-doublons
commits: [971418b..bcc1642]
revue: docs/reviews/001-04.md
---

## Objectif
Le reçu et l'écran de validation portent les alertes, sans jamais bloquer ; les quatre reçus réels servent de cas de test.

## Périmètre
- Dans : Alertes sur les numéros d'inscription, la pièce à vérifier, l'écart de calcul et le doublon possible
- Dans : Contrôle préalable à la validation et affichage à l'écran
- Hors : Alertes recalculées à la correction (001-09).

## Critères d'acceptation
Les critères listés dans l'en-tête (`criteres`), tels que définis dans le PRD. Chaque critère doit être couvert par au moins un test
dont le nom ou le `@DisplayName` contient son identifiant (ex. `ca10_…`, `cs07_…`).

## Preuve attendue
Tests rouges puis verts aux bornes (99,99 / 100,00 ; 499,99 / 500,00 ; 0,02 / 0,03), `scripts/verify-clean.sh` à `VERIFY OK`.

## Notes
- 2026-11-01 : Parallélisable avec 001-03 et 001-05 ; conflit probable avec 001-03 sur l'écran de validation.
- 2026-11-04 : Pour 001-03 : `controler()` de `recu.js` doit envoyer `montantTotalLuCents` (CA29), sinon l'écran pourrait taire une alerte que le serveur figera. Mineurs de la relecture n° 1, non corrigés d'office (`docs/reviews/001-04.md`) : M-3 NBSP en bordure du fournisseur, M-4 exclusion SpotBugs à limiter à `comparableSupplier`.
- 2026-11-04 : Limite de 3 relectures atteinte, dernière `APPROUVÉ` ; pas de revue de branche supplémentaire. Décisions : D-54 à D-56, avec le correctif D-55 après fusion (M-3 repris par ce correctif). Reste M-4, exclusion SpotBugs à limiter à `comparableSupplier`. CS15 reste à couvrir par 001-10, y compris le contrôle de doublon.
