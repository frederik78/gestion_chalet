---
id: 001-08
titre: Reprise de la copie
prd: docs/prd/001-recus.md
besoin: F-01
statut: Abandonnée
depend_de: [001-11]
criteres: [CA48, CA52, CA55, CA56, CA57, CA58, CA59, CA60]
branche:
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
Les critères listés dans l'en-tête (`criteres`), tels que définis dans le PRD. Ils sont tous retirés par D-66 : aucun test à écrire.

## Preuve attendue
Sans objet : issue abandonnée, remplacée par 001-16.

## Notes
- 2026-11-01 : Parallélisable avec 001-07.
- 2026-11-04 : Architecture (D-60) : le rythme des essais, `prochaineTentative`, la relance et la reprise au démarrage s'ajoutent à `RecuCopyService`, pas dans un nouveau module. L'heure d'essai est portée par la transition `A_COPIER` vers `A_COPIER` de `Recu`, les durées par `recus.copie.*`.
- 2026-11-10 : En pause (D-66) : le Sheet devient une projection régénérée à la demande ; issue à revoir après le nouveau PRD.
- 2026-11-10 : Abandonnée (D-66), remplacée par 001-16 (dépôt automatique des photos, reprise, pannes Drive). Critères reformulés déplacés : CA46, CA47, CA49, CA50, CA53 vers 001-16 ; CA54 vers 001-15 ; CA51 et CS18 vers 001-17 (code à retirer). CA48 et CA52 (retirés : plus d'identifiant de ligne invisible, plus d'insertion à ordonner) restent ici. CA55 à CA60 (retirés, ex 001-09 : plus de ligne persistante à réécrire ni de formule à refuser) y sont rattachés pour que chaque critère du PRD ait exactement une issue ; aucun test ne les nomme.
