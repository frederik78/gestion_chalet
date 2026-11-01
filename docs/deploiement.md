# Prérequis de déploiement

Le déploiement lui-même est reporté (backlog, NF-03). Ce fichier liste ce que l'application attend déjà.

## Environnement
| Variable | Rôle | Défaut |
|---|---|---|
| `RECUS_SECURITE_CONDENSE_MOT_DE_PASSE` | Condensé bcrypt du mot de passe du compte local, de coût 10 à 31 | aucun : l'application refuse de démarrer |
| `RECUS_SECURITE_COOKIE_SECURISE` | `false` seulement sur le poste local en HTTP | `true` |
| `ANTHROPIC_API_KEY` | Clé du modèle, lue par le client au moment de l'appel | aucune : le démarrage n'en dépend pas |

Produire le condensé, sans laisser le mot de passe dans l'historique du shell : `htpasswd -nBC 12 "" | tr -d ':\n'`. Le condensé contient des `$` : le placer entre apostrophes.

## Réglages (`recus.securite.*`, surchargeables par l'environnement)
Blocage : 5 échecs par source, 15 minutes. Inactivité de session : 8 heures. Corps de requête hors multipart : 64 Ko. Multipart (`spring.servlet.multipart.*`) : 15 Mo par fichier, 75 Mo par requête.

## À reprendre avec NF-03
- Derrière Cloudflare Tunnel, toutes les requêtes semblent venir de la même source : le blocage devra lire l'en-tête de confiance du tunnel, sans quoi il deviendrait global. Aucun en-tête `X-Forwarded-*` n'est lu aujourd'hui.
- HSTS n'est pas émis : à ajouter avec HTTPS.
- Le blocage et les sessions sont en mémoire : ils sont perdus au redémarrage.

## Essai réel du modèle (hors suite de tests)
`ANTHROPIC_API_KEY=... ./mvnw -q -Pessai-reel compile exec:java -Dphoto=/chemin/hors/depot/recu.jpg`. Options : `-Dcote.max=2400`, `-Dmodele=...`. Les photos réelles restent hors du dépôt.
