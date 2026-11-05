package ca.gestionchalet;

import org.springframework.test.context.TestPropertySource;

/**
 * D-62, variante : le fichier est renseigné mais l'onglet est vide (variable {@code RECUS_COPIE_ONGLET} oubliée). Mêmes attentes que la classe parente : aucune requête vers
 * Google, reçu {@code BLOQUEE} {@code FICHIER_INACCESSIBLE}. Les tests d_62 de la parente sont rejoués avec cette configuration.
 */
@TestPropertySource(properties = {"essai.google-en-memoire=false", "recus.copie.automatique=true", "recus.copie.intervalle=1h", "recus.copie.fichier=fichier-essai", "recus.copie.onglet="})
class RecuCopyWithoutTabRealServerTest extends RecuCopyUnconfiguredRealServerTest {
}
