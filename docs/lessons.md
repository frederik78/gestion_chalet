# Leçons

Ce qui a coûté du temps ou de la qualité dans ce projet et ce qu'on en change. Une ligne par leçon : date, constat, décision.
L'orchestrateur la relit au début d'un cycle et y ajoute à sa clôture. Les leçons valables pour tous les projets remontent dans `factory/LESSONS.md`.

- 2026-11-01 : un hexagone a été proposé par réflexe, alors que `factory/LESSONS.md` (n° 1 et n° 3) rapportait déjà cet échec ; l'utilisateur a dû poser la question. Décision : relire les leçons de la factory avant tout ADR d'architecture.
- 2026-11-01 : le premier ADR ne comparait aucune option. Décision : un ADR d'architecture compare au moins deux options, avec le coût de chacune.
- 2026-11-01 : le premier ADR annonçait trois frontières alors que ses dépendances en supposaient une quatrième, pour la persistance. Décision : confronter le sens des dépendances aux interfaces annoncées et à la règle « pas d'interface au cas où ».
- 2026-11-01 : quatre reçus réels et une capture du Sheet ont révélé des cas absents du cadrage (deux documents sur une photo, numéros de taxes vides, sections de mois) et invalidé une hypothèse d'écriture. Décision : demander des exemples réels dès le premier tour du cadrage.
- 2026-11-01 : les règles d'un autre projet ne se transposent pas toutes (jeton unique pensé pour une API sans frontend, taux de taxes inutiles ici). Décision : consigner dans l'ADR chaque écart avec le projet de référence et sa raison.
- 2026-11-01 : le dossier du projet a disparu un temps, cause non établie, avec tout le cadrage non commité. Décision : proposer un commit dès qu'un tour de cadrage produit des fichiers.
- 2026-11-02 : `git add -A` a versé des photos réelles dans un commit, qu'il a fallu recréer. Décision : ajouter le dossier au `.gitignore` et garder les photos réelles hors du dépôt, avant tout essai.
- 2026-11-02 : le réglage du modèle et de l'effort a été cru appliqué alors que le projet n'était pas à jour. Décision : vérifier le modèle et l'effort dans les appels réels de l'agent, et les passer explicitement tant que le projet n'a pas la version de la factory qui les règle.
- 2026-11-02 : du code maison a remplacé Spring pour la session et la connexion, sans que trois revues centrées sur le comportement le relèvent ; l'utilisateur l'a vu. Décision : Spring d'abord, JHipster lu fichier par fichier (factory 1.11.0 et 1.12.0).
- 2026-11-02 : `git grep -E` n'interprète pas `\b` ici : une recherche des anciens noms a répondu « rien » à tort, chez le développeur comme chez l'orchestrateur. Décision : vérifier une recherche sur un nom connu avant de conclure à une absence.
- 2026-11-02 : la limite de 5 revues a été atteinte, notamment parce que de nouvelles exigences (Spring, JHipster, anglais) sont arrivées en cours de livraison. Décision : la limite reste ; un dépassement ne se fait qu'avec l'accord de l'utilisateur, sur une portée limitée.
