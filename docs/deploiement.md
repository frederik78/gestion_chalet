# Prérequis de déploiement

Le déploiement lui-même est reporté (backlog, NF-03). Ce fichier liste ce que l'application attend déjà.

## Environnement
`.env.example` (versionné, placeholders seulement) sert de modèle : le copier en `.env`, non versionné.

| Variable | Rôle | Défaut |
|---|---|---|
| `RECUS_SECURITE_CONDENSE_MOT_DE_PASSE` | Condensé bcrypt du mot de passe du compte local, de coût 10 à 31 | aucun : l'application refuse de démarrer |
| `RECUS_GOOGLE_CLIENT_ID`, `RECUS_GOOGLE_CLIENT_SECRET` | Identifiants du client OAuth2 du projet Google, dont l'adresse de rappel autorisée est `<adresse de la PWA>/api/google/rappel`. Facultatifs : sans eux, l'autorisation reste `ABSENTE`, les reçus sont enregistrés et bloqués, et `GET /api/google/autorisation` répond 503 | aucun |
| `RECUS_GOOGLE_CLE_CHIFFREMENT` | Clé des jetons Google au repos : 32 octets aléatoires en base 64 (`openssl rand -base64 32`). La perdre ou la changer fait passer l'autorisation à `A_RENOUVELER` au premier usage du jeton : il faut autoriser Google de nouveau | aucun : l'application refuse de démarrer |
| `SPRING_PROFILES_ACTIVE` | `dev` seulement sur le poste ou le réseau local en HTTP (voir « Modes dev et prod ») ; jamais en production | aucun : mode prod |
| `RECUS_SECURITE_COOKIE_SECURISE` | Surcharge ponctuelle de `recus.securite.cookie-securise` (le profil `dev` le met à `false`) | `true` |
| `RECUS_DOSSIER_DONNEES` | Dossier de la base SQLite et, par défaut, des photos (`photos/`) ; à placer sur un volume conservé, hors de la racine web | `data` |
| `ANTHROPIC_API_KEY` | Clé du modèle, lue par le client au moment de l'appel | aucune : le démarrage n'en dépend pas |

Produire le condensé, sans laisser le mot de passe dans l'historique du shell : `htpasswd -nBC 12 "" | tr -d ':\n'`. Le condensé contient des `$` : le placer entre apostrophes.

bcrypt ne prend en compte que les 72 premiers octets du mot de passe : ce qui suit est ignoré, quelle que soit sa longueur (formulaire borné à 64 Ko). Choisir un mot de passe fort dans cette limite.

## Modes dev et prod
- **Prod** (sans profil) : cookies de session et CSRF `Secure`, donc HTTPS obligatoire (sauf `localhost`, que les navigateurs traitent comme sécurisé).
- **Dev** (`SPRING_PROFILES_ACTIVE=dev`, ou `-Dspring-boot.run.profiles=dev`) : `application-dev.yml` met `recus.securite.cookie-securise` à `false`, pour se connecter en HTTP sur une adresse du réseau local (téléphone, `http://192.168.50.115:8080`). L'application journalise `MODE DEV ACTIF` au démarrage. Lancement : `set -a; source .env; set +a; SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run`.
- **Jamais dev en production.** Rien d'autre n'est assoupli : les en-têtes de sécurité sont identiques dans les deux modes (ni HSTS ni `upgrade-insecure-requests` n'est émis aujourd'hui).
- Hors contexte sécurisé, le navigateur n'offre ni service worker ni installation de la PWA ; la page fonctionne sans.

## Réglages (`recus.securite.*`, surchargeables par l'environnement)
Blocage : 5 échecs par source, 15 minutes. Inactivité de session : 8 heures (`server.servlet.session.timeout`). Corps de requête hors multipart : 64 Ko ; formulaire lu par Tomcat : 64 Ko (`server.tomcat.max-http-form-post-size`). Multipart (`spring.servlet.multipart.*`) : 15 Mo par fichier, 75 Mo par requête, et refusé sans session. Filtres de formulaire de Spring désactivés, car ils liraient le corps avant la sécurité : `spring.mvc.formcontent.filter.enabled`, et `spring.mvc.hiddenmethod.filter.enabled`, dont c'est déjà le défaut.

## Concurrence des validations
Le lien unique vers la base (`maximum-pool-size: 1`) garantit l'idempotence de deux envois simultanés. Le hachage et l'écriture des photos ont lieu dans la transaction : le lien reste tenu 149 à 439 ms pour 5 photos de 14 Mo (mesuré). Un refus d'unicité arrive en `JpaSystemException` avec le dialecte SQLite, et non en `DataIntegrityViolationException`. Ces choix sont à revoir si SQLite est remplacé.

Ce même lien unique permet à deux validations simultanées de se voir comme doublons possibles : relever ce réglage ou remplacer SQLite demande de revoir cette détection, faute de quoi aucune alerte de doublon ne serait levée entre elles (D-56, 15 tours de 4 validations simultanées mesurés).

## Google Drive et Sheet (copie des reçus)
- Le dossier Drive « Reçus du chalet » est créé par l'application au premier dépôt. Un dossier du même nom créé à la main reste invisible pour elle (droit `drive.file`, limité aux fichiers qu'elle crée) et reste vide : ne pas le créer, ne pas y chercher les photos.
- Le fichier configuré (`RECUS_COPIE_ANNEES_<année>_FICHIER`) doit être un vrai Google Sheet, pas un `.xlsx` stocké dans Drive, que l'API Sheets refuse (400). Un `.xlsx` s'ouvre dans Google Sheets, puis Fichier › Enregistrer au format Google Sheets ; le nouveau fichier a un nouvel identifiant, à reporter dans la configuration.

## À reprendre avec NF-03
- Derrière Cloudflare Tunnel, toutes les requêtes semblent venir de la même source : le blocage devra lire l'en-tête de confiance du tunnel, sans quoi il deviendrait global. Aucun en-tête `X-Forwarded-*` n'est lu aujourd'hui.
- HSTS n'est pas émis : à ajouter avec HTTPS.
- Le blocage et les sessions sont en mémoire : ils sont perdus au redémarrage.
- Limite à reprendre avec NF-03 : 250 connexions sans session qui annoncent un corps jamais envoyé rendent `/actuator/health` injoignable le temps de `server.tomcat.connection-timeout` (mesuré : 58 s avec le défaut de 60 s, 20 s au réglage de 20 s, puis reprise seule) ; une pause de plus de 20 s dans un envoi le fait échouer.

## Essai réel du modèle (hors suite de tests)
La clé ne s'écrit jamais en clair dans la ligne de commande, qui resterait dans l'historique du shell. Sous macOS, depuis le trousseau :

```
export ANTHROPIC_API_KEY="$(security find-generic-password -a "$USER" -s anthropic-api-key -w)"
./mvnw -q -Pessai-reel compile exec:java -Dphoto=/chemin/hors/depot/recu.jpg
```

Options : `-Dcote.max=2400`, `-Dmodele=...`. Les photos réelles restent hors du dépôt.
