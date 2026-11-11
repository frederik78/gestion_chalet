# Glossaire
Les termes du métier, définis une fois, utilisés partout (code, tests, PRD, issues). Un terme par ligne : **Terme** : définition.
Aucun détail d'implémentation ici (voir la skill `domain-modeling`).

- **Chalet** : le bien unique loué par son propriétaire au Québec, auquel se rattachent les dépenses suivies.
- **Reçu** : pièce remise par un fournisseur qui atteste un achat. À éviter : ticket de caisse, ticket.
- **Fournisseur** : l'entreprise qui a vendu le bien ou le service indiqué sur un reçu. À éviter : vendeur, marchand, boutique.
- **Montant avant taxes** : somme due avant l'ajout de la TPS et de la TVQ. À éviter : prix hors taxe, prix HT, montant HT.
- **Montant total** : somme payée au fournisseur, taxes comprises. À éviter : prix avec taxes, prix TTC.
- **TPS** : taxe fédérale sur les produits et services indiquée sur un reçu.
- **TVQ** : taxe de vente du Québec indiquée sur un reçu.
- **Numéro d'inscription** : numéro attribué au fournisseur pour la TPS ou pour la TVQ et imprimé sur ses reçus.
- **Objet** : courte description de ce qui a été acheté, telle qu'elle figure dans le suivi des dépenses.
- **Type de dépense** : catégorie à laquelle une dépense est rattachée dans le suivi des dépenses. À éviter : type, catégorie.
- **Lecture** : extraction, par le modèle, des valeurs visibles sur les photos d'un reçu. Son résultat n'est qu'une proposition.
- **Validation** : acte par lequel l'utilisateur accepte les valeurs d'un reçu, éventuellement corrigées, et l'enregistre.
- **Alerte** : avertissement sur un reçu, jamais bloquant.
- **Copie** : report des photos d'un reçu validé vers Google Drive (aussi nommé dépôt). Depuis D-66, le Sheet n'est plus une copie reçu par reçu : c'est une projection de la base (voir Régénération).
- **Régénération** : écriture complète, à la demande, de l'onglet généré de chaque fichier d'année à partir de la base. Elle remplace l'ancien contenu de cet onglet et ne touche aucun autre onglet.
- **Onglet généré** : onglet du Sheet que l'application seule crée et réécrit à chaque régénération ; il reproduit les reçus de la base d'une année, une ligne par reçu, avec une colonne ID. Ne pas le confondre avec l'onglet de suivi de l'utilisateur (« Comptes 2026 »), que l'application ne lit ni n'écrit.
- **Import du Sheet** : première moitié de la mise à jour du Sheet (« importer, puis régénérer ») : l'application lit l'onglet généré, montre à l'utilisateur les différences avec ce qu'elle y avait écrit, ainsi que les ajouts saisis à la main, et n'applique à la base, comme corrections de reçus ou créations de dépenses sans reçu, que ce qu'il accepte ; la régénération suit.
- **Différence** : écart constaté à l'import entre une cellule de l'onglet généré et ce que l'application y avait écrit à la dernière régénération. Simple si seul le Sheet a changé ; conflit si la base a changé aussi depuis. À éviter : écart (réservé à l'écart de calcul des alertes), modification.
- **Dépense sans reçu** : dépense du chalet pour laquelle il n'y a aucune pièce à photographier (un abonnement, un prélèvement), saisie dans le Sheet et entrée en base par l'import. Elle est enregistrée avec les reçus, marquée « sans pièce », sans photo ; l'utilisateur doit conserver la facture. Ne pas la confondre avec un **Reçu**, qui atteste un achat par une pièce photographiée. À éviter : dépense manuelle, reçu sans photo.
- **Ajout** : ligne de l'onglet généré saisie à la main, sans ID, que l'import propose à l'utilisateur d'accepter ou de refuser ; accepté, il crée une dépense sans reçu. À éviter : nouvelle ligne, ligne ajoutée.
- **Section de mois** : dans un onglet du Sheet de l'utilisateur, la ligne de titre d'un mois et les lignes qui la suivent jusqu'au titre suivant. L'onglet généré n'en comporte pas.
- **Relevé de carte** : document de paiement par carte, sans détail des taxes.
