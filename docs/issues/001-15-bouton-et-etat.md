---
id: 001-15
titre: Bouton de mise à jour et état de la régénération
prd: docs/prd/001-recus.md
besoin: F-01
statut: À faire
depend_de: [001-13, 001-14]
criteres: [CA54, CA91, CA92, CA93, CA94, CA95, CA97, CA100, CA101, CA102, CA103, CA114, CA140, CS29, CS30, CS31]
criteres_justification: Une seule tranche verticale (machine d'états de la régénération et son écran). Les 16 critères se vérifient presque tous sur le même double du Sheet et les mêmes quelques cas d'usage ; les scinder obligerait à livrer un état sans écran, ou un écran sans état.
branche: feat/001-15-bouton-et-etat
commits: []
revue:
---

## Objectif
L'utilisateur lance la mise à jour du Google Sheet par un bouton, voit l'état de la régénération (jamais, en cours, réussie, échec et son motif), et rien d'autre ne la déclenche.

## Périmètre
- Dans : état global unique, conservé au redémarrage ; transitions du tableau de la section 4.2 et refus des autres (CS29) ; `GET /api/sheet/etat` complet : date de réussite, `recusEcrits`, `recusNonReportes`, `photosSansLien`, `anneesNonConfigurees` (CA91, R-42).
- Dans : une seule régénération à la fois, 409 `REGENERATION_EN_COURS` (CA92, R-43) ; réponse 202 même quand une condition manque (état `ECHEC` sans appel à Google, CA93) ; priorité des motifs, `FICHIER_INACCESSIBLE` avant `AUTORISATION_GOOGLE` (CA94) ; `GOOGLE_INDISPONIBLE` sur 429, 503, erreur réseau ou délai de 5 minutes, sans nouvel essai automatique (CA97, R-44) ; fichier ou onglet refusé, 403 ou 404, `FICHIER_INACCESSIBLE`, l'onglet antérieur intact, et le même refus de Drive laissant la photo à déposer (CA54) ; `invalid_grant` pendant la régénération : `A_RENOUVELER` et `AUTORISATION_GOOGLE` (CA101) ; onglet de même nom non créé par l'application : `ONGLET_NON_RECONNU`, aucune requête de modification (CS30, R-41).
- Dans : motif `ERREUR_INTERNE` (D-72) pour une panne qui ne vient pas de Google pendant la régénération (erreur interne, base de données) : état `ECHEC`, ancien onglet intact, onglet temporaire retiré si possible, journal limité au nom de la classe de l'erreur, texte « erreur inattendue, voir les journaux » dans l'écran (CA140). Tant que cette issue n'est pas livrée, 001-13 garde `GOOGLE_INDISPONIBLE` pour ces pannes.
- Dans : redémarrage trouvant `EN_COURS` : `ECHEC` `INTERROMPUE`, sans nouvelle régénération (CA95) ; le contenu mémorisé dont l'issue du remplacement est inconnue est marqué incertain (R-53, utilisé par 001-18).
- Dans : `POST /api/sheet/regeneration` ignore tout corps (CS31) ; pour le reçu CA100 : PHOTO vide pour un reçu sans photo déposée, aucun appel à Drive pendant la régénération, `photosSansLien` (R-18, R-45, Q-21).
- Dans : écran de la liste : bouton « Mettre à jour le Google Sheet », désactivé pendant la mise à jour, un seul appel pour un double clic, état lu périodiquement tant qu'`EN_COURS` (CA102) ; textes de l'état en français, motif jamais en code technique (CA103) ; confirmation transitoire de perte des modifications manuelles avant `POST /api/sheet/regeneration` (CA114, R-63), remplacée plus tard par le parcours de l'import (001-19, CA137).
- Hors : structure et remplacement de l'onglet (001-13), contenu des lignes (001-14), dépôt et reprise (001-16), import et son écran (001-18, 001-19), bandeau d'autorisation (001-10).

## Critères d'acceptation
Les critères listés dans l'en-tête (`criteres`), tels que définis dans le PRD. Chaque critère doit être couvert par au moins un test
dont le nom ou le `@DisplayName` contient son identifiant (ex. `ca10_…`, `cs07_…`).

## Preuve attendue
Tests rouges puis verts (Sheet et Drive doublés, horloge contrôlée, navigateur sans tête pour CA102, CA103 et CA114), `scripts/verify-clean.sh` à `VERIFY OK`.

## Notes
- Seams : transitions sans application (CS29) ; cas d'usage avec le Sheet doublé en attente pour la concurrence (CA92) ; contrat HTTP (CA91 à CA93, CS31) ; navigateur sans tête pour l'écran.
- Vigilance : R-42 à R-46 ; l'état et les motifs sont toujours calculés par le serveur (ADR 0001, CS31) ; une régénération ne programme jamais de nouvel essai (R-44) ; aucun nom de fichier, d'onglet ni identifiant Google dans l'objet état ; les événements de sécurité de CS21 (échec de régénération) sont vérifiés par 001-10.
- 2026-11-10 : Issue créée par le découpage de la révision D-66 à D-70, approuvé par l'utilisateur. CA54 vient de 001-08 ; le minimum d'état déjà posé par 001-13 y est complété. CA100 vit ici et non avec le dépôt (001-16) parce qu'il lit `photosSansLien` et une régénération.
- 2026-11-10 : D-72 ajoute le motif `ERREUR_INTERNE` et le critère CA140 à cette issue.
