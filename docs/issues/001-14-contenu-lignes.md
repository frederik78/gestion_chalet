---
id: 001-14
titre: Contenu des lignes de l'onglet généré
prd: docs/prd/001-recus.md
besoin: F-01
statut: À faire
depend_de: [001-12, 001-13, 001-16]
criteres: [CA13, CA17, CA38, CA39, CA40, CA44, CA139, CS26]
branche: feat/001-14-contenu-lignes
commits: []
revue:
---

## Objectif
Chaque ligne de l'onglet généré porte les valeurs du reçu telles qu'enregistrées, les alertes dans NOTES et les liens des photos déposées, sans jamais inventer une valeur (D-66).

## Périmètre
- Dans : valeurs de la ligne : vide et 0 restent distincts (TPS `null` donne une cellule vide, 0 donne 0, CA13) ; montant total corrigé écrit tel quel, sans recalcul (CA17) ; ligne complète du reçu Garage Iberville (CA38) ; numéros d'inscription absents de toute cellule et de toute métadonnée (CA39, R-25).
- Dans : NOTES : textes des alertes séparés par ` ; `, dans l'ordre du tableau des alertes ; vide sans alerte (CA38, CA40, R-26) ; alertes de taxes `RAPPORT_TVQ_TPS` et `TPS_AU_DELA_DU_TAUX` à leur rang, avec `ECART_CALCUL` et le doublon (CA139, R-49), sans correction ni calcul des taxes ni appel supplémentaire au modèle.
- Dans : PHOTO : un lien « Photo n » par photo déposée, séparés par « , », dans une seule cellule (CA44, R-27) ; lien construit de l'identifiant renvoyé par Drive, jamais d'un texte du reçu, et aucune permission de partage ajoutée par le dépôt ni par la régénération (CS26, R-28).
- Hors : structure, tri, remplacement tout ou rien et mémorisation (001-13) ; cellule PHOTO d'un reçu sans photo déposée et `photosSansLien` (001-15, CA100) ; dépôt et reprise des photos (001-16).

## Critères d'acceptation
Les critères listés dans l'en-tête (`criteres`), tels que définis dans le PRD. Chaque critère doit être couvert par au moins un test
dont le nom ou le `@DisplayName` contient son identifiant (ex. `ca10_…`, `cs07_…`).

## Preuve attendue
Tests rouges puis verts sur le contenu des cellules lues dans le Sheet doublé (types et valeurs), `scripts/verify-clean.sh` à `VERIFY OK`.

## Notes
- Seams : règles métier sans application (conversion des montants, texte de NOTES) puis cas d'usage avec le Sheet doublé ; le serveur HTTP local doublant Google pour CA139.
- Vigilance : R-08 (vide différent de 0), R-25, R-26, R-47 à R-49. Dépend de 001-16 parce que CA44 et CS26 lisent les identifiants Drive déposés par le dépôt automatique ; dépend de 001-12 pour les alertes de taxes de CA139.
- 2026-11-10 : Issue créée par le découpage de la révision D-66 à D-70, approuvé par l'utilisateur. CA13, CA17, CA38 à CA40, CA44 et CS26 viennent de 001-06 (faite).
