---
id: 001-10
titre: Consultation et contrôles finaux
prd: docs/prd/001-recus.md
besoin: F-01
statut: À faire
depend_de: [001-03, 001-08, 001-09]
criteres: [CA71, CA73, CA74, CA82, CS03, CS14, CS15, CS20, CS21, CS25]
branche: feat/001-10-consultation-controles
commits: []
revue:
---

## Objectif
L'utilisateur consulte ses reçus et l'état de leur copie ; le parcours complet est contrôlé pour les journaux et les hôtes contactés.

## Périmètre
- Dans : Liste filtrée, détail, photos relues, bandeau d'état
- Dans : Contrôles sur le parcours complet : aucune donnée de reçu dans les journaux, événements de sécurité, seuls hôtes de la configuration contactés, injections sans effet
- Hors : Déploiement (NF-03), sauvegardes (NF-02).

## Critères d'acceptation
Les critères listés dans l'en-tête (`criteres`), tels que définis dans le PRD. Chaque critère doit être couvert par au moins un test
dont le nom ou le `@DisplayName` contient son identifiant (ex. `ca10_…`, `cs07_…`).

## Preuve attendue
Tests rouges puis verts, contrôle manuel de la section 13 du PRD, `scripts/verify-clean.sh` à `VERIFY OK`.

## Notes
- 2026-11-01 : Le contrôle manuel confirme aussi Q-11 sur le vrai téléphone.
