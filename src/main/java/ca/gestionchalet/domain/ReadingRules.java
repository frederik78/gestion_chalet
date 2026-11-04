package ca.gestionchalet.domain;

import java.time.Duration;

/**
 * Limites de la copie d'une photo envoyée au modèle, tenues en configuration ({@code recus.lecture.*}).
 *
 * @param coteMaxPixels long côté de la copie envoyée, dans les limites d'image du service
 * @param coteSourceMaxPixels long côté au-delà duquel une photo n'est pas décodée : une ligne de pixels se décode d'un bloc
 * @param decodagesSimultanes nombre de photos décodées en même temps : borne la mémoire, y compris hors du tas
 * @param passesDecodageMax nombre de balayages d'un JPEG, contrôlé avant le décodage : chacun refait une passe complète
 * @param delaiDecodage durée maximale d'un décodage
 * @param attenteDecodage attente maximale d'une place de décodage
 */
public record ReadingRules(int coteMaxPixels, int coteSourceMaxPixels, int decodagesSimultanes, int passesDecodageMax, Duration delaiDecodage, Duration attenteDecodage) {
}
