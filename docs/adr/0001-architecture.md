# 0001. Stack et architecture en services

- Statut : accepté
- Date : 2026-11-01

## Contexte
L'application sert un seul utilisateur, propriétaire d'un chalet loué au Québec, avec moins de 50 reçus par mois. Le module Reçus est le premier d'une série encore à définir. Trois services externes sont utilisés : Claude pour lire les reçus, Google Sheets et Google Drive pour la copie des données et des photos. Le modèle de lecture doit pouvoir changer facilement, et SQLite est un choix de preuve de concept qui sera remplacé.

Le déploiement et les sauvegardes sont reportés (backlog, NF-02 et NF-03). `factory/LESSONS.md` (n° 1 et n° 3) rapporte qu'un projet précédent a abandonné l'hexagone pour une structure JHipster, après des ports à implémentation unique et trois refactors coûteux. Cette structure sert de point de départ, sans être recopiée telle quelle.

## Options comparées
| Option | Avantage | Inconvénient | Décision |
|---|---|---|---|
| Hexagone allégé | Domaine indépendant de JPA | Interfaces de persistance et conversions entre modèles, sans besoin réel ici | Écartée |
| Services, un sous-package par module | Frontières entre modules vérifiables | Structure prématurée tant qu'un seul module existe | Écartée pour l'instant |
| Services, structure JHipster à plat | Simple, connue, déjà éprouvée sur un projet équivalent | Modules distingués seulement par le nom des classes | **Retenue** |

## Décision

### Stack
- Java 25, Spring Boot 4.1, Maven (wrapper).
- Spring Security, Spring Data JPA, Liquibase, MapStruct, ArchUnit, SpotBugs avec Find Security Bugs.
- SQLite avec le dialecte communautaire d'Hibernate.
- Spring AI 2.0.x pour appeler Claude Haiku 5.5 : modèle fixé par configuration, sans `temperature`, `top_p` ni `top_k`.
- PWA en HTML, CSS et JavaScript, sans framework ni build, servie par Spring Boot.
- Structure inspirée de JHipster 9.x, sans générateur ni dépendance à JHipster.

### Structure
```text
src/main/java/ca/gestionchalet/
├── config/       configuration Spring, sécurité, propriétés `recus.*`
├── domain/       entités JPA, types métier, règles et erreurs métier, converter/
├── repository/   dépôts Spring Data, stockage local des photos
├── service/      cas d'utilisation et reprise des envois ; service/dto, service/mapper
├── client/       ExtracteurRecu, ClasseurDepenses, DepotPhotos et leurs implémentations
├── security/     compte local, limitation des échecs de connexion
└── web/rest/     XxxResource ; web/rest/errors
src/main/resources/
├── config/liquibase/   master.xml, changelog/<horodatage>_added_entity_<Entité>.xml
└── static/             PWA
```

Le package racine est à plat : pas de sous-package par module. Le métier est porté par le nom des classes (`Recu`, `RecuService`, `RecuRepository`, `RecuResource`).

### Règles exécutées par les tests
`TechnicalStructureTest` et `ReglesAgentsTest` (ArchUnit) font échouer `./mvnw test` à la moindre violation. Chaque règle a un test qui échoue sur une violation volontaire.

| Couche | Accessible par |
|---|---|
| `config` | personne |
| `web` | `config` |
| `service` | `web`, `config` |
| `security` | `config`, `service`, `web` |
| `repository` | `service`, `security`, `config` |
| `client` | `service`, `config` |
| `domain` | toutes les autres |

- **Web** : une `XxxResource` valide l'entrée et délègue à un `XxxService`, sans logique métier ni accès aux données.
- **Domaine** : annotations `jakarta.persistence` permises ; aucune dépendance à `web`, à l'API Servlet, à Spring MVC ou à Spring Security. Tout emprunt à `org.springframework..` ou `org.hibernate..` est une exception nommée dans `ReglesAgentsTest` et justifiée.
- **Interfaces** : uniquement dans `client/`, pour les trois services externes. Le dépôt, le stockage local et l'authentification restent des classes concrètes, sans port.
- **Entité** : pas de setter public ; l'état change par des fabriques et des transitions qui recalculent le statut et les alertes. Ceux-ci ne sont jamais lus d'une source non fiable (modèle, client). Le constructeur sans argument `protected` est réservé à JPA.
- **DTO** : le JSON est un record `XxxDTO` projeté par MapStruct ; une entité n'est jamais sérialisée.
- **Schéma** : Liquibase est le seul gestionnaire ; Hibernate ne fait que valider (`ddl-auto=validate`). Un changeset appliqué n'est jamais modifié.
- **Montants** : cents entiers, jamais de flottants.
- **Photos** : fichiers dans un dossier configurable, hors de la base et hors de la racine web.
- **Journaux** : aucune donnée de reçu.

### Configuration
Modèle, seuils d'alerte (100 $, 500 $, 0,02 $), liste des types de dépense, correspondance année -> fichier et onglet : configuration `recus.*`, jamais de constantes. Secrets dans l'environnement, jamais dans le dépôt. Aucun taux de TPS ou de TVQ : l'application ne calcule aucune taxe.

### Sécurité
- Chaîne `SecurityFilterChain` explicite, refus par défaut ; seul `GET /actuator/health` est public, avec les ressources strictement nécessaires à la page de connexion.
- Un seul compte local : condensé du mot de passe fourni par l'environnement, session par cookie (`HttpOnly`, `SameSite`, `Secure` hors poste local), **CSRF actif**.
- Blocage temporaire après N échecs de connexion par source.
- En-têtes de sécurité et corps d'erreur générique `{erreur, code}`, y compris sur les requêtes rejetées avant l'application.

### Écarts par rapport au projet de référence
| Référence | Ici | Raison |
|---|---|---|
| `Depense`, `Ticket` | `Recu` | Glossaire |
| Extracteur seul dans `client/` | Extracteur, Sheet, Drive | D-05, D-07 |
| Jeton Bearer, sans état, sans CSRF | Mot de passe, session, CSRF actif | D-17 ; un jeton conservé dans une PWA s'expose davantage au XSS |
| Backend seul | PWA statique | D-13 |
| Statut « à vérifier » si le calcul est faux, taux en configuration | Alerte non bloquante au-delà de 0,02 $, aucun taux | D-04, D-09, D-23 |
| HTTPS, proxy, valve Tomcat | Repris avec NF-03 ou si le PRD l'exige | Déploiement reporté |

### Tests
- `domain/`, `service/` : tests unitaires ; l'entité se teste sans Spring ; collaborateurs doublés.
- `web/rest/` : contrat JSON avec `@WebMvcTest` ; sécurité avec `SecurityConfiguration` importée, plus un test sur serveur réel, requêtes malformées comprises (`factory/LESSONS.md` n° 10).
- `repository/` : `@DataJpaTest` sur une base SQLite temporaire, schéma appliqué par Liquibase.
- `client/` : implémentations testées sans service réel. Aucun test ne dépend d'un service externe.

### Essai préalable
La première issue vérifie manuellement un vrai appel à Haiku 5.5, avec une photo et une sortie structurée, hors de la suite de tests. Si Spring AI ne convient pas, le SDK Java officiel d'Anthropic le remplace derrière `ExtracteurRecu`, sans nouvel ADR.

## Conséquences
- Changer de modèle demande seulement de modifier la configuration.
- Changer de base impose de reprendre les données, de revoir le dialecte et de repasser les tests de persistance, car JPA n'abstrait pas tout.
- ArchUnit ne protège pas les frontières entre modules : l'arrivée du deuxième module réexamine ce point. Un sous-découpage demandera un nouvel ADR.
- L'installation de la PWA, qui demande HTTPS, ne sera vérifiée qu'avec NF-03. L'application reste déployable dans une image Docker : configuration par l'environnement, dossiers de données configurables.
- Sans sauvegardes (NF-02), la base et ses photos ne doivent pas servir de seule archive fiscale.
