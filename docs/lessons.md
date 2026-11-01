# Leçons

Ce qui a coûté du temps ou de la qualité dans ce projet et ce qu'on en change. Une ligne par leçon : date, constat, décision.
L'orchestrateur la relit au début d'un cycle et y ajoute à sa clôture. Les leçons valables pour tous les projets remontent dans `factory/LESSONS.md`.

- 2026-11-01 : un hexagone a été proposé par réflexe, alors que `factory/LESSONS.md` (n° 1 et n° 3) rapportait déjà cet échec ; l'utilisateur a dû poser la question. Décision : relire les leçons de la factory avant tout ADR d'architecture.
- 2026-11-01 : le premier ADR ne comparait aucune option. Décision : un ADR d'architecture compare au moins deux options, avec le coût de chacune.
- 2026-11-01 : le premier ADR annonçait trois frontières alors que ses dépendances en supposaient une quatrième, pour la persistance. Décision : confronter le sens des dépendances aux interfaces annoncées et à la règle « pas d'interface au cas où ».
- 2026-11-01 : quatre reçus réels et une capture du Sheet ont révélé des cas absents du cadrage (deux documents sur une photo, numéros de taxes vides, sections de mois) et invalidé une hypothèse d'écriture. Décision : demander des exemples réels dès le premier tour du cadrage.
- 2026-11-01 : les règles d'un autre projet ne se transposent pas toutes (jeton unique pensé pour une API sans frontend, taux de taxes inutiles ici). Décision : consigner dans l'ADR chaque écart avec le projet de référence et sa raison.
- 2026-11-01 : le dossier du projet a disparu un temps, cause non établie, avec tout le cadrage non commité. Décision : proposer un commit dès qu'un tour de cadrage produit des fichiers.
