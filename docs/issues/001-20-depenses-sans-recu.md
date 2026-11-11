---
id: 001-20
titre: Dépenses sans reçu, ajouts saisis dans le Sheet
prd: docs/prd/001-recus.md
besoin: F-01
statut: À faire
depend_de: [001-19]
criteres: [CA141, CA142, CA143, CA144, CA145, CA146, CA147, CA148, CA149, CA150, CA151, CA152, CA153, CA154, CS38, CS39, CS40]
criteres_justification: Une seule tranche : la lecture des lignes sans ID, la validation de leurs cellules, le choix, la création et l'écran forment un seul parcours (sans l'écran, l'utilisateur ne peut pas choisir ; sans la création, la lecture ne sert à rien). Elle réutilise les mêmes doubles, la même relecture et la même enveloppe tout ou rien que 001-18 et 001-19. CS38 à CS40 vérifient ce parcours sous l'angle de la sécurité.
branche: feat/001-20-depenses-sans-recu
commits: []
revue:
---

## Objectif
Une dépense sans reçu (abonnement, prélèvement) se saisit sur une ligne sans ID de l'onglet généré. L'import la propose comme un ajout ; l'utilisateur l'accepte ou la refuse explicitement ; accepté, l'ajout crée une dépense sans reçu : même registre que les reçus, marquée « sans pièce », sans photo, avec les mêmes alertes et l'alerte `SANS_PIECE` (D-73).

## Périmètre
- Dans : lecture des ajouts : une ligne sans ID (ou à ID fait d'espaces) avec un contenu dans DATE à PHOTO devient un ajout, rendu dans `ajouts` de `GET /api/sheet/differences` avec son année, son numéro de ligne, ses valeurs, ses alertes et son doublon éventuel ; ligne vide jamais signalée ; ligne à ID non vide jamais un ajout ; NOTES et PHOTO ni importées ni signalées ; seuls les onglets générés des années configurées sont lus (CA141, CA154, R-64, R-61).
- Dans : validation des cellules d'un ajout avec les règles de la section 7.7 (formule refusée, montant illisible, date future, type hors liste, DATE, BOUTIQUE, MONTANT TOTAL et TYPE obligatoires) et date dans l'année du fichier (`HORS_ANNEE`) ; une ligne dont une cellule est refusée est rendue dans `refusees` (avec `annee` et `ligne`, `recuId` à `null`), jamais créée (CA144, CA145, R-56, R-64).
- Dans : doublon montré avant le choix (même comparaison que CA27, aux reçus de la base et entre les ajouts de la lecture), jamais bloquant ; recalculé à la création ; un ajout dont la régénération a échoué est reproposé avec son doublon (CA146, R-67, R-68).
- Dans : choix explicite par ajout, aucun présélectionné ; corps de `POST /api/sheet/import` étendu (`ajouts`, une entrée par ajout, `annee` et `ligne`) ; 400 `VALIDATION` si un choix manque, est inconnu ou mal formé ; valeurs relues du Sheet à l'envoi ; 409 `ONGLET_MODIFIE` (l'empreinte couvre les ajouts), `VERSION_PERIMEE`, `REGENERATION_EN_COURS` ; envoi tout ou rien avec les corrections (CA151, CA143, R-65, Q-34).
- Dans : création : dépense sans reçu, indicateur `sansPiece` calculé par le serveur et non modifiable, aucune photo (exception à R-06), version 1, alertes de tout reçu plus `SANS_PIECE` quand la TPS ou la TVQ n'est pas vide (un 0 compte comme renseigné), aucun appel à Drive ni au modèle ; ID écrit à la régénération suivante, puis modification par l'import comme un reçu (CA142, CA147, CA148, R-66, R-68, R-70).
- Dans : aucune suppression par le Sheet : `LIGNE_SUPPRIMEE`, comme pour un reçu ; colonne ID effacée par erreur : chaque ligne est un ajout avec son doublon signalé et chaque reçu `LIGNE_SUPPRIMEE`, rien n'est présélectionné (CA149, CA150, R-68, R-69).
- Dans : consultation : liste et détail montrent la dépense avec l'indicateur « Sans pièce » et sans photo ; `sansPiece` dans l'objet reçu ; la route des photos répond 404 ; aucune création sans photo dans l'application (CA153).
- Dans : écran des différences : section « Ajouts » (texte, jamais HTML), doublon signalé, deux choix sans présélection, bouton désactivé tant qu'un choix manque, un ajout compte comme « quelque chose à décider » (CA152, Q-35).
- Dans : sécurité : cellules d'une ligne sans ID traitées comme une sortie du modèle (CS38) ; le client ne décide ni des valeurs, ni de l'indicateur, ni des alertes (CS39) ; journaux sans valeur de cellule (CS40).
- Hors : lecture et classement des différences, `ID_INCONNU` et autres éléments ignorés (001-18) ; corrections des différences, empreinte, version (001-19) ; création d'une dépense sans photo dans l'application, ajout d'une photo, suppression d'un reçu, répétition automatique d'un abonnement (hors périmètre du PRD).

## Critères d'acceptation
Les critères listés dans l'en-tête (`criteres`), tels que définis dans le PRD (section 10, « Dépenses sans reçu »). Chaque critère doit être couvert par au moins un test
dont le nom ou le `@DisplayName` contient son identifiant (ex. `ca141_…`, `cs38_…`).

## Preuve attendue
Tests rouges puis verts avec le Sheet doublé (cellules typées, lignes sans ID, trace des lectures et des écritures), la base SQLite réelle, un serveur réel à port aléatoire pour les journaux (CS40) et un navigateur sans tête pour CA152, CA153 et CS38 ; `scripts/verify-clean.sh` à `VERIFY OK`.

## Notes
- Seams : règles métier sans application (validation des cellules d'un ajout, doublon, alerte `SANS_PIECE`) ; cas d'usage avec le Sheet doublé ; contrat HTTP (corps d'envoi, objet reçu) ; navigateur sans tête pour l'écran.
- Vigilance : R-64 à R-70 ; OWASP : A03 (cellule d'une ligne sans ID, texte hostile, formule), A04 (le corps du client n'impose ni valeur, ni état, ni `sansPiece`, aucune création sans choix explicite), A01 (une ligne sans ID n'atteint aucun reçu existant), A09 (journaux). La création réutilise la validation et le calcul des alertes de 001-09, 001-10 et 001-12 : ne pas les dupliquer. Le nombre d'ajouts d'une lecture n'a pas de borne propre (risque accepté, section 11) : il reste borné par la taille de l'onglet lu et par les 256 Ko du corps.
- Le PRD porte trois questions en attente (Q-50 à Q-52, section 15.8), avec leur lecture provisoire marquée dans le texte : à relayer avant la livraison.
- Jusqu'à la livraison de cette issue, 001-18 signale une ligne sans ID mais avec contenu comme `LIGNE_AJOUTEE` (transitoire) ; cette nature disparaît ici, avec ses tests éventuels. CA137 (001-19) reste vrai tel quel ; CA152 le complète pour les ajouts.
- 2026-11-10 : Issue créée pour D-73 (révision du PRD). Choix de découpage : une seule issue pour les 17 critères nouveaux, car la lecture, la création et l'écran ne se livrent pas séparément ; CA121 (reformulé : `ID_INCONNU` seulement) reste dans 001-18, car c'est une lecture sans création, et ses lignes sans ID passent aux critères d'ici.
