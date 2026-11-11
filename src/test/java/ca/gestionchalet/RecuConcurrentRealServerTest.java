package ca.gestionchalet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import ca.gestionchalet.config.ApplicationProperties;
import ca.gestionchalet.repository.PhotoStorage;
import ca.gestionchalet.support.RealServerTest;
import ca.gestionchalet.support.RecuRequests;
import ca.gestionchalet.support.TestClient;
import ca.gestionchalet.support.TestClient.Reponse;
import ca.gestionchalet.support.TestDoublesConfiguration;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** Des envois réellement simultanés : le premier ne continue qu'une fois les autres arrivés, en attente du lien ou écrivant eux aussi. */
@RealServerTest
class RecuConcurrentRealServerTest {

    private static final int ENVOIS = 4;

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate base;

    @Autowired
    ApplicationProperties properties;

    @MockitoSpyBean
    PhotoStorage stockage;

    private long fichiers() throws IOException {
        try (Stream<Path> chemins = Files.walk(properties.dossierPhotos())) {
            return chemins.filter(Files::isRegularFile).count();
        }
    }

    private long lignes(String table) {
        return base.queryForObject("select count(*) from " + table, Long.class);
    }

    @Test
    void ca18_deuxEnvoisSimultanesNeCreentQuUnSeulRecu() throws SQLException, IOException {
        TestClient client = new TestClient(port, "source-" + UUID.randomUUID());
        assertThat(client.connecter(TestDoublesConfiguration.MOT_DE_PASSE).statut()).isEqualTo(204);
        HikariDataSource liens = base.getDataSource().unwrap(HikariDataSource.class);
        AtomicInteger ecrivains = new AtomicInteger();
        AtomicBoolean chevauchement = new AtomicBoolean();
        doAnswer(invocation -> {
            ecrivains.incrementAndGet();
            Instant limite = Instant.now().plus(Duration.ofSeconds(15));
            while (ecrivains.get() + liens.getHikariPoolMXBean().getThreadsAwaitingConnection() < ENVOIS) {
                if (Instant.now().isAfter(limite)) {
                    throw new IllegalStateException("Envois non simultanés");
                }
                Thread.onSpinWait();
            }
            chevauchement.set(true);
            return invocation.callRealMethod();
        }).when(stockage).write(any(), any());
        Map<String, Object> corps = RecuRequests.corps(RecuRequests.valeurs());
        byte[] photo = RecuRequests.png(400);
        long recusAvant = lignes("recu");
        long photosAvant = lignes("photo");
        long fichiersAvant = fichiers();

        List<Reponse> reponses;
        try (ExecutorService fils = Executors.newFixedThreadPool(ENVOIS)) {
            List<CompletableFuture<Reponse>> envois = IntStream.range(0, ENVOIS)
                    .mapToObj(i -> CompletableFuture.supplyAsync(() -> RecuRequests.valider(client, corps, photo), fils))
                    .toList();
            reponses = envois.stream().map(CompletableFuture::join).toList();
        }

        assertThat(chevauchement).isTrue();
        assertThat(reponses).extracting(Reponse::statut).containsExactlyInAnyOrder(201, 200, 200, 200);
        assertThat(reponses).extracting(reponse -> reponse.json().get("id")).containsOnly(reponses.getFirst().json().get("id"));
        assertThat(lignes("recu")).isEqualTo(recusAvant + 1);
        assertThat(lignes("photo")).isEqualTo(photosAvant + 1);
        assertThat(fichiers()).isEqualTo(fichiersAvant + 1);
    }
}
