package ca.gestionchalet.service;

import static org.assertj.core.api.Assertions.assertThat;

import ca.gestionchalet.client.BlockedGoogleException;
import ca.gestionchalet.client.SheetClient.PhotoLink;
import ca.gestionchalet.client.SheetClient.SheetRow;
import ca.gestionchalet.client.TransientGoogleException;
import ca.gestionchalet.domain.MotifBlocage;
import ca.gestionchalet.domain.SheetTarget;
import ca.gestionchalet.repository.RecuRepository;
import ca.gestionchalet.service.dto.RecuValidationDTO;
import ca.gestionchalet.service.dto.RecuValidationResult;
import ca.gestionchalet.service.dto.ValeursDTO;
import ca.gestionchalet.support.InMemoryGoogle;
import ca.gestionchalet.support.RecuRequests;
import ca.gestionchalet.support.TestClock;
import ca.gestionchalet.support.TestDoublesConfiguration;
import java.time.Duration;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * Le cycle de la copie par l'interface du module (D-60) : vraie base SQLite, Drive et Sheet doublés en mémoire, appel direct de « traiter le suivant », aucune copie
 * en tâche de fond. Les valeurs attendues sont celles du PRD (sections 2.1 et 10), jamais recalculées.
 */
@SpringBootTest(properties = {"recus.securite.condense-mot-de-passe=" + TestDoublesConfiguration.CONDENSE})
@Import(TestDoublesConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class RecuCopyServiceTest {

    private static final String NOTES_GARAGE = "Numéros d'inscription manquants : TPS, TVQ ; Pièce à vérifier : nom de l'acheteur, description, conditions de paiement";

    @Autowired
    RecuCopyService copie;

    @Autowired
    RecuService recus;

    @Autowired
    RecuRepository depot;

    @Autowired
    InMemoryGoogle google;

    @Autowired
    JdbcTemplate base;

    @Autowired
    TransactionTemplate transaction;

    @Autowired
    TestClock horloge;

    @BeforeEach
    void repartirDeZero() {
        base.update("delete from recu_alerte");
        base.update("delete from photo");
        base.update("delete from recu");
        base.update("delete from autorisation_google");
        base.update("insert into autorisation_google (id, etat, jeton_acces_chiffre, jeton_acces_expire_le, jeton_renouvellement_chiffre) "
                + "values (1, 'VALIDE', 'acces', '2999-01-01T00:00:00Z', 'renouvellement')");
        google.reset();
    }

    private static ValeursDTO garageIberville() {
        return new ValeursDTO(LocalDate.of(2026, 6, 16), "Garage Iberville", null, "Entretien / Réparations", 61827L, 3091L, 6167L, 71085L, null, null);
    }

    private static ValeursDTO simple(String fournisseur, Long tpsCents) {
        return new ValeursDTO(LocalDate.of(2026, 10, 11), fournisseur, null, "Fournitures", null, tpsCents, null, 1586L, null, null);
    }

    private static MultipartFile photo(int cote) {
        return new MockMultipartFile("photos", "x.png", "image/png", RecuRequests.png(cote));
    }

    private RecuValidationResult valider(ValeursDTO valeurs, Long totalLuCents, MultipartFile... photos) {
        return recus.validate(new RecuValidationDTO(UUID.randomUUID(), valeurs, totalLuCents), List.of(photos));
    }

    private RecuValidationResult valider(ValeursDTO valeurs) {
        return valider(valeurs, null, photo(4));
    }

    private String etat(UUID id) {
        return base.queryForObject("select etat_copie from recu where id = ?", String.class, id);
    }

    private String motif(UUID id) {
        return base.queryForObject("select motif_blocage from recu where id = ?", String.class, id);
    }

    private List<UUID> photosDe(UUID recuId) {
        return base.queryForList("select id from photo where recu_id = ? order by rang", String.class, recuId).stream().map(UUID::fromString).toList();
    }

    private SheetRow ligne(UUID recuId) {
        return google.rows().stream().filter(row -> row.rowId().equals(recuId.toString())).findFirst().orElseThrow();
    }

    @Test
    void ca38_laLigneEcriteDuRecuGarageIbervilleVautSesValeursEnNombresEtEnVraieDate() {
        UUID id = valider(garageIberville(), 71085L, photo(4)).recu().id();

        assertThat(copie.processNext()).isTrue();

        SheetRow ligne = ligne(id);
        assertThat(ligne.date()).isEqualTo(LocalDate.of(2026, 6, 16));
        assertThat(ligne.supplier()).isEqualTo("Garage Iberville");
        assertThat(ligne.amountBeforeTax()).isEqualByComparingTo("618.27");
        assertThat(ligne.gst()).isEqualByComparingTo("30.91");
        assertThat(ligne.qst()).isEqualByComparingTo("61.67");
        assertThat(ligne.total()).isEqualByComparingTo("710.85");
        assertThat(ligne.notes()).isEqualTo(NOTES_GARAGE);
        assertThat(etat(id)).isEqualTo("COPIEE");
    }

    private static ValeursDTO tpsEtTvqInversees(long totalCents) {
        return new ValeursDTO(LocalDate.of(2026, 6, 16), "Taxes inversées", null, "Fournitures", 11894L, 1187L, 595L, totalCents,
                "123456789RT0001", "1234567890TQ0001");
    }

    @Test
    void ca112_laTpsEtLaTvqInverseesSontCopieesTellesQuellesAvecLesDeuxTextesDansNotes() {
        UUID id = valider(tpsEtTvqInversees(13676L)).recu().id();

        assertThat(copie.processNext()).isTrue();

        SheetRow ligne = ligne(id);
        assertThat(ligne.gst()).isEqualByComparingTo("11.87");
        assertThat(ligne.qst()).isEqualByComparingTo("5.95");
        assertThat(ligne.notes()).isEqualTo("Rapport TVQ / TPS inhabituel : TVQ 5,95 $ pour une TPS de 11,87 $ ; "
                + "TPS supérieure à 5 % du montant avant taxes : 11,87 $ pour 118,94 $");
    }

    @Test
    void ca112_notesSuitLOrdreDuTableauDesAlertes() {
        valider(tpsEtTvqInversees(14246L));
        UUID doublon = valider(tpsEtTvqInversees(14246L)).recu().id();

        assertThat(copie.processNext()).isTrue();
        assertThat(copie.processNext()).isTrue();

        assertThat(ligne(doublon).notes()).isEqualTo("Écart de calcul : 5,70 $ ; Rapport TVQ / TPS inhabituel : TVQ 5,95 $ pour une TPS de 11,87 $ ; "
                + "TPS supérieure à 5 % du montant avant taxes : 11,87 $ pour 118,94 $ ; Doublon possible");
    }

    @Test
    void ca31_lAnneeDuRecuChoisitLeFichierEtLOngletDeLaCorrespondanceEtAucunAutre() {
        UUID de2026 = valider(garageIberville()).recu().id();
        UUID de2025 = valider(new ValeursDTO(LocalDate.of(2025, 11, 20), "Familiprix", null, "Fournitures", null, null, null, 1586L, null, null)).recu().id();

        copie.processNext();
        copie.processNext();

        assertThat(google.targetOf(de2026.toString())).isEqualTo(new SheetTarget("fichier-essai", "Comptes 2026"));
        assertThat(google.targetOf(de2025.toString())).isEqualTo(new SheetTarget("fichier-essai-2025", "Comptes 2025"));
    }

    @Test
    void ca13_uneTaxeAbsenteResteVideEtUnZeroResteZeroDeLAPiAuSheet() {
        RecuValidationResult sansTps = valider(simple("Sans TPS", null));
        RecuValidationResult avecZero = valider(simple("Zero TPS", 0L));
        assertThat(sansTps.recu().valeurs().tpsCents()).isNull();
        assertThat(avecZero.recu().valeurs().tpsCents()).isZero();

        copie.processNext();
        copie.processNext();

        assertThat(ligne(sansTps.recu().id()).gst()).isNull();
        assertThat(ligne(avecZero.recu().id()).gst()).isNotNull().isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void ca17_unTotalCorrigePourLaPartDuChaletEstEcritTelQuelSansRecalculDesAutresMontants() {
        ValeursDTO partDuChalet = new ValeursDTO(LocalDate.of(2026, 6, 16), "Garage Iberville", null, "Entretien / Réparations", 61827L, 3091L, 6167L, 30000L, null, null);
        RecuValidationResult resultat = valider(partDuChalet, 71085L, photo(4));
        assertThat(resultat.recu().valeurs().montantTotalCents()).isEqualTo(30000L);

        copie.processNext();

        SheetRow ligne = ligne(resultat.recu().id());
        assertThat(ligne.total()).isEqualByComparingTo("300.00");
        assertThat(ligne.amountBeforeTax()).isEqualByComparingTo("618.27");
        assertThat(ligne.gst()).isEqualByComparingTo("30.91");
        assertThat(ligne.qst()).isEqualByComparingTo("61.67");
    }

    @Test
    void ca39_lesNumerosDInscriptionNArriventDansAucuneCelluleNiMetadonnee() {
        ValeursDTO avecNumeros = new ValeursDTO(LocalDate.of(2026, 9, 25), "Unimage", null, "Fournitures", 26500L, 1325L, 2643L, 30468L,
                "123456789RT0001", "1234567890TQ0001");
        UUID id = valider(avecNumeros).recu().id();

        copie.processNext();

        assertThat(ligne(id).toString()).doesNotContain("123456789RT0001").doesNotContain("1234567890TQ0001");
        assertThat(google.journal().toString()).doesNotContain("123456789RT0001").doesNotContain("1234567890TQ0001");
        assertThat(google.files().toString()).doesNotContain("123456789RT0001");
    }

    @Test
    void ca40_sansAlerteLaCelluleNotesEstVide() {
        UUID id = valider(simple("Familiprix", 69L)).recu().id();
        Integer alertes = transaction.execute(statut -> depot.findById(id).orElseThrow().getAlertes().size());
        assertThat(alertes).isZero();

        copie.processNext();

        assertThat(ligne(id).notes()).isEmpty();
    }

    @Test
    void ca44_deuxPhotosDonnentDeuxLiensVersLesDeuxFichiersDrive() {
        UUID id = valider(simple("F", null), null, photo(4), photo(5)).recu().id();
        List<UUID> photos = photosDe(id);

        copie.processNext();

        assertThat(ligne(id).photos()).containsExactly(
                new PhotoLink("Photo 1", google.fileOf(photos.get(0)).id()),
                new PhotoLink("Photo 2", google.fileOf(photos.get(1)).id()));
        assertThat(google.fileOf(photos.get(0)).id()).isNotEqualTo(google.fileOf(photos.get(1)).id());
    }

    @Test
    void ca45_tousLesDepotsDriveLAprecedentLEcritureDeLaLigneEtLaPhotoPartTelleQuelle() {
        MultipartFile premiere = photo(4);
        MultipartFile seconde = photo(5);
        UUID id = valider(simple("F", null), null, premiere, seconde).recu().id();
        List<UUID> photos = photosDe(id);

        copie.processNext();

        assertThat(google.journal()).containsExactly("drive:" + photos.get(0), "drive:" + photos.get(1), "sheet:" + id);
        assertThat(google.fileOf(photos.get(0)).content()).isEqualTo(RecuRequests.png(4));
        assertThat(google.fileOf(photos.get(1)).content()).isEqualTo(RecuRequests.png(5));
        assertThat(google.fileOf(photos.get(0)).mediaType()).isEqualTo("image/png");
        assertThat(google.fileOf(photos.get(0)).name()).isEqualTo("2026-10-11_" + photos.get(0) + ".png");
    }

    @Test
    void ca45_unDepotDriveEnPanneLaisseLeRecuACopierSansEcrireLaLigneEtLaReprisePoseTout(CapturedOutput journaux) {
        UUID id = valider(simple("Fournisseur-temoin-journal", null), null, photo(4), photo(5)).recu().id();
        google.failNextDeposit(new TransientGoogleException("PANNE"));

        assertThat(copie.processNext()).isTrue();

        assertThat(etat(id)).isEqualTo("A_COPIER");
        assertThat(google.journal()).noneMatch(appel -> appel.startsWith("sheet:"));
        assertThat(journaux.getAll()).doesNotContain("Fournisseur-temoin-journal");

        copie.processNext();

        assertThat(etat(id)).isEqualTo("COPIEE");
        assertThat(google.rows()).hasSize(1);
        assertThat(google.files()).hasSize(2);
    }

    @Test
    void ca45_uneEcritureEnPanneGardeLesPhotosDeposeesEtNeLesRedeposePas() {
        UUID id = valider(simple("F", null), null, photo(4), photo(5)).recu().id();
        google.failNextWrite(new TransientGoogleException("PANNE"));

        copie.processNext();

        assertThat(etat(id)).isEqualTo("A_COPIER");
        assertThat(base.queryForList("select drive_file_id from photo where recu_id = ?", String.class, id)).doesNotContainNull().hasSize(2);
        assertThat(google.depositCalls()).isEqualTo(2);

        copie.processNext();

        assertThat(etat(id)).isEqualTo("COPIEE");
        assertThat(google.depositCalls()).isEqualTo(2);
        assertThat(google.rows()).hasSize(1);
    }

    @Test
    void r21_uneReponseDeDepotOuDEcriturePerdueNeCreeNiFichierNiLigneEnDouble() {
        UUID id = valider(simple("F", null), null, photo(4)).recu().id();
        google.loseNextDepositResponse();
        google.loseNextWriteResponse();

        copie.processNext();
        copie.processNext();
        copie.processNext();

        assertThat(etat(id)).isEqualTo("COPIEE");
        assertThat(google.files()).hasSize(1);
        assertThat(google.rows()).hasSize(1);
        assertThat(google.depositCalls()).isEqualTo(2);
        assertThat(google.writeCalls()).isEqualTo(2);
    }

    @Test
    void ca45_unRefusDuFichierBloqueLeRecuApresLeDepotDesPhotosEtLeLaisseConsultable() {
        UUID id = valider(simple("F", null)).recu().id();
        google.failNextWrite(new BlockedGoogleException(MotifBlocage.FICHIER_INACCESSIBLE));

        copie.processNext();

        assertThat(etat(id)).isEqualTo("BLOQUEE");
        assertThat(motif(id)).isEqualTo("FICHIER_INACCESSIBLE");
        assertThat(base.queryForObject("select drive_file_id from photo where recu_id = ?", String.class, id)).isNotNull();
        assertThat(copie.processNext()).isFalse();
    }

    @Test
    void ca43_uneStructureInattendueDuSheetBloqueLeRecuPourCeMotifApresLeDepotDesPhotosSansEcrireLaLigne() {
        UUID id = valider(simple("F", null)).recu().id();
        google.failNextWrite(new BlockedGoogleException(MotifBlocage.STRUCTURE_SHEET));

        copie.processNext();

        assertThat(etat(id)).isEqualTo("BLOQUEE");
        assertThat(motif(id)).isEqualTo("STRUCTURE_SHEET");
        assertThat(base.queryForObject("select drive_file_id from photo where recu_id = ?", String.class, id)).isNotNull();
        assertThat(google.rows()).isEmpty();
        assertThat(copie.processNext()).isFalse();
    }

    @Test
    void ca45_uneAutorisationManquanteEnCoursDeCopieBloqueLeRecuPourCeMotif() {
        UUID id = valider(simple("F", null)).recu().id();
        google.failNextDeposit(new BlockedGoogleException(MotifBlocage.AUTORISATION_GOOGLE));

        copie.processNext();

        assertThat(etat(id)).isEqualTo("BLOQUEE");
        assertThat(motif(id)).isEqualTo("AUTORISATION_GOOGLE");
        assertThat(google.rows()).isEmpty();
    }

    @Test
    void ca45_unRecuCorrigePendantLaCopieResteACopierEtEstRecopie() {
        UUID id = valider(simple("F", null)).recu().id();
        google.beforeEachWrite(() -> transaction.executeWithoutResult(statut -> depot.findById(id).orElseThrow().corriger()));

        copie.processNext();

        assertThat(etat(id)).isEqualTo("A_COPIER");
        assertThat(base.queryForObject("select version from recu where id = ?", Integer.class, id)).isEqualTo(2);

        google.beforeEachWrite(() -> { });
        copie.processNext();

        assertThat(etat(id)).isEqualTo("COPIEE");
        assertThat(google.rows()).hasSize(1);
    }

    @Test
    void ca45_aucuneTransactionNEstOuvertePendantLesAppelsAGoogle() {
        valider(simple("F", null));
        List<Boolean> transactions = new java.util.concurrent.CopyOnWriteArrayList<>();
        google.beforeEachDeposit(() -> transactions.add(TransactionSynchronizationManager.isActualTransactionActive()));
        google.beforeEachWrite(() -> transactions.add(TransactionSynchronizationManager.isActualTransactionActive()));

        copie.processNext();

        assertThat(transactions).containsExactly(false, false);
    }

    @Test
    void ca45_lesRecusSontCopiesUnALaFoisDansLOrdreDeValidation() {
        UUID premier = valider(simple("Premier", null)).recu().id();
        horloge.avancer(Duration.ofSeconds(1));
        UUID second = valider(simple("Second", null)).recu().id();

        assertThat(copie.processNext()).isTrue();
        assertThat(etat(premier)).isEqualTo("COPIEE");
        assertThat(etat(second)).isEqualTo("A_COPIER");
        assertThat(google.rows()).extracting(SheetRow::rowId).containsExactly(premier.toString());

        assertThat(copie.processNext()).isTrue();
        assertThat(google.rows()).extracting(SheetRow::rowId).containsExactly(premier.toString(), second.toString());
        assertThat(copie.processNext()).isFalse();
    }

    @Test
    void ca45_sansRecuACopierOuAvecUnRecuBloqueAucunAppelNePartVersGoogle() {
        base.update("delete from autorisation_google");
        UUID bloque = valider(simple("F", null)).recu().id();
        assertThat(etat(bloque)).isEqualTo("BLOQUEE");

        assertThat(copie.processNext()).isFalse();

        assertThat(google.journal()).isEmpty();
    }

    @Test
    void cs12_unNomDePhotoHostileNArriveNiDansDriveNiDansLeJournalDeLaCopie() {
        MultipartFile hostile = new MockMultipartFile("photos", "../../secret.jpg", "image/png", RecuRequests.png(4));
        UUID id = valider(simple("F", null), null, hostile).recu().id();

        copie.processNext();

        UUID photo = photosDe(id).getFirst();
        assertThat(google.fileOf(photo).name()).isEqualTo("2026-10-11_" + photo + ".png").doesNotContain("secret").doesNotContain("..");
        assertThat(google.files().toString()).doesNotContain("secret");
    }

    @Test
    void cs16_unFournisseurEtDesObjetsQuiRessemblentADesFormulesSontRemisTelsQuels() {
        String formule = "=IMPORTXML(\"https://exemple.invalid\",\"//a\")";
        UUID premier = valider(new ValeursDTO(LocalDate.of(2026, 10, 11), formule, "+SOMME(1;1)", "Fournitures", null, null, null, 1586L, null, null)).recu().id();
        UUID second = valider(new ValeursDTO(LocalDate.of(2026, 10, 11), "Autre", "@objet", "Fournitures", null, null, null, 1586L, null, null)).recu().id();

        copie.processNext();
        copie.processNext();

        assertThat(ligne(premier).supplier()).isEqualTo(formule);
        assertThat(ligne(premier).object()).isEqualTo("+SOMME(1;1)");
        assertThat(ligne(second).object()).isEqualTo("@objet");
    }
}
