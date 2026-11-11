package ca.gestionchalet.service.dto;

import java.time.Instant;
import java.util.List;

/** Objet état de la régénération du contrat (PRD, section 7.1) : aucun nom de fichier, d'onglet ni identifiant Google, aucun contenu mémorisé. */
public record RegenerationStateDTO(
        String etat,
        String motif,
        Integer anneeEnCause,
        Instant demandeeLe,
        Instant derniereReussiteLe,
        Instant dernierEchecLe,
        Integer recusEcrits,
        List<Integer> anneesNonConfigurees) {

    public RegenerationStateDTO {
        anneesNonConfigurees = List.copyOf(anneesNonConfigurees);
    }
}
