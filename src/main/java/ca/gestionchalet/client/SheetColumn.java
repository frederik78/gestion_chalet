package ca.gestionchalet.client;

/** Les colonnes du Sheet que l'application écrit, par le nom de leur en-tête (PRD, section 7.4) : une colonne se repère par son en-tête, jamais par sa position (R-24). */
enum SheetColumn {
    DATE("DATE"),
    SUPPLIER("BOUTIQUE"),
    OBJECT("OBJET"),
    EXPENSE_TYPE("TYPE"),
    AMOUNT_BEFORE_TAX("MONTANT HT"),
    GST("TPS"),
    QST("TVQ"),
    TOTAL("MONTANT TOTAL"),
    NOTES("NOTES"),
    PHOTO("PHOTO");

    private final String header;

    SheetColumn(String header) {
        this.header = header;
    }

    String header() {
        return header;
    }
}
