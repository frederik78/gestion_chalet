package ca.gestionchalet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import ca.gestionchalet.domain.RegenerationRules;
import ca.gestionchalet.service.SheetRegenerationService;
import ca.gestionchalet.service.dto.RegenerationStateDTO;
import ca.gestionchalet.support.InMemoryGoogle;
import ca.gestionchalet.support.RealServerTest;
import ca.gestionchalet.support.RecuRequests;
import ca.gestionchalet.support.TestClient;
import ca.gestionchalet.support.TestClock;
import ca.gestionchalet.support.TestDoublesConfiguration;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** Le fil d'exécution de la régénération : un exécuteur de Spring, à un seul fil, et une tâche qui ne laisse jamais l'état à {@code EN_COURS} quand la base répond. */
@RealServerTest
class SheetRegenerationExecutionRealServerTest {

    private static final String FICHIER_2026 = "fichier-essai";
    private static final String FOURNISSEUR_SECRET = "Fournisseur-secret-du-journal";

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate base;

    @Autowired
    SheetRegenerationService regeneration;

    @Autowired
    InMemoryGoogle google;

    @Autowired
    ApplicationContext contexte;

    @MockitoSpyBean
    RegenerationRules rules;

    @MockitoSpyBean
    TestClock horloge;

    @MockitoSpyBean(name = "regenerationExecutor")
    @Qualifier("regenerationExecutor")
    TaskExecutor executeur;

    private final ListAppender<ILoggingEvent> journal = new ListAppender<>();
    private Logger logger;

    @BeforeEach
    void repartirDeZero() {
        base.update("delete from onglet_genere_cellule");
        base.update("delete from onglet_genere");
        base.update("delete from regeneration_annee_non_configuree");
        base.update("delete from regeneration");
        base.update("delete from recu_alerte");
        base.update("delete from photo");
        base.update("delete from recu");
        google.reset();
        google.seedFile(FICHIER_2026);
        doReturn(Map.of(2026, FICHIER_2026)).when(rules).files();
        TestClient client = new TestClient(port, "source-" + UUID.randomUUID());
        assertThat(client.connecter(TestDoublesConfiguration.MOT_DE_PASSE).statut()).isEqualTo(204);
        Map<String, Object> valeurs = RecuRequests.valeurs();
        valeurs.put("fournisseur", FOURNISSEUR_SECRET);
        assertThat(RecuRequests.valider(client, RecuRequests.corps(valeurs), RecuRequests.png()).statut()).isEqualTo(201);
        logger = (Logger) LoggerFactory.getLogger(SheetRegenerationService.class);
        journal.start();
        logger.addAppender(journal);
    }

    @AfterEach
    void retirerLeJournal() {
        logger.detachAppender(journal);
    }

    private RegenerationStateDTO attendreLaFin() {
        long limite = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (regeneration.state().etat().equals("EN_COURS")) {
            if (System.nanoTime() > limite) {
                fail("La régénération est restée EN_COURS");
            }
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Interrompu");
            }
        }
        return regeneration.state();
    }

    @Test
    void l_executeurDeLaRegenerationEstUnBeanNommeEtLExecuteurAutoConfigureDeSpringBootRestePresent() {
        assertThat(contexte.getBean("regenerationExecutor")).isInstanceOf(org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor.class);
        assertThat(contexte.containsBean("applicationTaskExecutor")).isTrue();
    }

    @Test
    void uneExceptionLeveeParLEcritureDeLaReussiteEtParCelleDeLEchecEstJournaliseeSansContenuEtLEtatNeResteJamaisEnCours() {
        AtomicInteger lecturesEnEchec = new AtomicInteger(2);
        doAnswer(appel -> {
            if (Thread.currentThread().getName().startsWith("regeneration-sheet") && lecturesEnEchec.getAndDecrement() > 0) {
                throw new IllegalStateException(FOURNISSEUR_SECRET);
            }
            return appel.callRealMethod();
        }).when(horloge).instant();

        regeneration.request();
        RegenerationStateDTO etat = attendreLaFin();

        assertThat(etat.etat()).isEqualTo("ECHEC");
        assertThat(journal.list).anySatisfy(evenement -> assertThat(evenement.getFormattedMessage()).contains("IllegalStateException"));
        assertThat(journal.list).extracting(ILoggingEvent::getFormattedMessage).noneMatch(message -> message.contains(FOURNISSEUR_SECRET));
        assertThat(journal.list).extracting(ILoggingEvent::getThrowableProxy).as("aucune trace de pile, dont le message peut citer un reçu").containsOnlyNulls();
    }

    @Test
    void unRejetDeLaTacheNeLaisseJamaisLEtatEnCoursEtLaDemandeEchoue() {
        doThrow(new TaskRejectedException("exécuteur arrêté ou plein")).when(executeur).execute(any());

        assertThatThrownBy(regeneration::request).isInstanceOf(TaskRejectedException.class);

        assertThat(regeneration.state().etat()).isEqualTo("ECHEC");
        assertThat(google.requests()).isEmpty();
    }

    @Test
    void uneTacheQuiTermineLaisseUnEtatFinalMemeAvecLeVraiExecuteur() {
        regeneration.request();

        assertThat(attendreLaFin().etat()).isEqualTo("REUSSIE");
        assertThat(google.tabsOf(FICHIER_2026)).isNotEmpty();
    }
}
