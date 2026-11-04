package ca.gestionchalet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import ca.gestionchalet.config.ApplicationProperties;
import ca.gestionchalet.support.RealServerTest;
import ca.gestionchalet.support.RecuRequests;
import ca.gestionchalet.support.TestClient;
import ca.gestionchalet.support.TestClient.Reponse;
import ca.gestionchalet.support.TestDoublesConfiguration;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.AdditionalAnswers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.support.StandardMultipartHttpServletRequest;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;

/** La lecture d'une photo reçue échoue : seule cette frontière est doublée, le reste de la requête est réel. */
@RealServerTest
@Import(RecuPhotoReadFailureRealServerTest.FailingReadConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class RecuPhotoReadFailureRealServerTest {

    private static final String TEMOIN_FOURNISSEUR = "Fournisseur-temoin-lecture";
    private static final String TEMOIN_PANNE = "valeur-temoin-lecture-photo";
    private static final int NO_FAILURE = -1;
    /** Lectures de photo encore permises avant la seule qui échoue ; une valeur négative n'en fait échouer aucune. */
    private static final AtomicInteger READS_BEFORE_FAILURE = new AtomicInteger(NO_FAILURE);

    @LocalServerPort
    int port;

    @Autowired
    ApplicationProperties properties;

    @Autowired
    JdbcTemplate base;

    @TestConfiguration
    static class FailingReadConfiguration {

        /** Le nom est celui que cherche le {@code DispatcherServlet}. */
        @Bean
        StandardServletMultipartResolver multipartResolver() {
            return new StandardServletMultipartResolver() {
                @Override
                public MultipartHttpServletRequest resolveMultipart(HttpServletRequest request) {
                    return new StandardMultipartHttpServletRequest(request) {
                        @Override
                        public List<MultipartFile> getFiles(String name) {
                            return super.getFiles(name).stream().map(RecuPhotoReadFailureRealServerTest::failingOnDemand).toList();
                        }
                    };
                }
            };
        }
    }

    private static MultipartFile failingOnDemand(MultipartFile file) {
        MultipartFile wrapped = mock(MultipartFile.class, AdditionalAnswers.delegatesTo(file));
        try {
            doAnswer(invocation -> {
                if (READS_BEFORE_FAILURE.getAndDecrement() == 0) {
                    throw new IOException(TEMOIN_PANNE);
                }
                return file.getInputStream();
            }).when(wrapped).getInputStream();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return wrapped;
    }

    @AfterEach
    void retirerLaPanne() {
        READS_BEFORE_FAILURE.set(NO_FAILURE);
    }

    private List<Path> fichiers() throws IOException {
        try (Stream<Path> chemins = Files.walk(properties.dossierPhotos())) {
            return chemins.filter(Files::isRegularFile).toList();
        }
    }

    private long recus() {
        return base.queryForObject("select count(*) from recu", Long.class);
    }

    @Test
    void ca19_lEchecDeLectureAuControleNeLaisseNiRecuNiFichierEtLaMemeCleReussitEnsuite(CapturedOutput journaux) throws IOException {
        verifierQuUneLectureEnEchecNeLaisseRien(0, journaux);
    }

    @Test
    void ca19_lEchecDeLectureDUnePhotoNeLaisseNiRecuNiFichierEtLaMemeCleReussitEnsuite(CapturedOutput journaux) throws IOException {
        verifierQuUneLectureEnEchecNeLaisseRien(1, journaux);
    }

    @Test
    void ca19_lEchecDeLaSecondeLectureAuStockageNeLaisseNiRecuNiFichierEtLaMemeCleReussitEnsuite(CapturedOutput journaux) throws IOException {
        verifierQuUneLectureEnEchecNeLaisseRien(2, journaux);
    }

    /** La première lecture d'une photo la contrôle, la deuxième calcule l'empreinte, la troisième l'écrit. */
    private void verifierQuUneLectureEnEchecNeLaisseRien(int readsBeforeFailure, CapturedOutput journaux) throws IOException {
        TestClient client = new TestClient(port, "source-" + UUID.randomUUID());
        assertThat(client.connecter(TestDoublesConfiguration.MOT_DE_PASSE).statut()).isEqualTo(204);
        Map<String, Object> valeurs = RecuRequests.valeurs();
        valeurs.put("fournisseur", TEMOIN_FOURNISSEUR);
        Map<String, Object> corps = RecuRequests.corps(valeurs);
        byte[] photo = RecuRequests.png();
        List<Path> fichiersAvant = fichiers();
        long recusAvant = recus();
        READS_BEFORE_FAILURE.set(readsBeforeFailure);

        Reponse panne = RecuRequests.valider(client, corps, photo);

        assertThat(READS_BEFORE_FAILURE.get()).isNegative();
        assertThat(panne.statut()).isEqualTo(500);
        assertThat(panne.json()).isEqualTo(Map.of("erreur", "Une erreur interne est survenue.", "code", "ERREUR_INTERNE"));
        assertThat(recus()).isEqualTo(recusAvant);
        assertThat(fichiers()).isEqualTo(fichiersAvant);
        assertThat(journaux.getAll()).doesNotContain(TEMOIN_FOURNISSEUR, TEMOIN_PANNE, "recu.png");

        assertThat(RecuRequests.valider(client, corps, photo).statut()).isEqualTo(201);
        assertThat(recus()).isEqualTo(recusAvant + 1);
    }
}
