package ca.gestionchalet;

import static org.assertj.core.api.Assertions.assertThat;

import ca.gestionchalet.config.ApplicationProperties;
import ca.gestionchalet.support.RealServerTest;
import ca.gestionchalet.support.RecuRequests;
import ca.gestionchalet.support.TestClient;
import ca.gestionchalet.support.TestClient.Reponse;
import ca.gestionchalet.support.TestClock;
import ca.gestionchalet.support.TestDoublesConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/** Validation d'un reçu de bout en bout : serveur réel, session, CSRF, multipart, base et dossier temporaires. */
@RealServerTest
class RecuRealServerTest {

    @LocalServerPort
    int port;

    @Autowired
    ApplicationProperties properties;

    /** Partagée avec les autres tests du même contexte, qui l'avancent. */
    @Autowired
    TestClock horloge;

    @Autowired
    JdbcTemplate base;

    private TestClient client;

    @BeforeEach
    void connecter() {
        client = new TestClient(port, "source-" + UUID.randomUUID());
        assertThat(client.connecter(TestDoublesConfiguration.MOT_DE_PASSE).statut()).isEqualTo(204);
    }

    private List<Path> fichiers() throws IOException {
        try (Stream<Path> chemins = Files.walk(properties.dossierPhotos())) {
            return chemins.filter(Files::isRegularFile).toList();
        }
    }

    private long lignes(String table) {
        return base.queryForObject("select count(*) from " + table, Long.class);
    }

    private record Etat(long recus, long photos, long fichiers) {
    }

    private Etat etat() throws IOException {
        return new Etat(lignes("recu"), lignes("photo"), fichiers().size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"date", "fournisseur", "montantTotalCents", "typeDepense"})
    void ca14_unChampObligatoireManquantEstRefuseSansRienEnregistrer(String champ) throws IOException {
        Etat avant = etat();
        Map<String, Object> valeurs = RecuRequests.valeurs();
        valeurs.remove(champ);

        Reponse reponse = RecuRequests.valider(client, RecuRequests.corps(valeurs), RecuRequests.png());

        assertThat(reponse.statut()).isEqualTo(400);
        assertThat(reponse.json()).isEqualTo(Map.of("erreur", "Certains champs sont invalides.", "code", "VALIDATION",
                "champs", List.of(Map.of("champ", champ, "code", "OBLIGATOIRE"))));
        assertThat(etat()).isEqualTo(avant);
    }

    @Test
    void ca14_unFournisseurFaitDEspacesEstManquant() throws IOException {
        Etat avant = etat();
        Map<String, Object> valeurs = RecuRequests.valeurs();
        valeurs.put("fournisseur", "   ");

        Reponse reponse = RecuRequests.valider(client, RecuRequests.corps(valeurs), RecuRequests.png());

        assertThat(reponse.statut()).isEqualTo(400);
        assertThat(reponse.json().get("champs")).isEqualTo(List.of(Map.of("champ", "fournisseur", "code", "OBLIGATOIRE")));
        assertThat(etat()).isEqualTo(avant);
    }

    @Test
    void ca14_uneValidationSansPhotoEstRefuseeSansRienEnregistrer() throws IOException {
        Etat avant = etat();

        Reponse reponse = RecuRequests.valider(client, RecuRequests.corps(RecuRequests.valeurs()));

        assertThat(reponse.statut()).isEqualTo(400);
        assertThat(reponse.json()).isEqualTo(Map.of("erreur", "Au moins une photo est requise.", "code", "PHOTO_MANQUANTE"));
        assertThat(etat()).isEqualTo(avant);
    }

    @Test
    void ca14_unePhotoVideCompteCommeUnePhotoManquante() throws IOException {
        Etat avant = etat();

        Reponse reponse = RecuRequests.valider(client, RecuRequests.corps(RecuRequests.valeurs()), new byte[0]);

        assertThat(reponse.statut()).isEqualTo(400);
        assertThat(reponse.json().get("code")).isEqualTo("PHOTO_MANQUANTE");
        assertThat(etat()).isEqualTo(avant);
    }

    @Test
    void ca20_unReçuSaisiALaMainAvecUnePhotoEstCreeSansTotalLu() throws IOException {
        byte[] photo = RecuRequests.png();
        List<Path> avant = fichiers();

        Reponse reponse = RecuRequests.valider(client, RecuRequests.corps(RecuRequests.valeurs()), photo);

        assertThat(reponse.statut()).isEqualTo(201);
        assertThat(reponse.json()).containsEntry("montantTotalLuCents", null).containsEntry("etatCopie", "A_COPIER");
        List<Path> nouveaux = fichiers().stream().filter(chemin -> !avant.contains(chemin)).toList();
        assertThat(nouveaux).hasSize(1);
        assertThat(Files.readAllBytes(nouveaux.getFirst())).isEqualTo(photo);
    }

    @Test
    @SuppressWarnings("unchecked")
    void ca75_leJsonDuReçuContientExactementLesChampsDuContrat() {
        Map<String, Object> saisies = RecuRequests.valeurs();
        saisies.put("objet", "Objet témoin");
        saisies.put("montantAvantTaxesCents", 26500);
        saisies.put("tpsCents", 0);
        saisies.put("numeroInscriptionTps", "123456789RT0001");
        Map<String, Object> corps = RecuRequests.corps(saisies);
        corps.put("montantTotalLuCents", 30468);
        byte[] photo = RecuRequests.png();

        Reponse reponse = RecuRequests.valider(client, corps, photo);

        Map<String, Object> recu = reponse.json();
        assertThat(recu).containsOnlyKeys("id", "version", "valeurs", "montantTotalLuCents", "alertes", "etatCopie",
                "motifBlocage", "prochaineTentative", "photos", "valideLe", "corrigeLe");
        assertThat(UUID.fromString((String) recu.get("id"))).isNotNull();
        assertThat(recu).containsEntry("version", 1).containsEntry("montantTotalLuCents", 30468)
                .containsEntry("alertes", List.of()).containsEntry("etatCopie", "A_COPIER")
                .containsEntry("motifBlocage", null).containsEntry("prochaineTentative", null).containsEntry("corrigeLe", null);
        assertThat((String) recu.get("valideLe")).isEqualTo(horloge.instant().toString());

        Map<String, Object> valeurs = (Map<String, Object>) recu.get("valeurs");
        assertThat(valeurs).containsOnlyKeys("date", "fournisseur", "objet", "typeDepense", "montantAvantTaxesCents",
                "tpsCents", "tvqCents", "montantTotalCents", "numeroInscriptionTps", "numeroInscriptionTvq");
        assertThat(valeurs).containsEntry("date", "2026-10-11").containsEntry("fournisseur", "Fournisseur témoin")
                .containsEntry("objet", "Objet témoin").containsEntry("montantAvantTaxesCents", 26500)
                .containsEntry("tpsCents", 0).containsEntry("tvqCents", null).containsEntry("montantTotalCents", 3500)
                .containsEntry("numeroInscriptionTps", "123456789RT0001").containsEntry("numeroInscriptionTvq", null);

        List<Map<String, Object>> photos = (List<Map<String, Object>>) recu.get("photos");
        assertThat(photos).hasSize(1);
        assertThat(photos.getFirst()).containsOnlyKeys("rang", "format", "taille", "lienDrive")
                .containsEntry("rang", 1).containsEntry("format", "PNG").containsEntry("taille", photo.length).containsEntry("lienDrive", null);
        assertThat(reponse.corps()).doesNotContain("test-data", "photos-", "recu.png", "jeton", "token");
    }

    @Test
    void ca20_unePhotoDeFormatHorsListeEstRefusee() {
        Reponse reponse = RecuRequests.validerAvecType(client, RecuRequests.corps(RecuRequests.valeurs()), "image/gif", RecuRequests.png());

        assertThat(reponse.statut()).isEqualTo(415);
        assertThat(reponse.json()).isEqualTo(Map.of("erreur", "Format non accepté.", "code", "FORMAT_NON_ACCEPTE"));
    }
}
