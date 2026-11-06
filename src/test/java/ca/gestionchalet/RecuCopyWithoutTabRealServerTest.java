package ca.gestionchalet;

import org.springframework.test.context.TestPropertySource;

/**
 * D-62, variante : le fichier est renseigné mais l'onglet est vide (variable {@code RECUS_COPIE_ANNEES_2026_ONGLET} oubliée). Mêmes attentes que la classe parente : aucune requête vers
 * Google, reçu {@code BLOQUEE} {@code FICHIER_INACCESSIBLE}. Les tests d_62 de la parente sont rejoués avec cette configuration.
 */
@TestPropertySource(properties = {"essai.google-en-memoire=false", "recus.copie.automatique=true", "recus.copie.intervalle=1h", "recus.copie.annees.2026.fichier=fichier-essai", "recus.copie.annees.2026.onglet=", "recus.copie.annees.2025.fichier=fichier-essai-2025", "recus.copie.annees.2025.onglet="})
class RecuCopyWithoutTabRealServerTest extends RecuCopyUnconfiguredRealServerTest {
}
