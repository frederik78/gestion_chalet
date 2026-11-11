package ca.gestionchalet.client;

/**
 * Un onglet d'un fichier du Sheet : son identifiant, qui ne change pas quand on le renomme ou qu'on remplace son contenu, et son titre.
 *
 * @param id identifiant de l'onglet dans le fichier
 * @param title titre de l'onglet, une donnée externe : comparé, jamais journalisé
 */
public record SheetTab(int id, String title) {
}
