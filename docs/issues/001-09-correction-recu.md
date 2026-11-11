---
id: 001-09
titre: Correction d'un reçu
prd: docs/prd/001-recus.md
besoin: F-01
statut: En cours
depend_de: [001-04, 001-12, 001-13]
criteres: [CA28, CA61, CA62, CA63, CA98, CA138]
branche: feat/001-09-correction-recu
commits: []
revue:
---

## Objectif
L'utilisateur corrige un reçu validé : la base change, ses alertes sont recalculées (taxes comprises), et la correction apparaît dans l'onglet généré à la régénération suivante, sans aucun appel à Google ni aucune photo redéposée.

## Périmètre
- Dans : correction par `PUT /api/recus/{id}` avec contrôle de version (409 `VERSION_PERIMEE`), validation de R-06 et R-10, alertes recalculées (dont `RAPPORT_TVQ_TPS` et `TPS_AU_DELA_DU_TAUX`, CA138), un reçu non comparé à lui-même pour le doublon (CA28).
- Dans : la correction n'écrit rien dans le Sheet (R-29) ; une régénération demandée directement, sans import, écrase la modification manuelle de l'onglet généré et laisse l'onglet de l'utilisateur intact (CA62), et rend visibles la valeur corrigée et les alertes recalculées (CA63, CA98, y compris `recusNonReportes` avant puis après).
- Dans : numéros d'inscription, montant total lu et photos inchangés par une correction ; photos ni redéposées ni renommées.
- Hors : l'import, qui applique des corrections par la même règle (001-19) ; ajout d'une photo à la correction (exclu de la première version, Q-04) ; réécriture de ligne, déplacement entre mois ou années, état de copie (retirés par D-66).

## Critères d'acceptation
Les critères listés dans l'en-tête (`criteres`), tels que définis dans le PRD. Chaque critère doit être couvert par au moins un test
dont le nom ou le `@DisplayName` contient son identifiant (ex. `ca10_…`, `cs07_…`).

## Preuve attendue
Tests rouges puis verts avec un Sheet doublé en mémoire pour la régénération, `scripts/verify-clean.sh` à `VERIFY OK`.

## Notes
- 2026-11-04 : Architecture (D-60) : la correction repassait à `A_COPIER` par `RecuCopyService` ; sans objet depuis D-66, il n'y a plus d'état de copie : la correction ne touche ni au dépôt ni au Sheet.
- 2026-11-10 : En pause (D-66) : le Sheet devient une projection régénérée à la demande ; issue à revoir après le nouveau PRD.
- 2026-11-10 : Redécoupée (D-66, D-67) : CA55 à CA60, retirés, rattachés à 001-08 (abandonnée) ; ajout de CA98 et de CA138 (partie correction de CA111). Dépend de 001-13 (régénération, pour CA62, CA63 et CA98) et de 001-12 (alertes de taxes, pour CA138) ; elle n'a plus de lien avec 001-06 ni 001-07.
- Vigilance : R-29, R-34, R-08 (une taxe vide reste vide, jamais calculée), R-47 à R-49. Le contrôle de version de CA61 est celui que réutilise l'import (R-58).
