---
id: 001-01
titre: Socle et connexion
prd: docs/prd/001-recus.md
besoin: F-01
statut: Fait
depend_de: []
criteres: [CS01, CS02, CS04, CS05, CS06, CS07, CS08, CS09, CS28, CA83]
branche: feat/001-01-socle-connexion
commits: [401429a..ac47c30]
revue: docs/reviews/001-01.md
---

## Objectif
Mettre en place le projet et ses garde-fous, avec une PWA minimale où l'utilisateur se connecte, et vérifier sur le réel que l'appel au modèle retenu fonctionne.

## Périmètre
- Dans : Build Maven et wrapper, tests d'architecture de l'ADR 0001, analyse statique de sécurité, CI
- Dans : Analyse des dépendances vulnérables (OWASP Dependency-Check), que l'ADR ne citait pas ; profil `security`, A06
- Dans : Connexion du compte local, session, CSRF, blocage après échecs, en-têtes de sécurité, erreurs génériques, test sur serveur réel
- Dans : Page de connexion, manifeste et logique de cache de la PWA
- Dans : Essai manuel, hors suite de tests : un vrai appel à Claude Haiku 5.5 avec une photo et une sortie structurée (ADR 0001, section 13 du PRD), photos réelles hors dépôt
- Hors : Lecture et validation des reçus (001-02, 001-03).

## Critères d'acceptation
Les critères listés dans l'en-tête (`criteres`), tels que définis dans le PRD. Chaque critère doit être couvert par au moins un test
dont le nom ou le `@DisplayName` contient son identifiant (ex. `ca10_…`, `cs07_…`).

## Preuve attendue
Tests rouges puis verts, `scripts/verify-clean.sh` à `VERIFY OK`, résultat de l'essai réel par champ sans donnée personnelle.

## Notes
- 2026-11-01 : Besoin d'une clé d'API Anthropic pour l'essai réel. Si Spring AI ne convient pas, repli sur le SDK Java officiel derrière la même frontière (ADR 0001).
- 2026-11-02 : Essai réel fait par l'utilisateur sur une photo, modèle `claude-haiku-5-5`, copie de 157 861 octets, sortie structurée. Date, fournisseur, montant avant taxes, TPS, TVQ, total, objet et type de dépense : présents et jugés bons par l'utilisateur. Le total égale la somme, et les taxes correspondent à 5 % et 9,975 %, au cent. Numéros d'inscription TPS et TVQ : `null`, non comparés au reçu. Spring AI est retenu. Les 3 autres photos n'ont pas été essayées.
- 2026-11-02 : Revue : 8 itérations, approuvée. Limite de 5 atteinte sans approbation ; la correction de l'itération 6 et le renommage (7, 8) ont été accordés hors limite par l'utilisateur. Dernière vérification à ac47c30 : 230 tests, 0 échec, 0 SpotBugs, 10 critères. Dependency-Check non lancé (pas de `NVD_API_KEY`).

### Écarts
- Fusion locale dans `develop`, sans PR, à la demande de l'utilisateur ; CI verte sur le push de `develop` (run 41461982551). Dependency-Check n'a pas tourné (pas de `NVD_API_KEY`).
- TDD : un RED global par échec de compilation et quatre commits intermédiaires rouges sur SpotBugs à la première livraison ; `33a3e17` rouge, corrigé par `39c3aa1`. Historique non réécrit.
- Écarts à JHipster et raisons : tableaux des rapports, `docs/reviews/001-01.md`. `@Component` reste sur `DomainUserDetailsService`, par décision de l'utilisateur. `formLogin.permitAll()` n'est pas repris (7 routes passeraient de 401 à 404 sans session). `loginPage("/")` et `logout.permitAll()` sont repris sans effet mesuré.

### Pour 001-02
- Une validation sans photo est refusée.
- Une route ne doit pas appeler `getWriter()` avant de lever une exception sans réponse engagée : Tomcat journalise alors la trace avec le message de l'exception (itération 6, M1). À vérifier dès qu'une route porte un reçu.
- À reprendre de JHipster : `CRLFLogConverter`, `LiquibaseConfiguration`, `AbstractAuditingEntity`, s'ils répondent à un critère.
- Multipart réel à essayer de bout en bout.

### Mineurs ouverts
- `spotbugs-exclude.xml` : exclusion `CRLF_INJECTION_LOGS` sur toute une classe (itération 1, M2).
- `tracking-modes: cookie` sans test (itération 1, M5).
- Sur socket brute : `OPTIONS *` sans en-têtes de sécurité ; `GET / HTTP/9.9`, `CONNECT` et `Transfer-Encoding: gzip2` donnent 500 (itération 1, M6).
- `InactiviteSession` : le `Thread.sleep` et l'assertion 200 qui le précède peuvent échouer sur une CI lente (itération 1, M9).
- Lisibilité et doublons des utilitaires de test (itération 1, M10).
- `ExceptionTranslator` : `@ResponseStatus(409)` et `AccessDeniedException` donnent 500, à reprendre avec `VERSION_PERIMEE` (itération 4, M3).
- Après engagement, une exception de statut non 500 ne laisse aucune trace (itération 6, M2).
- `@NotNull` sur `securite` sans test : le démarrage reste refusé sans lui, seul le message se dégrade (itération 8).
- Famine des fils : reportée à NF-03, voir `docs/deploiement.md` (itération 3, M14).

### Décisions de l'utilisateur
- 2026-11-02 : remise à zéro du compteur après une connexion réussie, et conversion de 405 en 404, inscrites au PRD (D-33).
