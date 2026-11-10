---
id: 001-10
titre: Consultation et contrôles finaux
prd: docs/prd/001-recus.md
besoin: F-01
statut: À faire
depend_de: [001-03, 001-09, 001-15, 001-16]
criteres: [CA71, CA73, CA74, CA82, CS03, CS14, CS15, CS20, CS21, CS25]
branche: feat/001-10-consultation-controles
commits: []
revue:
---

## Objectif
L'utilisateur consulte ses reçus (et les photos sans lien) et voit le bandeau d'autorisation Google ; le parcours complet (lecture, validation, dépôt, régénération) est contrôlé pour les journaux et les hôtes contactés.

## Périmètre
- Dans : liste filtrée par année (le paramètre `etatCopie` est retiré et ignoré, CA74), détail, photos relues, bandeau d'autorisation Google sans compte de reçus bloqués (CA82)
- Dans : contrôles sur le parcours complet : aucune donnée de reçu dans les journaux, événements de sécurité (échec de régénération, dépôt bloqué), seuls hôtes de la configuration contactés, injections sans effet
- Hors : l'état de la régénération et le bouton (001-15), le dépôt (001-16), l'import (001-18, 001-19) ; déploiement (NF-03), sauvegardes (NF-02).

## Critères d'acceptation
Les critères listés dans l'en-tête (`criteres`), tels que définis dans le PRD. Chaque critère doit être couvert par au moins un test
dont le nom ou le `@DisplayName` contient son identifiant (ex. `ca10_…`, `cs07_…`).

## Preuve attendue
Tests rouges puis verts, contrôle manuel de la section 13 du PRD, `scripts/verify-clean.sh` à `VERIFY OK`.

## Notes
- 2026-11-01 : Le contrôle manuel confirme aussi Q-11 sur le vrai téléphone.
- 2026-11-10 : Revue après D-66. Dépendances : 001-16 et 001-09 remplacent 001-08, et 001-15 est ajoutée parce que CS20, CS21 et CS25, reformulés (« copie » devient « dépôt et régénération »), parcourent la régénération et ses échecs. Le sens de CA71, CA73, CS03 et CS14 est inchangé ; CA74, CA82 et CS15 sont reformulés (plus d'état de copie, d'`etatCopie` ni de reçus bloqués). Le périmètre ci-dessus est mis à jour en conséquence.
- Vigilance : CS20 : aucune valeur de reçu, de mot de passe ni de jeton, aucun nom de fichier ni d'onglet dans les journaux ; CS25 : seuls les hôtes de la configuration sont contactés par la lecture, la validation, le dépôt et la régénération. Le parcours de l'import (CS34) est contrôlé par 001-19.
