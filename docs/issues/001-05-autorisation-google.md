---
id: 001-05
titre: Autorisation Google
prd: docs/prd/001-recus.md
besoin: F-01
statut: À faire
depend_de: [001-02]
criteres: [CA64, CA65, CA66, CA67, CA68, CS22, CS23, CS24]
branche: feat/001-05-autorisation-google
commits: []
revue:
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
