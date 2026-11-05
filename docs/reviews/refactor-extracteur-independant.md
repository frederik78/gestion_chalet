# Relecture de refactor/extracteur-independant

## Relecture n° 1 (2026-11-05), rapport tel quel

### Verdict : APPROUVÉ (aucun BLOQUANT ; 3 réserves mineures et 3 passages à relever)

### Vérifié (copie jetable /private/tmp/claude-501/-Users-sophieseigneuret-projects-gestion-chalet-worktrees-develop/88a91e4d-acf3-4faf-a2ec-79464de6c3f4/scratchpad/rev1, HEAD par git archive)
- `./mvnw -o test` sur ChatModelRecuExtractorTest, ChatModelReadingContractRealServerTest, ReadingUnreachableRealServerTest et AgentRulesTest : 10 + 18 + 1 + 73 tests, 0 échec, 0 erreur. Pas de `verify-clean.sh` ni de Chrome.
- Aucune classe de production ne référence plus Anthropic : `grep` de `src/main` ne trouve que `application.yml` et un commentaire de `ApplicationProperties`. Reste `src/essai/java/realreading/RealReadingTrial.java`.

### Mutations rejouées (6, toutes détectées)
| # | Mutation | Résultat |
|---|---|---|
| M1 | `isUnreachable` remplacé par `SERVICE_ERROR` partout | 5 échecs unitaires, 2 sur le serveur réel, 1 sur la connexion refusée |
| M2 | `SERVICE_ERROR` remplacé par `INVALID_RESPONSE` | 1 échec unitaire, 2 sur le serveur réel |
| M3 | schéma retiré (`mutate().build()`) | 1 échec unitaire, 1 erreur sur le serveur réel (ca06/ca08) |
| M4 | options reconstruites sans recopie du modèle ni de `max_tokens` | 1 échec unitaire, 1 échec et 1 erreur sur le serveur réel |
| M5 | garde `instanceof` de démarrage supprimée | 1 classe en échec (`d64_desOptionsSansSchemaImposeFontEchouerLeDemarrage`) |
| M6 | règle ArchUnit réduite à `com.anthropic..` | `AgentRulesTest` : 1 échec (la fixture `VendorBoundClient` n'est plus détectée) |

### Examen demandé
- **Options (`ChatModelRecuExtractor.java:61-68`)** : `mutate()` recopie le modèle, `max_tokens` et le schéma. Le corps réellement envoyé est vérifié par ChatModelReadingContractRealServerTest (lignes 134-136) : ni `temperature`, ni `top_p`, ni `top_k`. Un futur `temperature` posé dans `spring.ai.anthropic.chat.options` serait donc attrapé par ce test ; un garde-fou de plus n'est pas nécessaire.
- **Garde de démarrage (ligne 56-58)** : `IllegalStateException` au message fixe, sans secret ni valeur. Testée (`d64_…`, M5).
- **Classement par `getCause()` (lignes 94-101)** : le cas `IOException` imbriquée est testé. Réserve R1 (mineure) : la boucle n'est pas bornée. Un cycle de causes A→B→A (permis par `initCause` sur deux exceptions distinctes) bloquerait le fil. Je n'en connais aucun dans Spring AI ni dans le SDK. Une borne à environ 10 niveaux coûterait peu.
- **`InterruptedException`** : si le SDK l'enveloppe en `RuntimeException`, la panne est classée `SERVICE_ERROR` et le drapeau d'interruption n'est pas restauré. Réserve R2 (mineure) : comportement identique à l'ancien code, hors périmètre de ce refactor. Les `Error` ne sont pas interceptés, comme avant.
- **Distinctions perdues (PRD 7.3, CA04, CA08)** : `RecuReadingService.java:62-66` attrape `RecuExtractionException` quel que soit `failure()` et renvoie `ECHEC` avec `champsRejetes` vide. Seul le journal change : `Lecture en échec : SERVICE_ERROR` au lieu de `INVALID_RESPONSE`, texte fixe. Le PRD 7.3 et CA04 ne disent que « `ECHEC` sans détail technique » : conformes. Le test `ca04_unCorpsJsonDeMauvaiseFormeDonneErreurDeService` documente le changement et vérifie que le témoin du corps n'apparaît pas dans les journaux.
- **Règle d'architecture** : elle porte sur `ca.gestionchalet..`, `config/` compris, et sur le bytecode (import, nom complet, champ, appel de méthode, annotation). Elle ne bloque pas la configuration actuelle. Elle ne voit pas la réflexion par nom de classe en chaîne, ni les constantes inlinées. Réserve R3 (mineure) : ce n'est pas écrit dans le Javadoc de la règle (limite à ajouter, comme pour ca72). La fixture de violation et la fixture conforme (`ProviderClient`) existent ; M6 montre que la fixture mord.
- **`RealReadingTrial` (hors règle)** : acceptable. Il n'est compilé que dans le profil `essai-reel`, hors `test` et `verify`, et son rôle est justement de nommer Anthropic pour l'essai manuel. À consigner en une phrase dans D-64 ou l'ADR.
- **Journaux** : seul le texte fixe `Lecture en échec : {}` avec l'énumération. Aucun message d'exception n'est repris (testé avec le témoin). Aucune clé.
- **OWASP** : A03 (réponse du modèle non fiable) inchangé, la validation reste en aval. A05, A09 : pas de fuite par message d'erreur ni par journal. A06 : aucune dépendance ajoutée. Rien d'avéré.

### Passages devenus faux ou à compléter (à ne pas corriger ici)
1. `docs/reviews/001-03.md:39` cite `AnthropicRecuExtractor` : historique daté, sans correction à faire.
2. Commentaire d'`application.yml:53` (`org.springframework.ai.anthropic: ERROR`) et Javadoc d'`ApplicationProperties.java:91` : toujours exacts, car la configuration reste. Rien d'autre de faux relevé.
3. L'ADR 0001, ligne 100 (« le SDK Java ... le remplace derrière `ExtracteurRecu` »), garde l'ancien nom `ExtracteurRecu`. L'ADR 0001, ligne 104, n'ajoute pas la mention de `RealReadingTrial`. La section 7.3 du PRD (« Anthropic » dans le titre) reste vraie tant que le modèle configuré est Claude.

### Non fait
- `scripts/verify-clean.sh` complet non lancé ; tests d'écran avec Chrome laissés à la session.
