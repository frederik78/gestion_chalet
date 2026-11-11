---
id: 001-20
titre: Modes dev et prod
prd: docs/prd/001-recus.md
besoin: F-01
statut: En revue
depend_de: [001-01]
criteres: [CS37]
branche: fix/modes-dev-prod
commits: []
revue:
---

## Objectif
L'application démarre en mode prod par défaut (cookies `Secure`) ; le mode dev n'existe que par le profil `dev` demandé explicitement, avertit au démarrage et reste utilisable en HTTP.

## Périmètre
- Dans : cookies de session et CSRF `Secure` sans profil, profil `dev` qui retire `Secure`, avertissement de démarrage sans valeur de configuration, écran de validation utilisable en HTTP non sécurisé (sans `crypto.randomUUID`), modèle `.env.example` et documentation des deux modes.
- Hors : déploiement et sauvegardes (backlog, NF-02 et NF-03).

## Critères d'acceptation
Les critères listés dans l'en-tête (`criteres`), tels que définis dans le PRD. Chaque critère doit être couvert par au moins un test
dont le nom ou le `@DisplayName` contient son identifiant (ex. `ca10_…`, `cs07_…`).

## Preuve attendue
Tests `cs29_…` rouges puis verts, `scripts/verify-clean.sh` à `VERIFY OK`.

## Notes
- 2026-11-10 : Issue créée sur décision de l'utilisateur pour que CS37 soit couvert par exactement une issue non `Fait`. Tests : `cs37_modeProd_sansProfilLesCookiesPortentSecure`, `cs37_modeDev_leProfilDevRetireSecureDesCookies`, `cs37_modeDev_leDemarrageJournaliseUnAvertissementSansValeurSecrete`, `cs37_modeDev_sansRandomUUIDLeBoutonContinuerFonctionneEtLaCleEstUnUuidV4` (Chrome).
- Le passage à `Fait` revient à l'utilisateur après sa fusion et `scripts/fin-issue.sh 001-20`.
