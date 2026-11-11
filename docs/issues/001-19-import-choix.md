---
id: 001-19
titre: Import du Sheet, choix et application
prd: docs/prd/001-recus.md
besoin: F-01
statut: À faire
depend_de: [001-09, 001-18]
criteres: [CA116, CA117, CA118, CA125, CA127, CA128, CA129, CA135, CA137, CS32, CS33, CS34]
criteres_justification: Le serveur (choix, application, concurrence) et l'écran des différences forment une seule tranche livrable : sans l'écran, l'utilisateur ne peut pas choisir ; sans le serveur, l'écran n'a rien à envoyer. CS32 à CS34 vérifient ce même parcours sous l'angle de la sécurité.
branche: feat/001-19-import-choix
commits: []
revue:
---

## Objectif
L'utilisateur accepte ou refuse chaque différence lue dans le Sheet ; les différences acceptées deviennent des corrections de reçus, puis la régénération suit. L'écran remplace l'avertissement transitoire du bouton (D-69, D-70).

## Périmètre
- Dans : `POST /api/sheet/import` avec `empreinte` et un choix `ACCEPTER` ou `REFUSER` par différence rendue, et pour elles seules ; 400 `VALIDATION` (`OBLIGATOIRE`, `HORS_LISTE`, `FORMAT`) pour un choix manquant, inconnu, mal formé ou une empreinte absente, sans rien appliquer (CA127, R-57) ; les valeurs appliquées sont celles que le serveur relit du Sheet, jamais celles du corps (CS33).
- Dans : application : une seule correction par reçu qui regroupe ses cellules acceptées, par les règles de toute correction (R-29, R-58) : validations, alertes recalculées dont celles de taxes, version incrémentée, date de correction, numéros d'inscription, montant total lu et photos inchangés, aucun appel à Drive ni au modèle (CA116, CA128) ; vide du Sheet devenu valeur absente, 0 resté 0, champ obligatoire vide refusé (CA129, R-08) ; changement d'année : le reçu passe d'un onglet à l'autre, ou n'est plus dans aucun et l'année est signalée (CA128, Q-22) ; refus d'une différence : base inchangée, valeur du Sheet écrasée à la régénération (CA117).
- Dans : conflit : base changée depuis la dernière régénération avec une autre valeur que le Sheet, les deux valeurs montrées ; décision cellule par cellule (CA118, R-54).
- Dans : concurrence : relecture de l'onglet à l'envoi et comparaison à l'empreinte de la lecture, 409 `ONGLET_MODIFIE` même pour une seule cellule ou après une régénération intercalée ; 409 `VERSION_PERIMEE` si la version d'un reçu n'est plus la courante ; tout ou rien : si une correction est refusée, aucune n'est appliquée et aucune régénération n'est demandée (CA125, R-58, R-59, Q-34).
- Dans : la régénération qui suit lit la base après les corrections ; si elle échoue, les corrections restent en base et une cellule acceptée n'est plus une différence, tandis qu'un refus réapparaît (CA135, R-60).
- Dans : écran : le clic appelle d'abord `GET /api/sheet/differences` sans avertissement ; sans rien à décider, envoi immédiat sans choix ; sinon liste par reçu, valeurs en texte jamais en HTML, conflit avec ses deux valeurs, aucun choix présélectionné, « Appliquer et mettre à jour » désactivé tant qu'un choix manque, valeurs refusées et éléments ignorés indiqués comme perdus, « Annuler » sans effet, un seul envoi même au double clic, invitation à relire sur 409, motif français sur échec de lecture, « Mettre à jour sans importer » avec la confirmation de CA114 (CA137, R-62, R-63, Q-35, Q-36).
- Dans : sécurité : textes hostiles (HTML, `' OR 1=1--`, `-2+3`, `@SUM(1)`) acceptés comme texte littéral, affichés sans exécution de script, réécrits comme textes et non comme formules, caractères de contrôle refusés (CS32) ; journaux sans valeur de cellule, sans nom de fichier ni d'onglet, avec un événement et son code pour chaque envoi refusé, lecture en échec et import réussi (CS34).
- Hors : lecture, comparaison et classement (001-18) ; régénération et état (001-13, 001-15) ; création ou suppression d'un reçu par l'import (hors périmètre du PRD, sauf les ajouts de D-73, livrés par 001-20 : choix des ajouts, création des dépenses sans reçu, section « Ajouts » de l'écran).

## Critères d'acceptation
Les critères listés dans l'en-tête (`criteres`), tels que définis dans le PRD. Chaque critère doit être couvert par au moins un test
dont le nom ou le `@DisplayName` contient son identifiant (ex. `ca10_…`, `cs07_…`).

## Preuve attendue
Tests rouges puis verts avec le Sheet doublé, la base SQLite réelle et un navigateur sans tête pour CA137 et CS32 ; `scripts/verify-clean.sh` à `VERIFY OK`.

## Notes
- Seams : cas d'usage et contrat HTTP pour le serveur ; serveur réel à port aléatoire pour les journaux (CS34) ; navigateur sans tête pour l'écran.
- Vigilance : R-57 à R-60, R-63 ; OWASP : A04 et A08 (le corps du client n'impose ni valeur, ni état, ni alerte, CS33), A03 (injection par le contenu du Sheet), A09 (journaux). Les corrections réutilisent le code de correction livré par 001-09 : ne pas le dupliquer.
- 2026-11-10 : Issue créée par le découpage de la révision D-66 à D-70, approuvé par l'utilisateur. CA135 et CA137 sont ici comme dans la forme approuvée ; CA131 à CA134 et CA136 (lecture seule) sont dans 001-18 ; CS32 à CS34 (acceptation de choix, parcours complet) ici, CS35 et CS36 dans 001-18.
- 2026-11-10 (D-73) : aucun critère ne change. 001-20 étend ensuite le corps de l'envoi (`ajouts`), l'écran (section « Ajouts », « rien à décider » qui compte aussi les ajouts, CA152) et l'enveloppe tout ou rien (créations avec les corrections) : garder le corps de l'envoi, la validation des choix et la transaction d'application extensibles sans les dupliquer.
