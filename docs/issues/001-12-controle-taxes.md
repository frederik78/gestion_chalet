---
id: 001-12
titre: Contre-vérification des taxes
prd: docs/prd/001-recus.md
besoin: F-01
statut: Fait
depend_de: [001-04]
criteres: [CA104, CA105, CA106, CA107, CA108, CA109, CA110, CA111, CA112, CA113]
branche: feat/001-12-controle-taxes
commits: []
revue:
---

## Objectif
Deux alertes non bloquantes signalent une TPS ou une TVQ incohérente : le rapport TVQ/TPS s'écarte de 9,975/5, ou la TPS dépasse 5 % du montant avant taxes (D-67).

## Périmètre
- Dans : codes `RAPPORT_TVQ_TPS` et `TPS_AU_DELA_DU_TAUX`, règles R-47 à R-49, calcul exact en cents entiers, taux et tolérance en configuration (section 9), affichage avant validation (`POST /api/recus/controles`), texte dans NOTES dans l'ordre du tableau des alertes.
- Hors : régénération du Sheet (D-66, D-68) et import (D-69), issues à venir ; aucune valeur corrigée ni calculée ; pas de seconde lecture par le modèle.

## Critères d'acceptation
Les critères listés dans l'en-tête (`criteres`), tels que définis dans le PRD. Chaque critère doit être couvert par au moins un test
dont le nom ou le `@DisplayName` contient son identifiant (ex. `ca10_…`, `cs07_…`).

## Preuve attendue
Tests rouges puis verts sur les règles d'alerte et de bout en bout par `POST /api/recus/controles` et la validation, `scripts/verify-clean.sh` à `VERIFY OK`.

## Notes
- 2026-11-10 : Issue créée par la session sur la décision D-67 et ses précisions (tolérance propre, pas de contrôle du rapport avec une taxe à 0, rien recalculé sans montant avant taxes). Livrée avant la régénération (D-69). Branche empilée sur `docs/sheet-genere`, qui porte le PRD révisé : à fusionner après elle.
