package ca.gestionchalet;

import org.springframework.test.context.TestPropertySource;

/**
 * Q-21, variante : le fichier est renseigné mais l'onglet est vide (variable {@code RECUS_COPIE_ANNEES_2026_ONGLET} oubliée). Mêmes attentes que la classe parente : les photos
 * sont déposées, le Sheet n'est pas appelé.
 */
@TestPropertySource(properties = {"recus.copie.annees.2026.fichier=fichier-essai", "recus.copie.annees.2026.onglet=", "recus.copie.annees.2025.fichier=fichier-essai-2025", "recus.copie.annees.2025.onglet="})
class RecuCopyWithoutTabRealServerTest extends RecuCopyUnconfiguredRealServerTest {
}
