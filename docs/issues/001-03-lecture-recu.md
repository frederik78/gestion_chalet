---
id: 001-03
titre: Lecture du reçu
prd: docs/prd/001-recus.md
besoin: F-01
statut: À faire
depend_de: [001-02]
criteres: [CA01, CA02, CA03, CA04, CA05, CA06, CA07, CA08, CA84, CA69, CA70, CA77, CA78, CS10, CS11, CS13, CS19]
branche: feat/001-03-lecture-recu
commits: []
revue:
---

## Objectif
La photo est lue par le modèle et le formulaire de validation est prérempli ; les images sont contrôlées sur les deux routes d'envoi.

## Périmètre
- Dans : Appel au modèle par une frontière doublée dans les tests, modèle fixé par configuration
- Dans : Résultats `LU`, `ILLISIBLE`, `ECHEC` et valeurs rejetées, affichage à côté des photos
- Dans : Contrôle du format, de la taille, des dimensions et du contenu réel des images ; retrait des métadonnées pour le modèle ; limite de lectures
- Hors : Alertes affichées (001-04).

## Critères d'acceptation
Les critères listés dans l'en-tête (`criteres`), tels que définis dans le PRD. Chaque critère doit être couvert par au moins un test
dont le nom ou le `@DisplayName` contient son identifiant (ex. `ca10_…`, `cs07_…`).

## Preuve attendue
Tests rouges puis verts avec un double du modèle, `scripts/verify-clean.sh` à `VERIFY OK`.

## Notes
- 2026-11-01 : Parallélisable avec 001-04 et 001-05 ; conflit probable avec 001-04 sur l'écran de validation.
