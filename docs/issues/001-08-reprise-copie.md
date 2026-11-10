---
id: 001-08
titre: Reprise de la copie
prd: docs/prd/001-recus.md
besoin: F-01
statut: À faire
depend_de: [001-11]
criteres: [CA46, CA47, CA48, CA49, CA50, CA51, CA52, CA53, CA54, CS18]
branche: feat/001-08-reprise-copie
commits: []
revue:
---

## Objectif
Une copie interrompue est réessayée sans doublon, y compris après un redémarrage, et les blocages attendent une action de l'utilisateur.

## Périmètre
- Dans : Nouveaux essais et rythme, quota Google, relance manuelle, reprise au démarrage, copies traitées une à la fois
- Dans : Réponses perdues de Drive et du Sheet, transitions d'état autorisées
- Hors : Correction d'un reçu (001-09), bandeau d'état (001-10).

## Critères d'acceptation
Les critères listés dans l'en-tête (`criteres`), tels que définis dans le PRD. Chaque critère doit être couvert par au moins un test
dont le nom ou le `@DisplayName` contient son identifiant (ex. `ca10_…`, `cs07_…`).

## Preuve attendue
Tests rouges puis verts avec une horloge doublée, `scripts/verify-clean.sh` à `VERIFY OK`.

## Notes
- 2026-11-01 : Parallélisable avec 001-07.
- 2026-11-04 : Architecture (D-60) : le rythme des essais, `prochaineTentative`, la relance et la reprise au démarrage s'ajoutent à `RecuCopyService`, pas dans un nouveau module. L'heure d'essai est portée par la transition `A_COPIER` vers `A_COPIER` de `Recu`, les durées par `recus.copie.*`.
- 2026-11-10 : En pause (D-66) : le Sheet devient une projection régénérée à la demande ; issue à revoir après le nouveau PRD.
