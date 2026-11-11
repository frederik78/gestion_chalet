package ca.gestionchalet.service;

import static org.assertj.core.api.Assertions.assertThat;

import ca.gestionchalet.client.BlockedGoogleException;
import ca.gestionchalet.client.TransientGoogleException;
import ca.gestionchalet.domain.MotifBlocage;
import ca.gestionchalet.repository.RecuRepository;
import ca.gestionchalet.service.dto.RecuValidationDTO;
import ca.gestionchalet.service.dto.RecuValidationResult;
import ca.gestionchalet.service.dto.ValeursDTO;
import ca.gestionchalet.support.InMemoryGoogle;
import ca.gestionchalet.support.RecuRequests;
import ca.gestionchalet.support.TestClock;
import ca.gestionchalet.support.TestDoublesConfiguration;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * Le dépôt des photos par l'interface du module de copie (D-60, D-66) : vraie base SQLite, Drive et Sheet doublés en mémoire, horloge contrôlée, appel direct du passage
 * (« traiter ce qui est dû »), aucune tâche de fond. Le Sheet n'est jamais appelé : le double le prouve par son journal. Les durées attendues sont celles du PRD (R-20), jamais recalculées.
 */
@SpringBootTest(properties = {"recus.securite.condense-mot-de-passe=" + TestDoublesConfiguration.CONDENSE})
@Import(TestDoublesConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class RecuCopyServiceTest {

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
    ApplicationEventPublisher evenements;

    @Autowired
    TestClock horloge;

    @BeforeEach
    void repartirDeZero() {
        base.update("delete from recu_alerte");
        base.update("delete from photo");
        base.update("delete from recu");
        autoriser();
        google.reset();
    }

    private void autoriser() {
        base.update("delete from autorisation_google");
        base.update("insert into autorisation_google (id, etat, jeton_acces_chiffre, jeton_acces_expire_le, jeton_renouvellement_chiffre) "
                + "values (1, 'VALIDE', 'acces', '2999-01-01T00:00:00Z', 'renouvellement')");
    }

    private static ValeursDTO simple(String fournisseur) {
        return new ValeursDTO(LocalDate.of(2026, 10, 11), fournisseur, null, "Fournitures", null, null, null, 1586L, null, null);
    }

    private static MultipartFile photo(int cote) {
        return new MockMultipartFile("photos", "x.png", "image/png", RecuRequests.png(cote));
    }

    private RecuValidationResult valider(ValeursDTO valeurs, MultipartFile... photos) {
        return recus.validate(new RecuValidationDTO(UUID.randomUUID(), valeurs, null), List.of(photos));
    }

    private UUID validerUnePhoto() {
        return valider(simple("F"), photo(4)).recu().id();
    }

    private List<UUID> photosDe(UUID recuId) {
        return base.queryForList("select id from photo where recu_id = ? order by rang", String.class, recuId).stream().map(UUID::fromString).toList();
    }

    private String fichierDe(UUID photoId) {
        return base.queryForObject("select drive_file_id from photo where id = ?", String.class, photoId);
    }

    /** L'horloge avance, puis le passage de la copie : rien n'est déposé avant l'heure dite (R-20). */
    private void passerApres(Duration duree) {
        horloge.avancer(duree);
        copie.processPending();
    }

    @Test
    void ca45_lesPhotosSontDeposeesTellesQuellesSansAucunAppelAuSheet() {
        UUID id = valider(simple("F"), photo(4), photo(5)).recu().id();
        List<UUID> photos = photosDe(id);

        copie.processPending();

        assertThat(google.journal()).containsExactly("drive:" + photos.get(0), "drive:" + photos.get(1));
        assertThat(google.writeCalls()).isZero();
        assertThat(google.fileOf(photos.get(0)).content()).isEqualTo(RecuRequests.png(4));
        assertThat(google.fileOf(photos.get(1)).content()).isEqualTo(RecuRequests.png(5));
        assertThat(google.fileOf(photos.get(0)).mediaType()).isEqualTo("image/png");
        assertThat(google.fileOf(photos.get(0)).name()).isEqualTo("2026-10-11_" + photos.get(0) + ".png");
        assertThat(fichierDe(photos.get(0))).isEqualTo(google.fileOf(photos.get(0)).id());
        assertThat(fichierDe(photos.get(1))).isEqualTo(google.fileOf(photos.get(1)).id()).isNotEqualTo(fichierDe(photos.get(0)));
    }

    @Test
    void ca45_aucuneTransactionNEstOuvertePendantLesAppelsADrive() {
        validerUnePhoto();
        List<Boolean> transactions = new CopyOnWriteArrayList<>();
        google.beforeEachDeposit(() -> transactions.add(TransactionSynchronizationManager.isActualTransactionActive()));

        copie.processPending();

        assertThat(transactions).containsExactly(false);
    }

    @Test
    void ca45_lesRecusSontDeposesUnALaFoisDansLOrdreDeValidation() {
        UUID premier = validerUnePhoto();
        horloge.avancer(Duration.ofSeconds(1));
        UUID second = validerUnePhoto();

        copie.processPending();

        assertThat(google.journal()).containsExactly("drive:" + photosDe(premier).getFirst(), "drive:" + photosDe(second).getFirst());
    }

    @Test
    void r29_uneFoisDeposeeUnePhotoNEstJamaisRedeposee() {
        UUID id = validerUnePhoto();
        copie.processPending();
        String fichier = fichierDe(photosDe(id).getFirst());

        passerApres(Duration.ofHours(2));
        copie.processPending();

        assertThat(google.depositCalls()).isEqualTo(1);
        assertThat(fichierDe(photosDe(id).getFirst())).isEqualTo(fichier);
    }

    @Test
    void ca46_unDepotEnPanneLaisseLeRecuEnregistreSansLienEtProgrammeUnEssaiSansAppelerLeSheet(CapturedOutput journaux) {
        UUID id = valider(simple("Fournisseur-temoin-journal"), photo(4)).recu().id();
        google.failNextDeposit(new TransientGoogleException("PANNE"));

        copie.processPending();

        assertThat(base.queryForObject("select count(*) from recu where id = ?", Integer.class, id)).isEqualTo(1);
        assertThat(fichierDe(photosDe(id).getFirst())).isNull();
        assertThat(google.writeCalls()).isZero();
        assertThat(journaux.getAll()).doesNotContain("Fournisseur-temoin-journal");
        passerApres(Duration.ofSeconds(60));
        assertThat(fichierDe(photosDe(id).getFirst())).isNotNull();
        assertThat(google.writeCalls()).isZero();
    }

    @Test
    void ca47_troisPannesPuisUnSuccesEspacentLesEssaisDeUnDeuxEtQuatreMinutesEtLaPhotoNEstQueDansUnFichier() {
        UUID id = validerUnePhoto();
        google.failNextDeposit(new TransientGoogleException("HTTP_503"));
        copie.processPending();
        assertThat(google.depositCalls()).isEqualTo(1);

        passerApres(Duration.ofSeconds(59));
        assertThat(google.depositCalls()).as("avant 1 minute").isEqualTo(1);
        google.failNextDeposit(new TransientGoogleException("HTTP_503"));
        passerApres(Duration.ofSeconds(1));
        assertThat(google.depositCalls()).as("à 1 minute").isEqualTo(2);

        passerApres(Duration.ofSeconds(119));
        assertThat(google.depositCalls()).as("avant 2 minutes de plus").isEqualTo(2);
        google.failNextDeposit(new TransientGoogleException("HTTP_503"));
        passerApres(Duration.ofSeconds(1));
        assertThat(google.depositCalls()).as("à 2 minutes").isEqualTo(3);

        passerApres(Duration.ofSeconds(239));
        assertThat(google.depositCalls()).as("avant 4 minutes de plus").isEqualTo(3);
        assertThat(fichierDe(photosDe(id).getFirst())).isNull();
        passerApres(Duration.ofSeconds(1));
        assertThat(google.depositCalls()).as("à 4 minutes").isEqualTo(4);

        assertThat(fichierDe(photosDe(id).getFirst())).isNotNull();
        assertThat(google.files()).hasSize(1);
        assertThat(google.writeCalls()).isZero();
    }

    @Test
    void ca47_uneLonguePanneEspaceLesEssaisDUneHeureAuPlus() {
        validerUnePhoto();
        google.failNextDeposit(new TransientGoogleException("HTTP_503"));
        copie.processPending();
        int appels = 1;
        for (long intervalle : new long[] {60, 120, 240, 480, 960, 1920, 3600, 3600}) {
            passerApres(Duration.ofSeconds(intervalle - 1));
            assertThat(google.depositCalls()).as("rien avant " + intervalle + " s").isEqualTo(appels);
            google.failNextDeposit(new TransientGoogleException("HTTP_503"));
            passerApres(Duration.ofSeconds(1));
            appels++;
            assertThat(google.depositCalls()).as("un essai à " + intervalle + " s").isEqualTo(appels);
        }
    }

    @Test
    void ca53_unQuotaAvecRetryAfterDe120SecondesInterditUnEssaiAvantCeDelai() {
        UUID id = validerUnePhoto();
        google.failNextDeposit(new TransientGoogleException("HTTP_429", Duration.ofSeconds(120)));
        copie.processPending();

        passerApres(Duration.ofSeconds(119));
        assertThat(google.depositCalls()).as("avant 120 s, malgré 1 minute de rythme").isEqualTo(1);
        passerApres(Duration.ofSeconds(1));

        assertThat(google.depositCalls()).isEqualTo(2);
        assertThat(fichierDe(photosDe(id).getFirst())).isNotNull();
    }

    @Test
    void ca53_unRetryAfterAuDelaDeLHeurePlafonneLAttenteAUneHeure() {
        validerUnePhoto();
        google.failNextDeposit(new TransientGoogleException("HTTP_429", Duration.ofDays(2)));
        copie.processPending();

        passerApres(Duration.ofSeconds(3599));
        assertThat(google.depositCalls()).isEqualTo(1);
        passerApres(Duration.ofSeconds(1));

        assertThat(google.depositCalls()).isEqualTo(2);
    }

    @Test
    void ca49_uneReponseDeDepotPerdueEstReprisePuisLeFichierRetrouveEtIlNEnExisteQuUn() {
        UUID id = validerUnePhoto();
        google.loseNextDepositResponse();
        copie.processPending();
        assertThat(fichierDe(photosDe(id).getFirst())).isNull();
        assertThat(google.files()).hasSize(1);

        passerApres(Duration.ofSeconds(60));

        assertThat(google.files()).hasSize(1);
        assertThat(google.depositCalls()).isEqualTo(2);
        assertThat(fichierDe(photosDe(id).getFirst())).isEqualTo(google.fileOf(photosDe(id).getFirst()).id());
    }

    @Test
    void ca64_sansAutorisationLeRecuEstEnregistreSesPhotosAttendentEtAucunAppelNePart() {
        base.update("delete from autorisation_google");

        UUID id = validerUnePhoto();
        copie.processPending();
        passerApres(Duration.ofHours(3));

        assertThat(base.queryForObject("select count(*) from recu where id = ?", Integer.class, id)).isEqualTo(1);
        assertThat(fichierDe(photosDe(id).getFirst())).isNull();
        assertThat(google.journal()).isEmpty();
    }

    @Test
    void ca65_apresUneAutorisationLesPhotosEnAttenteSontDeposeesAuPassageSuivantSansAttendreLEssai() {
        base.update("delete from autorisation_google");
        UUID enAttente = validerUnePhoto();
        autoriser();
        google.failNextDeposit(new TransientGoogleException("HTTP_503"));
        copie.processPending();
        assertThat(google.depositCalls()).isEqualTo(1);

        transaction.executeWithoutResult(statut -> evenements.publishEvent(new GoogleAuthorizationGranted()));
        copie.processPending();

        assertThat(fichierDe(photosDe(enAttente).getFirst())).as("repris aussitôt, avant la minute d'attente").isNotNull();
        assertThat(google.writeCalls()).isZero();
    }

    @Test
    void ca66_uneAutorisationPerdueEnCoursDeDepotLaisseLesDepotsAFaireEtUneNouvelleLesDeposeSansDoublon() {
        UUID premier = validerUnePhoto();
        google.failNextDeposit(new BlockedGoogleException(MotifBlocage.AUTORISATION_GOOGLE));
        copie.processPending();
        assertThat(fichierDe(photosDe(premier).getFirst())).isNull();
        base.update("update autorisation_google set etat = 'A_RENOUVELER'");
        UUID pendant = validerUnePhoto();
        passerApres(Duration.ofHours(3));
        assertThat(google.depositCalls()).as("rien ne part sans autorisation").isEqualTo(1);

        autoriser();
        transaction.executeWithoutResult(statut -> evenements.publishEvent(new GoogleAuthorizationGranted()));
        copie.processPending();

        assertThat(google.files()).hasSize(2);
        assertThat(fichierDe(photosDe(premier).getFirst())).isNotNull();
        assertThat(fichierDe(photosDe(pendant).getFirst())).isNotNull();
        assertThat(google.writeCalls()).isZero();
    }

    @Test
    void ca99_aucuneEcritureAuSheetApresValidationCorrectionAutorisationAccordeeOuPerdueEtUneHeureDoublee() {
        UUID id = validerUnePhoto();
        transaction.executeWithoutResult(statut -> depot.findById(id).orElseThrow().corriger());
        transaction.executeWithoutResult(statut -> evenements.publishEvent(new GoogleAuthorizationGranted()));
        transaction.executeWithoutResult(statut -> evenements.publishEvent(new GoogleAuthorizationLost()));
        copie.processPending();
        passerApres(Duration.ofHours(1));
        passerApres(Duration.ofHours(1));

        assertThat(google.writeCalls()).isZero();
        assertThat(google.journal()).noneMatch(appel -> appel.startsWith("sheet:"));
    }

    @Test
    void q19_unRecuEnregistreParUneVersionAnterieureDontLaPhotoEstADeposerEstRepris() {
        UUID bloque = validerUnePhoto();
        UUID copieeSansPhoto = validerUnePhoto();
        base.update("update recu set etat_copie = 'BLOQUEE', motif_blocage = 'FICHIER_INACCESSIBLE' where id = ?", bloque.toString());
        base.update("update recu set etat_copie = 'COPIEE' where id = ?", copieeSansPhoto.toString());

        copie.processPending();

        assertThat(fichierDe(photosDe(bloque).getFirst())).isNotNull();
        assertThat(fichierDe(photosDe(copieeSansPhoto).getFirst())).isNotNull();
    }

    @Test
    void cs12_unNomDePhotoHostileNArriveNiDansDriveNiDansLeJournal(CapturedOutput journaux) {
        MultipartFile hostile = new MockMultipartFile("photos", "../../secret.jpg", "image/png", RecuRequests.png(4));
        UUID id = valider(simple("F"), hostile).recu().id();
        google.failNextDeposit(new TransientGoogleException("PANNE"));
        copie.processPending();
        passerApres(Duration.ofSeconds(60));

        UUID photo = photosDe(id).getFirst();
        assertThat(google.fileOf(photo).name()).isEqualTo("2026-10-11_" + photo + ".png").doesNotContain("secret").doesNotContain("..");
        assertThat(journaux.getAll()).doesNotContain("secret");
    }
}
