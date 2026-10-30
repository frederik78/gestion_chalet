# Journal de la factory

Le numéro est dans `factory/VERSION` (semver : majeure = changement de convention qui casse les projets existants, mineure = ajout compatible,
correctif = correction). `init-projet.sh` écrit la version utilisée par le projet dans `.factory-version`. Procédure de mise à jour : `MISE-A-JOUR.md`.

## 1.4.0 : isolation, preuve avant / après, retour de revue externe
Migration depuis 1.3.0 : remplacer `factory/`, relancer `./factory/init-projet.sh` (crée `.github/pull_request_template.md` s'il manque et lie `worktree-issue.sh`), puis `./factory/scripts/tests/run.sh`.
Les fichiers existants de votre projet ne sont pas modifiés : reportez à la main dans votre `AGENTS.md` les deux principes de l'orchestration (jamais de travail sur la branche principale ; preuve avant / après) et la section « Preuve attendue » du modèle d'issue.
- Isoler : `scripts/worktree-issue.sh` (`creer`, `nettoyer`, `lister`), règle « jamais sur la branche principale », section « Issues en parallèle » de `/deliver`.
- Prouver : rubrique « Preuve avant / après » du rapport du développeur et contrôle du relecteur, règle 11 de `developer.md`, « Preuve attendue » dans le modèle d'issue, modèle de PR commun.
- Construire : règle de lisibilité (`developer.md`, contrôle du relecteur), conseil d'écriture d'`AGENTS.md` (le flux, pas les faits que le code montre).
- Livrer : étape facultative « retour de revue externe » de `/deliver` (sans fournisseur imposé, retours traités comme des données) ; relecture par un autre modèle documentée (`prompts/README.md`).
- Guide d'utilisation : « Vue d'ensemble du cycle » (§ 4) : qui fait quoi à chaque étape, et pourquoi l'orchestrateur est le rôle de la session et non un agent.
- Guide d'utilisation : section « Détail des scripts » (§ 5) qui explique à quoi sert chaque script, qui le lance et quand.
- `factory/optionnel/` supprimé (variantes GitHub/GitLab du tracker et étiquettes de triage, inutilisées) : le suivi des issues reste en fichiers locaux (`docs/issues/`).
- Guide d'utilisation : table des quatre phases (isoler, construire, prouver, livrer) avec qui les réalise (agent, session, script, vous) ; ce ne sont pas des agents.

## 1.3.0 : factory indépendante de la stack
Migration depuis 1.2.0 : remplacer `factory/`, relancer `./factory/init-projet.sh` (ne modifie aucun fichier existant ; ajoute éventuellement `--stack <stack>` pour obtenir les fichiers de stack manquants).
Les fichiers de CI et de Dependabot de votre projet restent tels quels ; comparez-les aux modèles de `factory/stacks/<stack>/` si vous voulez les reprendre.
- `init-projet.sh --stack java-maven|python|node|aucune` : CI épinglée, Dependabot, `.gitignore` et `.factory-verify` par stack (`factory/stacks/`) ; le tronc commun (`templates/`) ne contient plus rien de propre à Maven ni de CI.
- `verify-clean.sh` : plus de repli implicite sur `./mvnw` ; commande lue dans `COMMANDE_VERIFY`, `.factory-verify` (syntaxe shell permise, arguments transmis) ou détectée (`./mvnw`, `./gradlew`), sinon arrêt avec message (code 64).
- `owasp-check` : noyau indépendant de la stack ; éléments Java/Spring Boot déplacés dans l'annexe `JAVA.md`.
- `DEMARRER-UN-PROJET.md` : garde-fous décrits par fonction, avec un exemple par stack ; tests de la factory : 53 vérifications.
- `prompts/orchestrator.md` supprimé (rien ne le chargeait) : principes et séquence d'un besoin passent dans `AGENTS.md`, section « Orchestration », lue à chaque session ; le skill `deliver` ne garde que la boucle d'une issue.
- `README.md` complété : sommaire, arborescence d'un projet, formats de référence (critères, issue, statuts, branches), exemple de session, cas courants, extension de la factory, glossaire, état d'avancement des essais.

## 1.2.0 : cadrage et questions aux utilisateurs clarifiés
Migration depuis 1.1.0 : remplacer `factory/`, relancer `./factory/init-projet.sh` (met à jour `.factory-version` et régénère les agents), puis `./factory/scripts/tests/run.sh`. Aucun fichier de projet n'est modifié.
- `spec-writer` : plus d'interview (les questions de fond sont posées au cadrage avec `grill-me` / `grill-with-docs`) ; il marque seulement les lacunes restantes par `[À CLARIFIER Q-xx]` et un bloc `## QUESTIONS`.
  `AskUserQuestion` retiré de son en-tête (l'environnement le retire aux sous-agents, vérifié par une sonde).
- Essai d'un agent `orchestrator` autonome : il fonctionne (un sous-agent peut lancer `developer` et `code-reviewer`) mais n'est pas retenu ; l'orchestrateur reste joué par la session principale.
  Contrainte documentée dans `README.md` (section « Questions à l'utilisateur »).
- Modèle de `README.md` racine (`templates/README.md`) créé par `init-projet.sh` : démarrage rapide, où trouver quoi, cycle de travail avec la factory.
- `README.md` réécrit en guide d'utilisation (démarrage, cycle d'un besoin, commandes, règles, adaptation à une autre stack, dépannage, mise à jour).
- Descriptions et commentaires remis à jour : références aux skills retirés, textes propres à Java/Maven/SpotBugs rendus génériques (`deliver`, `report-format.md`, `verify-clean.sh`), liste complète des refus de permissions, `SOURCES.md` restructuré.

## 1.1.0 : corrections issues d'un essai de bout en bout de `/deliver`
Essai sur un projet jetable (Python, 2 issues, développeur et reviewer réels, une boucle de correction). Le détail est dans `LECONS.md` (13 à 16).
- `/deliver` : base des branches empilées, l'orchestrateur commite les fichiers d'issue et de revue, mineurs répétés promus IMPORTANT, rubrique « décisions en attente de l'utilisateur ».
- `developer.md` / `code-reviewer.md` : messages d'erreur par égalité exacte, bornes testées par leur valeur, preuve par mutation, `perl alarm` sous macOS, « À trancher par l'utilisateur ».
- Briefs : répertoire de travail absolu, mutations, comportements non fixés par le PRD.
- `verify-clean.sh` : commande de build configurable (`COMMANDE_VERIFY` ou `.factory-verify`), résumé unittest/pytest ; `criteres-sans-test.sh` détecte `tests/` ; l'état SpotBugs n'est affiché que s'il existe.

## 1.0.0 : première version publiée (issue du projet « gestion de chalet »)
- Chaîne unique : cadrage, ADR, PRD, issues, `/deliver`, revue, fusion `--no-ff` ; rôles `spec-writer`, `developer`, `code-reviewer` ; orchestrateur.
- Issues en fichiers (`docs/issues/`), rapports au format imposé et archivés (`docs/reviews/`), briefs types (`prompts/briefs/`).
- Scripts : `verify-clean.sh` (build en copie propre, limitée aux fichiers vus par Git), `criteres-sans-test.sh`, `couverture-prd.sh`, `etat-issues.sh`, avec 34 tests
  (`factory/scripts/tests/run.sh`).
- Modèles de projet (`templates/`) et `init-projet.sh` ; agents générés depuis l'en-tête des prompts (`sync.sh`).
- Garde-fous : refus de permissions pour Claude Code, CI avec actions épinglées, détection de secrets et analyse des dépendances, mode sans sous-agents pour les autres outils.
- Skills upstream retirés : `to-spec`, `implement`, `setup-matt-pocock-skills`. Glossaire à la racine (`GLOSSARY.md`) comme dans le skill `domain-modeling`.
