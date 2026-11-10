---
id: 001-05
titre: Autorisation Google
prd: docs/prd/001-recus.md
besoin: F-01
statut: Fait
depend_de: [001-02]
criteres: [CA67, CA68, CS22, CS23, CS24]
branche: feat/001-05-autorisation-google
commits: [b7b5ebe..e60e9c0]
revue: docs/reviews/001-05.md
---

## Objectif
L'utilisateur autorise Google une fois ; une expiration ou une révocation est détectée, et les reçus en attente ne sont pas perdus.

## Périmètre
- Dans : Demande d'autorisation limitée aux droits du PRD, rappel protégé par `state`
- Dans : Conservation protégée du jeton, état de l'autorisation, motif de blocage `AUTORISATION_GOOGLE`
- Hors : Copie vers Drive et le Sheet (001-06), bandeau d'état (001-10).

## Critères d'acceptation
Les critères listés dans l'en-tête (`criteres`), tels que définis dans le PRD. Chaque critère doit être couvert par au moins un test
dont le nom ou le `@DisplayName` contient son identifiant (ex. `ca10_…`, `cs07_…`).

## Preuve attendue
Tests rouges puis verts avec un serveur local doublant Google, `scripts/verify-clean.sh` à `VERIFY OK`.

## Notes
- 2026-11-01 : Q-12 reste à confirmer dans la documentation de Google (section 13 du PRD).
- 2026-11-04 : Mineurs de la relecture n° 1, non corrigés d'office (`docs/reviews/001-05.md`) : m1 `withPkce()` redondant ; m3 `HEAD` répond 503 alors que Google est configuré ; m5 `"google"` répété dans 4 classes.
- 2026-11-04 : Limite de 3 relectures atteinte : la dernière n'est pas `APPROUVÉ` au sens strict (mineur m11, question sur le 401). D-59 et m11 corrigés ensuite en e60e9c0, sans 4e relecture, à la demande de l'utilisateur ; preuve par mutation du développeur, vérification par l'orchestrateur. Reste à 001-06 : rythme de l'essai de reprise (R-20), copie sans doublon (CA66). Reste à 001-08 : reçus bloqués pendant un retrait d'identifiants. m12 cosmétique, non corrigé.
- 2026-11-10 : CA64, CA65 et CA66, reformulés par D-66 (plus d'état de copie ; dépôts de photos repris, aucune régénération lancée), déplacés vers 001-16 (D-66). Le statut de cette issue ne change pas.
