---
id: 001-06
titre: Dépôt Drive et ligne écrite
prd: docs/prd/001-recus.md
besoin: F-01
statut: En revue
depend_de: [001-03, 001-05]
criteres: [CA13, CA17, CA38, CA39, CA40, CA44, CA45, CS12, CS16, CS26]
branche: feat/001-06-copie-drive-sheet
commits: []
revue:
---

## Objectif
Un reçu validé dépose ses photos sur Drive, puis écrit sa ligne dans un onglet au format habituel.

## Périmètre
- Dans : Dépôt des photos avant l'écriture de la ligne, sans attendre dans la réponse de validation
- Dans : Valeurs écrites dans la ligne, colonne PHOTO, NOTES, protection contre l'injection de formules
- Hors : Fichier et onglet selon l'année, colonnes repérées par leur en-tête, colonne PHOTO absente, placement dans une section, blocage sur une structure inattendue (001-11) ; création de sections et placement selon la date (001-07) ; reprise après panne (001-08).
- Limite : tant que 001-11 n'est pas livrée, ne pas utiliser sur un vrai Sheet.

## Critères d'acceptation
Les critères listés dans l'en-tête (`criteres`), tels que définis dans le PRD. Chaque critère doit être couvert par au moins un test
dont le nom ou le `@DisplayName` contient son identifiant (ex. `ca10_…`, `cs07_…`).

## Preuve attendue
Tests rouges puis verts avec un Sheet doublé en mémoire, `scripts/verify-clean.sh` à `VERIFY OK`.

## Notes
- 2026-11-01 : Q-07 reste à confirmer sur un fichier d'essai (section 13 du PRD).
- 2026-11-03 : Dépend aussi de 001-03 : aucune photo ne part vers Drive tant que le contenu des images n'est pas contrôlé sur toutes les routes (D-40).
- 2026-11-04 : Reporté de la relecture de 001-05 : la clause « copiés sans doublon » de CA66 et la relance immédiate de CA65 et de la section 7.6 du PRD ne sont prouvables qu'avec la copie. Les tester ici, en plus des critères de cette issue. Le premier appel à `accessToken()` arrive ici : une clé changée n'est détectée qu'à partir de cette issue.
- 2026-11-04 : Architecture (D-60) : construire ici `RecuCopyService`. Premiers commits, sans changement de comportement : déplacer vers lui les blocages de `RecuService` et de `GoogleAuthorizationService`, qui prévient par des événements Spring synchrones. Ajouter les transitions manquantes à `Recu`, le test du tableau complet de la section 4.2 du PRD, et la règle de `AgentRulesTest` qui réserve ces transitions au module. Copie en trois temps, sans transaction pendant les appels à Google ; base comme file, un seul fil ; adapters qui classent les pannes et demandent le jeton. Tests par l'interface du module : vraie base SQLite, doubles en mémoire pour Drive et le Sheet, horloge contrôlée, appel direct de « traiter le suivant ». Une panne passagère laisse le reçu `A_COPIER`, repris au prochain passage périodique. Écrire la règle dans la section Architecture d'`AGENTS.md`.
- 2026-11-05 : Relecture n° 1 : décisions de l'utilisateur consignées en D-62. Mineurs gardés : 403 de quota classé blocage et erreurs permanentes retentées chaque minute sans fin, sans `Retry-After` (à traiter dans 001-08) ; `Recu.corriger()` sans appelant de production avant 001-09 ; fil unique du `TaskScheduler` partagé avec tout futur `@Scheduled` ; ADR 0001 à aligner sur `DriveClient` et `SheetClient` ; Q-07 et `stringValue` à confirmer à l'essai réel.
- 2026-11-05 : I-1 de la relecture n° 1 tranché par l'utilisateur : le `multipart/related` de Drive reste écrit à la main, car le convertisseur de Spring ajoute à chaque partie `Content-Disposition: form-data` et `Content-Length`, absents du format documenté par Google ; raison écrite dans le Javadoc de `GoogleDriveClient`. Le convertisseur sera réessayé à l'essai réel après 001-11, sur un fichier jetable.
