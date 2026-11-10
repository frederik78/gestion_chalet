---
id: 001-16
titre: Dépôt des photos par reçu
prd: docs/prd/001-recus.md
besoin: F-01
statut: À faire
depend_de: [001-06]
criteres: [CA45, CA46, CA47, CA49, CA50, CA53, CA64, CA65, CA66, CA99]
branche: feat/001-16-photos-par-recu
commits: []
revue:
---

## Objectif
Après la validation, les photos d'un reçu sont déposées dans Drive toutes seules, avec ou sans Sheet configuré, reprises après une panne ou un redémarrage, sans doublon et sans aucun appel au Sheet (D-66, D-68). Remplace 001-08.

## Périmètre
- Dans : dépôt dès que Google est autorisé, sans que la réponse de validation l'attende, sans aucun appel au Sheet, même sans fichier configuré (CA45, R-18, Q-21) ; chaque photo est « déposée » ou « à déposer », une photo déposée n'est jamais redéposée ni renommée (R-29).
- Dans : reprise : panne passagère de Drive, nouvel essai à 1, 2, 4 minutes puis plafond d'une heure, `Retry-After` respecté (quota de 120 s, CA47, CA53, R-20) ; le reçu reste enregistré et consultable, la photo sans `lienDrive` (CA46) ; réponse de dépôt perdue : fichier retrouvé par l'identifiant de la photo, un seul fichier (CA49, R-21) ; reprise de toute photo à déposer au redémarrage, dont celle qui attend l'autorisation (CA50).
- Dans : autorisation Google : sans autorisation, le reçu est enregistré, l'objet ne porte aucun état de copie, les photos attendent, `GET /api/google/etat` répond `ABSENTE` (CA64) ; après autorisation, les dépôts en attente reprennent aussitôt, sans régénération, et sans nouvelle autorisation ensuite (CA65) ; `invalid_grant` pendant un dépôt : `A_RENOUVELER`, dépôts à faire, validation possible, photos déposées sans doublon après une nouvelle autorisation (CA66).
- Dans : aucune écriture au Sheet n'a lieu après une validation, une correction, une autorisation accordée ou perdue, un redémarrage, ni après une heure d'horloge doublée (CA99, R-44).
- Dans : le cycle de copie ne passe plus par l'écriture de ligne : l'objet reçu et la liste cessent d'exposer un état de copie, un motif de blocage et une prochaine tentative (section 7.1) ; les reçus déjà enregistrés par une version antérieure dont des photos sont encore à déposer sont repris (Q-19).
- Hors : retrait du code d'insertion ligne par ligne, des états de copie dans le domaine et leur migration, de la route `POST /api/recus/{id}/copie` (001-17) ; régénération et son état (001-13, 001-15) ; `photosSansLien` (001-15).

## Critères d'acceptation
Les critères listés dans l'en-tête (`criteres`), tels que définis dans le PRD. Chaque critère doit être couvert par au moins un test
dont le nom ou le `@DisplayName` contient son identifiant (ex. `ca10_…`, `cs07_…`).

## Preuve attendue
Tests rouges puis verts par l'interface du module de dépôt : vraie base SQLite, Drive doublé en mémoire (et serveur HTTP local pour le format des requêtes), Sheet doublé dont on vérifie qu'il n'a reçu aucun appel, horloge contrôlée ; `scripts/verify-clean.sh` à `VERIFY OK`.

## Notes
- Architecture : ce travail reste dans le module de copie réuni (D-60) : les appelants signalent des faits (reçu validé, autorisation accordée ou perdue), le module décide de l'ordre, des pannes et des essais ; aucune transaction ouverte pendant un appel à Google ; la base sert de file, un seul fil. Aucun nouvel ADR : seule la portée change (photos, plus de ligne). Ne pas modifier un changeset Liquibase appliqué.
- Vigilance : R-18, R-20, R-21, R-28 (dossier Drive créé par l'application, fichiers privés), R-29, R-36 ; une panne de Drive n'est jamais visible dans la réponse de validation (D-66, qui remplace D-62 sur ce point) ; CS12 et CS26 (pas de nom d'origine, pas de partage) restent vrais.
- 2026-11-10 : Issue créée par le découpage de la révision D-66 à D-70, approuvé par l'utilisateur. CA45 vient de 001-06, CA64 à CA66 de 001-05 (faites), CA46, CA47, CA49, CA50 et CA53 de 001-08 (abandonnée), CA99 du découpage approuvé. Peut se livrer en parallèle de 001-12 et de 001-13.
