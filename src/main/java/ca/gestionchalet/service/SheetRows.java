package ca.gestionchalet.service;

import ca.gestionchalet.client.SheetClient.PhotoLink;
import ca.gestionchalet.client.SheetClient.SheetRow;
import ca.gestionchalet.domain.Alerte;
import ca.gestionchalet.domain.Valeurs;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** La ligne du Sheet d'un reçu : ses valeurs, sans les numéros d'inscription (R-25), les textes des alertes dans l'ordre du tableau du PRD (R-26) et ses liens de photos (R-27). */
final class SheetRows {

    private static final String NOTES_SEPARATOR = " ; ";

    private SheetRows() {
    }

    static SheetRow of(UUID recuId, Valeurs valeurs, List<Alerte> alertes, List<PhotoLink> links) {
        String notes = alertes.stream().sorted(Comparator.comparing(alerte -> alerte.code().ordinal())).map(Alerte::message).collect(Collectors.joining(NOTES_SEPARATOR));
        return new SheetRow(recuId.toString(), valeurs.date(), valeurs.fournisseur(), valeurs.objet(), valeurs.typeDepense(),
                dollars(valeurs.montantAvantTaxesCents()), dollars(valeurs.tpsCents()), dollars(valeurs.tvqCents()), dollars(valeurs.montantTotalCents()), notes, links);
    }

    /** Des cents entiers aux dollars, sans flottant ; une taxe absente reste absente (R-08). */
    private static BigDecimal dollars(Long cents) {
        return cents == null ? null : BigDecimal.valueOf(cents, 2);
    }
}
