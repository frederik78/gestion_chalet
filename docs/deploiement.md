# Prérequis de déploiement

Le déploiement lui-même est reporté (backlog, NF-03). Ce fichier liste ce que l'application attend déjà.

## Environnement
| Variable | Rôle | Défaut |
|---|---|---|
| `RECUS_SECURITE_CONDENSE_MOT_DE_PASSE` | Condensé bcrypt du mot de passe du compte local, de coût 10 à 31 | aucun : l'application refuse de démarrer |
| `RECUS_SECURITE_COOKIE_SECURISE` | `false` seulement sur le poste local en HTTP | `true` |
| `ANTHROPIC_API_KEY` | Clé du modèle, lue par le client au moment de l'appel | aucune : le démarrage n'en dépend pas |

Produire le condensé, sans laisser le mot de passe dans l'historique du shell : `htpasswd -nBC 12 "" | tr -d ':\n'`. Le condensé contient des `$` : le placer entre apostrophes.

bcrypt ne prend en compte que les 72 premiers octets du mot de passe : ce qui suit est ignoré, quelle que soit sa longueur (formulaire borné à 64 Ko). Choisir un mot de passe fort dans cette limite.

## Réglages (`recus.securite.*`, surchargeables par l'environnement)
Blocage : 5 échecs par source, 15 minutes. Inactivité de session : 8 heures (`server.servlet.session.timeout`). Corps de requête hors multipart : 64 Ko ; formulaire lu par Tomcat : 64 Ko (`server.tomcat.max-http-form-post-size`). Multipart (`spring.servlet.multipart.*`) : 15 Mo par fichier, 75 Mo par requête, et refusé sans session. Filtres de formulaire de Spring désactivés, car ils liraient le corps avant la sécurité : `spring.mvc.formcontent.filter.enabled`, et `spring.mvc.hiddenmethod.filter.enabled`, dont c'est déjà le défaut.

## À reprendre avec NF-03
- Derrière Cloudflare Tunnel, toutes les requêtes semblent venir de la même source : le blocage devra lire l'en-tête de confiance du tunnel, sans quoi il deviendrait global. Aucun en-tête `X-Forwarded-*` n'est lu aujourd'hui.
- HSTS n'est pas émis : à ajouter avec HTTPS.
- Le blocage et les sessions sont en mémoire : ils sont perdus au redémarrage.
- 200 connexions sans session qui annoncent un corps jamais envoyé rendent `/actuator/health` injoignable (mesuré : délai de 15 s dépassé). Cause probable, non lue dans le code : Tomcat consomme le reste du corps après la réponse. À traiter avec le proxy et `connection-timeout`.

## Essai réel du modèle (hors suite de tests)
La clé ne s'écrit jamais en clair dans la ligne de commande, qui resterait dans l'historique du shell. Sous macOS, depuis le trousseau :

```
export ANTHROPIC_API_KEY="$(security find-generic-password -a "$USER" -s anthropic-api-key -w)"
./mvnw -q -Pessai-reel compile exec:java -Dphoto=/chemin/hors/depot/recu.jpg
```

Options : `-Dcote.max=2400`, `-Dmodele=...`. Les photos réelles restent hors du dépôt.
