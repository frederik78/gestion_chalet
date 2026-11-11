package ca.gestionchalet.client;

/** Les colonnes de l'onglet généré, dans leur ordre fixe, avec le nom de leur en-tête (PRD, section 7.4) : DATE à PHOTO, puis l'identifiant du reçu (R-51). */
enum GeneratedColumn {
    DATE("DATE"),
    SUPPLIER("BOUTIQUE"),
    OBJECT("OBJET"),
    EXPENSE_TYPE("TYPE"),
    AMOUNT_BEFORE_TAX("MONTANT HT"),
    GST("TPS"),
    QST("TVQ"),
    TOTAL("MONTANT TOTAL"),
    NOTES("NOTES"),
    PHOTO("PHOTO"),
    ID("ID");

    private final String header;

    GeneratedColumn(String header) {
        this.header = header;
    }

    String header() {
        return header;
    }
}
