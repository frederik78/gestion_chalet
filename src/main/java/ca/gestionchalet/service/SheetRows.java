package ca.gestionchalet.service;

import ca.gestionchalet.client.SheetClient.PhotoLink;
import ca.gestionchalet.client.SheetClient.SheetRow;
import ca.gestionchalet.domain.Alerte;
import ca.gestionchalet.domain.Valeurs;
import java.math.BigDecimal;
import java.util.Arrays;
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

    /**
     * Ce que l'application écrit dans les cellules DATE à PHOTO de la ligne, tel qu'elle l'écrit, pour la mémoriser (R-53) : la date en {@code AAAA-MM-JJ}, un montant en dollars, un texte
     * tel quel, les liens de photos par leur texte ; {@code null} pour une cellule vide. L'identifiant du reçu n'en fait pas partie : il identifie la ligne.
     */
    static List<String> writtenCells(SheetRow row) {
        return Arrays.asList(row.date().toString(), row.supplier(), emptyToNull(row.object()), row.expenseType(), amount(row.amountBeforeTax()), amount(row.gst()),
                amount(row.qst()), amount(row.total()), emptyToNull(row.notes()),
                row.photos().isEmpty() ? null : row.photos().stream().map(PhotoLink::label).collect(Collectors.joining(", ")));
    }

    private static String amount(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private static String emptyToNull(String text) {
        return text == null || text.isEmpty() ? null : text;
    }

    /** Des cents entiers aux dollars, sans flottant ; une taxe absente reste absente (R-08). */
    private static BigDecimal dollars(Long cents) {
        return cents == null ? null : BigDecimal.valueOf(cents, 2);
    }
}
