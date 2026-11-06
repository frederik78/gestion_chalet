# Backlog : Gestion de chalet

Vue d'ensemble des besoins. Détail dans `docs/prd/`, suivi de la livraison dans `docs/issues/`.

**Statuts** : `À faire` -> `En clarification` -> `Spécifié` -> `En cours` -> `En revue` -> `Fait`
**Priorités** : `P1` indispensable, `P2` important, `P3` souhaitable

## Contexte et contraintes globales
- Utilisateur : le propriétaire, seul, pour un seul chalet loué au Québec
- Volume attendu : moins de 50 reçus par mois
- Langue : français ; montants en dollars canadiens
- Contraintes fiscales : déclaration de la TPS et de la TVQ ; pièces justificatives conservées six ans après la dernière année concernée
- Pièces justificatives : numéros d'inscription du fournisseur exigés à partir de 100 $ ; nom de l'acheteur, description et conditions de paiement en plus à partir de 500 $
- Sécurité : OWASP Top 10 intégral, voir la skill `owasp-check`
- Périmètre actuel : module Reçus, premier d'une série de modules de gestion du chalet (à définir)
- Google Sheet existant : un fichier par année, plusieurs onglets ; colonnes DATE, BOUTIQUE, OBJET, TYPE, MONTANT HT, TPS, TVQ, MONTANT TOTAL, NOTES

## Besoins fonctionnels

| ID | Besoin | Priorité | Statut | PRD | Décisions |
|----|--------|----------|--------|-----|-----------|
| F-01 | Photographier un reçu avec le téléphone pour que ses valeurs (date, fournisseur, TPS et TVQ s'il y en a, montant avant taxes, montant total) et sa photo soient reportées en BD et dans le Google Sheet | P1 | En cours | `docs/prd/001-recus.md` | D-01 à D-13, D-16 à D-25 |

## Besoins non fonctionnels

| ID | Besoin | Statut |
|----|--------|--------|
| NF-01 | Sécurité OWASP Top 10, vérifiée à chaque revue | À faire |
| NF-02 | Sauvegardes de la base et des photos : à traiter plus tard, reporté à la demande de l'utilisateur | À faire |
| NF-03 | Déploiement (image Docker, serveur local, Cloudflare, nom de domaine) : reporté, le travail porte d'abord sur l'application | À faire |

## Décisions prises

| ID | Date | Décision | Besoin |
|----|------|----------|--------|
| D-01 | 2026-11-01 | Extraction par Claude Haiku 5.5 ; modèle modifiable par configuration ; qualité réévaluée quand il y aura assez de reçus | F-01 |
| D-02 | 2026-11-01 | Rien n'est enregistré avant la validation par l'utilisateur des valeurs lues, affichées à côté de la photo | F-01 |
| D-03 | 2026-11-01 | Numéros d'inscription TPS et TVQ extraits et conservés en BD seulement ; alerte s'ils manquent à partir de 100 $, alerte « pièce à vérifier » à partir de 500 $ | F-01 |
| D-04 | 2026-11-01 | Une ligne par reçu, sans détail par article ; montants corrigeables à la validation pour ne garder que la part du chalet ; alerte non bloquante si le calcul ne tombe pas juste | F-01 |
| D-05 | 2026-11-01 | La BD est la référence, le Sheet une copie : une modification manuelle dans le Sheet ne revient pas en BD ; envoi réessayé si Google est indisponible | F-01 |
| D-06 | 2026-11-01 | Pas d'import des anciennes lignes du Sheet dans la BD | F-01 |
| D-07 | 2026-11-01 | Photos déposées dans un dossier Google Drive, lien dans une nouvelle colonne PHOTO en fin de tableau, copie conservée par l'application ; NOTES ne reçoit que les alertes utiles | F-01 |
| D-08 | 2026-11-01 | Un reçu peut compter plusieurs photos | F-01 |
| D-09 | 2026-11-01 | Taxes absentes du reçu laissées vides, jamais calculées ni mises à 0 | F-01 |
| D-10 | 2026-11-01 | Le fournisseur est désigné par son nom commercial usuel, corrigeable à la validation | F-01 |
| D-11 | 2026-11-01 | Même fournisseur, même date et même total : avertissement sans blocage | F-01 |
| D-12 | 2026-11-01 | Envoi des photos à Anthropic et stockage sur Google Drive acceptés ; contenu des reçus exclu des journaux | F-01 |
| D-13 | 2026-11-01 | Application web installable (PWA) : appareil photo ou galerie, sans mode hors ligne | F-01 |
| D-14 | 2026-11-01 | Une seule application structurée en services, Spring avec Spring Data JPA, SQLite pour la preuve de concept avec changement de base prévu ensuite ; détail dans l'ADR 0001 | tous |
| D-15 | 2026-11-01 | Image Docker hébergée sur un serveur local, Dell OptiPlex Micro 3060 avec 24 Go de RAM ; à formaliser dans l'ADR 0001 | tous |
| D-16 | 2026-11-01 | Accès depuis Internet par Cloudflare Tunnel, sans port ouvert, protégé par Cloudflare Access limité au courriel de l'utilisateur ; passage des photos chez Cloudflare accepté | F-01 |
| D-17 | 2026-11-01 | Un seul compte local avec mot de passe fort dans l'application, en plus de Cloudflare Access ; autorisation Google donnée une seule fois, séparément | F-01 |
| D-18 | 2026-11-01 | Fichier et onglet choisis selon l'année de la date du reçu, par une correspondance en configuration (2026 : onglet « Comptes 2026 ») ; année non configurée : reçu conservé en BD, alerte, envoi repris ensuite | F-01 |
| D-19 | 2026-11-01 | Valeurs validées écrites telles quelles dans le Sheet, sans jamais écraser une formule | F-01 |
| D-20 | 2026-11-01 | Liste fermée des types de dépense, orthographe du Sheet : Honoraires professionnel, Fournitures, Entretien / Réparations, Électricité / Internet, Impôts / Taxes, Assurances, Publicité, Autres dépenses, Frais de voyage, Intérêts, Administration | F-01 |
| D-21 | 2026-11-01 | Première version limitée aux images (photo ou capture importée) ; PDF plus tard ; aucun total de déclaration calculé depuis la BD tant que des lignes sont saisies à la main dans le Sheet | F-01 |
| D-22 | 2026-11-01 | Date, fournisseur, montant total et type de dépense obligatoires à la validation | F-01 |
| D-23 | 2026-11-01 | Alerte de calcul seulement au-delà de 0,02 $ d'écart entre montant avant taxes plus taxes et montant total | F-01 |
| D-24 | 2026-11-01 | Section d'un mois créée à l'arrivée de son premier reçu, à sa place parmi les mois ; nouvelle ligne insérée selon sa date dans la section ; lignes existantes jamais déplacées | F-01 |
| D-25 | 2026-11-01 | Montants stockés en cents entiers ; photos stockées en fichiers, hors de la base | F-01 |
| D-26 | 2026-11-01 | ADR 0001 accepté : structure JHipster à plat sans générateur, Spring AI 2.0.x pour appeler Claude, à confirmer par un essai réel en première issue, SDK officiel en repli | tous |
| D-27 | 2026-11-01 | Issues indépendantes livrées en parallèle, jusqu'à 3 à la fois | F-01 |
| D-28 | 2026-11-01 | Connexion façon JHipster : `formLogin` et `logout` de Spring Security, formulaire au lieu de JSON, déconnexion 204 même sans session | F-01 |
| D-29 | 2026-11-02 | Connexion : pas de limite de 1 000 caractères sur le mot de passe ; cas limites acceptés (vide ou absent 401, JSON 415, paramètre d'URL 400, sans longueur déclarée 413, corps jamais terminé fermé à 60 s) | F-01 |
| D-30 | 2026-11-02 | Les classes techniques sont impérativement en anglais | F-01 |
| D-31 | 2026-11-02 | `archunit-junit5` et `@ArchTest` comme JHipster ; `@Component` conservé sur `DomainUserDetailsService` ; deux défenses de taille sur la connexion, avec une seule valeur | F-01 |
| D-32 | 2026-11-02 | Essai réel validé : Spring AI et Claude Haiku 5.5 retenus | F-01 |
| D-33 | 2026-11-02 | Connexion réussie : compteur d'échecs de la source remis à zéro ; méthode non permise sur une route existante : 404 sans `Allow`, `OPTIONS` excepté | F-01 |
| D-34 | 2026-11-02 | `liquibase-core` 5.0.4, sous licence FSL-1.1-ALv2, conservé | F-01 |
| D-35 | 2026-11-02 | Chrome installé dans la CI pour que les tests d'écran y tournent réellement ; pilote de test écrit avec le JDK conservé | F-01 |
| D-36 | 2026-11-02 | Renvoi d'une clé connue avec un contenu différent : 409 `CLE_DEJA_UTILISEE`, reçu inchangé, utilisateur averti | F-01 |
| D-37 | 2026-11-03 | Tests d'écran en CI avec le Chrome de l'image du runner, sans téléchargement ; sa version suit l'image ; `sysctl` AppArmor gardé tant qu'un passage réel n'a pas montré qu'il est inutile | F-01 |
| D-38 | 2026-11-03 | `service/validation` inscrit à la structure de l'ADR 0001, pour les validateurs Bean Validation propres au métier | F-01 |
| D-39 | 2026-11-03 | `spring-boot-starter-data-jpa-test` (Apache-2.0), scope test, pour `@DataJpaTest` exigé par l'ADR 0001 | F-01 |
| D-40 | 2026-11-03 | `POST /api/recus` sécurisé sans attendre 001-03 : contenu réel, nombre, taille et dimensions des photos contrôlés avant toute écriture, sur tous les chemins ; textes reçus en JSON strictement en chaînes, caractères de format refusés ; 001-06 dépend de 001-03 | F-01 |
| D-41 | 2026-11-04 | Image tronquée après son en-tête acceptée, sans décodage ; image valide suivie d'un autre contenu conservée, à servir plus tard sous le type de son format avec `nosniff` ; requête totale à 76 Mo ; numéro d'inscription fait d'espaces rendu vide ; bouton de retour de l'écran de validation dans 001-03 | F-01 |
| D-42 | 2026-11-04 | Sans identifiants Google, l'application démarre en `ABSENTE` ; la clé de chiffrement reste obligatoire. Consentement partiel : `A_RENOUVELER`, jetons effacés. Clé de chiffrement changée : `A_RENOUVELER` | F-01 |
| D-43 | 2026-11-04 | Saisie sans session : `server.tomcat.connection-timeout` raccourci à 20 s, après mesure ; le reste de la protection (proxy) relève de NF-03, à traiter avant toute exposition publique | F-01 |
| D-44 | 2026-11-04 | Échec de renouvellement Google qui n'est pas une panne passagère au sens de R-20 (`invalid_client`, par exemple) : l'autorisation passe à `A_RENOUVELER`, avec un journal d'erreur | F-01 |
| D-45 | 2026-11-04 | Identifiants Google retirés après autorisation : jetons conservés et chiffrés, état `ABSENTE`, retour à `VALIDE` sans nouveau consentement quand ils reviennent | F-01 |
| D-46 | 2026-11-04 | Reçus bloqués pendant ce retrait : ils restent `BLOQUEE` jusqu'à la reprise de 001-08 | F-01 |
| D-47 | 2026-11-04 | Clé de chiffrement changée : détectée au premier usage et non au démarrage, l'autorisation passe alors à `A_RENOUVELER` | F-01 |
| D-48 | 2026-11-04 | Photos changées après une lecture : le montant lu est invalidé et la relance proposée, la saisie reste intacte | F-01 |
| D-49 | 2026-11-04 | Image acceptée par le contrôle mais non décodable, ou côté source au-delà de 20000 px : la lecture finit en `ECHEC`, saisie à la main | F-01 |
| D-50 | 2026-11-04 | Limite de lectures perdue au redémarrage ; une photo refusée ne compte pas, une lecture en `ECHEC` compte | F-01 |
| D-51 | 2026-11-04 | La relance ne remplit que les champs vides ; orientation lue pour le seul JPEG ; long côté de 1568 px, à confirmer sur un long reçu | F-01 |
| D-52 | 2026-11-04 | Mémoire native au-delà de `-Xmx` à la lecture (776 Mo mesurés sous `-Xmx256m`) : reporté à NF-03, à régler avant tout déploiement | F-01 |
| D-53 | 2026-11-04 | Le vrai appel à Haiku avec de vraies photos, lancé par l'utilisateur, précède le passage de 001-03 à `Fait` | F-01 |
| D-54 | 2026-11-04 | Contrôle préalable : aucun contrôle sur une saisie incomplète ; s'il échoue, message et validation possible ; `recuId` accepté ; aucune alerte rétroactive ; écart sans séparateur de milliers | F-01 |
| D-55 | 2026-11-04 | Comparaison des fournisseurs : `œ`, apostrophe courbe et espace insécable en bordure traités par un correctif après la fusion de 001-04 | F-01 |
| D-56 | 2026-11-04 | Risques acceptés : aucune limite de débit sur le contrôle préalable ; doublons simultanés détectés grâce au lien unique vers la base, à écrire dans `docs/deploiement.md` ; le contrôle peut signaler un reçu déjà créé après une réponse perdue | F-01 |
| D-57 | 2026-11-04 | Remplace D-44. Renouvellement Google : `invalid_grant` ou `invalid_token` efface le jeton ; réponse illisible, `temporarily_unavailable` et `server_error` sont des pannes passagères ; `invalid_client` et les autres refus passent à `A_RENOUVELER` avec un journal d'erreur, sans effacer le jeton, pour reprendre sans nouveau consentement | F-01 |
| D-58 | 2026-11-04 | Le développeur de chaque issue peut rebaser sa branche non poussée sur `develop`, une fois par fusion précédente, puis refait la vérification complète ; un conflit de sécurité lui fait abandonner le rebase et le remonter | F-01 |
| D-59 | 2026-11-04 | Renouvellement Google : un 401 ou 403 sans code lisible passe à `A_RENOUVELER` sans effacer le jeton, avec bandeau et journal d'erreur ; un 200 illisible reste passager. 001-05 se termine sur cette correction, sans 4e relecture | F-01 |
| D-60 | 2026-11-04 | Cycle de la copie réuni dans `RecuCopyService`, construit dans 001-06 sans branche préalable, complété par 001-11 et 001-08 : les appelants signalent des faits, le module décide de l'ordre, des pannes et des essais. `Recu` seul change son état et refuse les transitions interdites, sans bibliothèque, avec un test du tableau complet ; une règle d'architecture réserve ces transitions au module. L'autorisation Google prévient par des événements Spring synchrones, dans la même transaction. Aucune transaction pendant un appel à Google. La base sert de file, un seul fil, réveillé après chaque validation et périodiquement. Les adapters de Drive et du Sheet classent les pannes et demandent le jeton. Rythme des essais dans 001-08, durées dans `recus.copie.*`. Pas de nouvel ADR : la structure ne change pas | F-01 |
| D-61 | 2026-11-05 | Texte vide et comparaison des fournisseurs réunis dans une seule classe du domaine, sans annotation de validation ni nouvel ADR ; la validation, la lecture du modèle et la comparaison l'appellent. Comparaison (complète D-55) : en plus de la casse, des accents et des espaces en bordure, `œ` = `oe`, `æ` = `ae` (majuscules comprises), apostrophe courbe = apostrophe droite, séparateurs `\p{Z}` dont l'espace insécable en bordure ; rien d'autre (ni ponctuation, ni `Inc.` / `Inc`, ni séparateur du milieu) ; la valeur enregistrée et affichée ne change pas. Un fournisseur fait seulement de séparateurs renvoyé par le modèle est vide : non relevé, non rejeté, et il ne compte pas pour un résultat `LU` | F-01 |
| D-62 | 2026-11-05 | Copie sans fichier ou onglet du Sheet configuré : aucun appel à Google, aucune photo déposée ; le reçu reste en base, `BLOQUEE` motif `FICHIER_INACCESSIBLE`, et la validation affiche « Reçu enregistré. Copie vers Google en attente : fichier ou onglet du Sheet non configuré. » ; la reprise après configuration relève de 001-08. Erreurs de Google pendant la copie : 401 passagère, 403 et 404 bloquent en `FICHIER_INACCESSIBLE` (403 de quota à distinguer dans 001-08) ; liens PHOTO séparés par « , » ; date affichée « 16 juin 2026 » ; passage périodique toutes les minutes jusqu'à 001-08 | F-01 |
| D-63 | 2026-11-05 | Sheet non configuré et autorisation Google absente à la fois : le motif `FICHIER_INACCESSIBLE` l'emporte sur `AUTORISATION_GOOGLE`, car autoriser Google ne permet aucune copie tant que le fichier et l'onglet manquent ; la reprise relève de 001-08 | F-01 |
| D-64 | 2026-11-05 | Lecture indépendante du fournisseur du modèle : aucune classe ne dépend d'Anthropic (`ChatModelRecuExtractor`), schéma de réponse imposé par l'interface commune de Spring AI (`StructuredOutputChatOptions`), pannes classées par la chaîne de leurs causes (`IOException` ou délai : `UNREACHABLE` ; autre erreur de l'appel : `SERVICE_ERROR` ; réponse vide ou illisible : `INVALID_RESPONSE`) ; changer de modèle ne demande que la dépendance Spring AI et la configuration `spring.ai.*` ; règle d'architecture à l'appui (`AgentRulesTest`). Un corps de réponse de forme inattendue passe de `INVALID_RESPONSE` à `SERVICE_ERROR`. Un modèle dont les options n'imposent pas de schéma de réponse fait refuser le démarrage ; aucun garde-fou contre un réglage de tirage au-delà du test qui vérifie qu'aucun ne part | F-01 |
| D-65 | 2026-11-06 | Copie par année (001-11) : aucune correspondance utilisable, D-62 s'applique (aucun appel à Google, `FICHIER_INACCESSIBLE`) ; correspondance présente mais pas pour l'année du reçu, photos déposées et Sheet non appelé, `ANNEE_NON_CONFIGUREE` (CA32) ; année absente et autorisation absente, `AUTORISATION_GOOGLE`. Entrée de configuration incomplète ignorée, sans échec au démarrage. Jusqu'à 001-07 : mois sans section bloqué `STRUCTURE_SHEET`, ligne placée en fin de section ; titre de mois = seule cellule non vide d'une ligne, en première colonne ; deux sections d'un même mois ou un en-tête en double bloquent l'onglet. Essai réel sur une copie jetable du Sheet avant tout usage sur les vrais comptes (Q-07, titres de mois, liste de TYPE, total sous décembre) | F-01 |

## À trancher

| Sujet | Besoin |
|-------|--------|
| Destruction des reçus papier après numérisation : règle de Revenu Québec non confirmée, les conserver d'ici là | F-01 |
