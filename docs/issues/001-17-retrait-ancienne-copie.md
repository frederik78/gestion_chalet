---
id: 001-17
titre: Retrait de l'ancienne copie ligne par ligne
prd: docs/prd/001-recus.md
besoin: F-01
statut: À faire
depend_de: [001-15, 001-16]
criteres: [CA51, CS18]
branche: feat/001-17-retrait-ancienne-copie
commits: []
revue:
---

## Objectif
Le code et le schéma devenus sans objet depuis D-66 disparaissent : il ne reste que le dépôt des photos par reçu et la régénération, sans état de copie par reçu.

## Périmètre
- Dans : retrait de l'insertion d'une ligne dans l'onglet de l'utilisateur, de la lecture de cet onglet (colonnes repérées par en-tête, sections de mois, formules, mise en forme reprise, ajout de PHOTO), du motif `STRUCTURE_SHEET` et des autres motifs de blocage de copie (`ANNEE_NON_CONFIGUREE`, `AUTORISATION_GOOGLE`, `FICHIER_INACCESSIBLE` côté reçu, `FORMULE_PRESENTE`) ; ce retrait vaut pour CA30, CA41, CA42 et CA43, rattachés à 001-11 (faite) par le PRD.
- Dans : retrait des états de copie du reçu (`A_COPIER`, `BLOQUEE`, `COPIEE`), de leurs transitions, de la règle d'architecture qui les réservait, et de leur colonne par un nouveau changeset Liquibase (jamais en modifiant un changeset appliqué) ; les reçus déjà enregistrés gardent leurs photos et leurs identifiants Drive (Q-19) et figurent à la première régénération.
- Dans : retrait de la route `POST /api/recus/{id}/copie` et du paramètre `etatCopie` de la liste (CA51) ; un état de copie n'existe plus nulle part (CS18).
- Hors : tout comportement nouveau ; les critères CA33 à CA37, CA48, CA52 et CA55 à CA60 (retirés sans code) restent rattachés à 001-07 et 001-08.

## Critères d'acceptation
Les critères listés dans l'en-tête (`criteres`), retirés par D-66, tels que définis dans le PRD. Chacun a un test d'absence nommé par son identifiant : `ca51_…` (la route répond 404 `INTROUVABLE`, sans en-tête `Allow`, et n'appelle rien) et `cs18_…` (plus aucun état ni transition de copie dans le domaine, la base ni l'API, une requête qui en porte un n'a aucun effet).

## Preuve attendue
Les tests restants restent verts après le retrait ; absence du code retiré prouvée par les tests d'absence ci-dessus et par la recherche des noms retirés dans les sources (liste dans le rapport du développeur) ; `scripts/verify-clean.sh` à `VERIFY OK`. Aucun test existant n'est supprimé sans que son critère soit retiré par le PRD ou repris par une autre issue.

## Notes
- Aucun critère nouveau : le travail est un retrait, après que 001-15 et 001-16 ont remplacé tous les usages (le dépôt n'écrit plus de ligne, la régénération porte l'état).
- Vigilance : ne pas supprimer ce que la régénération réutilise (client du Sheet, configuration par année, autorisation Google, dépôt Drive) ; interfaces `client/` conservées seulement si une implémentation et un double les utilisent encore ; ne rien casser des tests d'architecture (`TechnicalStructureTest`, `AgentRulesTest`) ; mettre à jour `AGENTS.md` si la règle d'architecture sur les transitions de copie disparaît (à proposer, non modifié sans accord).
- 2026-11-10 : Issue créée par le découpage de la révision D-66 à D-70, approuvé par l'utilisateur. CA51 vient de 001-08, CS18 de 001-08.
