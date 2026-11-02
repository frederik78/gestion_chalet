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
| F-01 | Photographier un reçu avec le téléphone pour que ses valeurs (date, fournisseur, TPS et TVQ s'il y en a, montant avant taxes, montant total) et sa photo soient reportées en BD et dans le Google Sheet | P1 | Spécifié | `docs/prd/001-recus.md` | D-01 à D-13, D-16 à D-25 |

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

## À trancher

| Sujet | Besoin |
|-------|--------|
| Destruction des reçus papier après numérisation : règle de Revenu Québec non confirmée, les conserver d'ici là | F-01 |
