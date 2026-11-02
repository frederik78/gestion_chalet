---
id: 001-01
titre: Socle et connexion
prd: docs/prd/001-recus.md
besoin: F-01
statut: À faire
depend_de: []
criteres: [CS01, CS02, CS04, CS05, CS06, CS07, CS08, CS09, CS28, CA83]
branche: feat/001-01-socle-connexion
commits: []
revue:
---

## Objectif
Mettre en place le projet et ses garde-fous, avec une PWA minimale où l'utilisateur se connecte, et vérifier sur le réel que l'appel au modèle retenu fonctionne.

## Périmètre
- Dans : Build Maven et wrapper, tests d'architecture de l'ADR 0001, analyse statique de sécurité, CI
- Dans : Analyse des dépendances vulnérables (OWASP Dependency-Check), que l'ADR ne citait pas ; profil `security`, A06
- Dans : Connexion du compte local, session, CSRF, blocage après échecs, en-têtes de sécurité, erreurs génériques, test sur serveur réel
- Dans : Page de connexion, manifeste et logique de cache de la PWA
- Dans : Essai manuel, hors suite de tests : un vrai appel à Claude Haiku 5.5 avec une photo et une sortie structurée (ADR 0001, section 13 du PRD), photos réelles hors dépôt
- Hors : Lecture et validation des reçus (001-02, 001-03).

## Critères d'acceptation
Les critères listés dans l'en-tête (`criteres`), tels que définis dans le PRD. Chaque critère doit être couvert par au moins un test
dont le nom ou le `@DisplayName` contient son identifiant (ex. `ca10_…`, `cs07_…`).

## Preuve attendue
Tests rouges puis verts, `scripts/verify-clean.sh` à `VERIFY OK`, résultat de l'essai réel par champ sans donnée personnelle.

## Notes
- 2026-11-01 : Besoin d'une clé d'API Anthropic pour l'essai réel. Si Spring AI ne convient pas, repli sur le SDK Java officiel derrière la même frontière (ADR 0001).
