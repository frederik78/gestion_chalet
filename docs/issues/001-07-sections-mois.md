---
id: 001-07
titre: Sections de mois
prd: docs/prd/001-recus.md
besoin: F-01
statut: Abandonnée
depend_de: [001-11]
criteres: [CA33, CA34, CA35, CA36, CA37]
branche:
commits: []
revue:
---

## Objectif
La section d'un mois est créée au besoin, et la ligne est placée selon sa date sans déplacer les lignes existantes.

## Périmètre
- Dans : Règles de placement, y compris les lignes sans date et les dates saisies en texte
- Dans : Création d'une section à sa place parmi les mois
- Hors : Déplacement d'une ligne à la correction (001-09).

## Critères d'acceptation
Les critères listés dans l'en-tête (`criteres`), tels que définis dans le PRD. Ils sont tous retirés par D-66 : aucun test à écrire.

## Preuve attendue
Sans objet : issue abandonnée, aucun code à livrer.

## Notes
- 2026-11-01 : Parallélisable avec 001-08.
- 2026-11-10 : En pause (D-66) : le Sheet devient une projection régénérée à la demande ; issue à revoir après le nouveau PRD.
- 2026-11-10 : Abandonnée (D-66) : sans objet. Il n'y a plus d'insertion dans un onglet de l'utilisateur ni de sections de mois (onglet généré plat, Q-16, D-68). Le tri par date est vérifié par CA85 (001-13). CA33 à CA37, tous retirés, restent rattachés ici pour que chaque critère du PRD ait exactement une issue ; aucun test ne les nomme.
