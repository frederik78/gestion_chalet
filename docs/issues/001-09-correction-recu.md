---
id: 001-09
titre: Correction d'un reçu
prd: docs/prd/001-recus.md
besoin: F-01
statut: À faire
depend_de: [001-04, 001-06, 001-07]
criteres: [CA55, CA56, CA57, CA58, CA59, CA60, CA61, CA62, CA63, CA28]
branche: feat/001-09-correction-recu
commits: []
revue:
---

## Objectif
L'utilisateur corrige un reçu validé ; sa ligne est réécrite ou déplacée, et ses alertes sont recalculées.

## Périmètre
- Dans : Correction avec contrôle de version, réécriture de la ligne à son emplacement actuel
- Dans : Déplacement si le mois ou l'année change, ligne supprimée à la main, formule présente
- Hors : Ajout d'une photo à la correction (exclu de la première version, Q-04).

## Critères d'acceptation
Les critères listés dans l'en-tête (`criteres`), tels que définis dans le PRD. Chaque critère doit être couvert par au moins un test
dont le nom ou le `@DisplayName` contient son identifiant (ex. `ca10_…`, `cs07_…`).

## Preuve attendue
Tests rouges puis verts, `scripts/verify-clean.sh` à `VERIFY OK`.

## Notes
