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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

    private Reponse validerAvec(String champ, Object valeur) {
        Map<String, Object> valeurs = RecuRequests.valeurs();
        valeurs.put(champ, valeur);
        return RecuRequests.valider(client, RecuRequests.corps(valeurs), RecuRequests.png());
    }

    private static Map<String, Object> erreurDeChamp(String champ, String code) {
        return Map.of("erreur", "Certains champs sont invalides.", "code", "VALIDATION", "champs", List.of(Map.of("champ", champ, "code", code)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Honoraires professionnel", "Fournitures", "Entretien / Réparations", "Électricité / Internet",
        "Impôts / Taxes", "Assurances", "Publicité", "Autres dépenses", "Frais de voyage", "Intérêts", "Administration"})
    void ca15_chacunDesOnzeTypesEstAccepte(String type) {
        Reponse reponse = validerAvec("typeDepense", type);

        assertThat(reponse.statut()).isEqualTo(201);
        assertThat(reponse.corps()).contains(type);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Autres", "fournitures", " Fournitures"})
    void ca15_unTypeHorsListeEstRefuse(String type) throws IOException {
        Etat avant = etat();

        Reponse reponse = validerAvec("typeDepense", type);

        assertThat(reponse.statut()).isEqualTo(400);
        assertThat(reponse.json()).isEqualTo(erreurDeChamp("typeDepense", "HORS_LISTE"));
        assertThat(etat()).isEqualTo(avant);
    }

    @Test
    void ca16_uneDatePosterieureAAujourdhuiAMontrealEstRefuseeMemeSiElleEstAujourdhuiEnUtc() {
        Instant maintenant = horloge.instant();
        Instant deuxHeuresUtc = maintenant.atZone(ZoneOffset.UTC).toLocalDate().plusDays(1).atTime(2, 0).toInstant(ZoneOffset.UTC);
        horloge.avancer(Duration.between(maintenant, deuxHeuresUtc));
        LocalDate aujourdhuiUtc = deuxHeuresUtc.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate aujourdhuiMontreal = aujourdhuiUtc.minusDays(1);

        Reponse future = validerAvec("date", aujourdhuiUtc.toString());
        Reponse aujourdhui = validerAvec("date", aujourdhuiMontreal.toString());

        assertThat(future.statut()).isEqualTo(400);
        assertThat(future.json()).isEqualTo(erreurDeChamp("date", "BORNE"));
        assertThat(aujourdhui.statut()).isEqualTo(201);
    }

    @ParameterizedTest
    @CsvSource({"montantTotalCents, 0", "montantTotalCents, -1", "montantTotalCents, 100000001", "tpsCents, -1", "tvqCents, 100000001",
        "montantAvantTaxesCents, -1", "montantAvantTaxesCents, 100000001"})
    void ca16_unMontantHorsBornesEstRefuse(String champ, long cents) throws IOException {
        Etat avant = etat();

        Reponse reponse = validerAvec(champ, cents);

        assertThat(reponse.statut()).isEqualTo(400);
        assertThat(reponse.json()).isEqualTo(erreurDeChamp(champ, "BORNE"));
        assertThat(etat()).isEqualTo(avant);
    }

    @Test
    void ca16_leTotalLuHorsBornesEstRefuse() {
        Map<String, Object> corps = RecuRequests.corps(RecuRequests.valeurs());
        corps.put("montantTotalLuCents", 100000001);

        Reponse reponse = RecuRequests.valider(client, corps, RecuRequests.png());

        assertThat(reponse.statut()).isEqualTo(400);
        assertThat(reponse.json()).isEqualTo(erreurDeChamp("montantTotalLuCents", "BORNE"));
    }

    @ParameterizedTest
    @CsvSource({"montantTotalCents, 1", "montantTotalCents, 100000000", "tpsCents, 0", "tvqCents, 100000000"})
    void ca16_unMontantALaLimiteEstAccepte(String champ, long cents) {
        assertThat(validerAvec(champ, cents).statut()).isEqualTo(201);
    }

    @ParameterizedTest
    @ValueSource(strings = {"35.5", "\"3500\"", "\"abc\"", "3.5e2"})
    void ca16_unMontantDecimalOuTexteEstRefuse(String montant) throws IOException {
        Etat avant = etat();
        String corps = "{\"cleIdempotence\":\"" + UUID.randomUUID() + "\",\"valeurs\":{\"date\":\"2026-10-11\",\"fournisseur\":\"F\","
                + "\"typeDepense\":\"Fournitures\",\"montantTotalCents\":" + montant + "}}";

        Reponse reponse = RecuRequests.validerJson(client, corps, RecuRequests.png());

        assertThat(reponse.statut()).isEqualTo(400);
        assertThat(reponse.json()).isEqualTo(Map.of("erreur", "La requête est invalide.", "code", "REQUETE_INVALIDE"));
        assertThat(etat()).isEqualTo(avant);
    }

    @Test
    void ca16_lesTextesSontBornesEtSansCaractereDeControle() {
        assertThat(validerAvec("fournisseur", "x".repeat(120)).statut()).isEqualTo(201);
        assertThat(validerAvec("fournisseur", "x".repeat(121)).json()).isEqualTo(erreurDeChamp("fournisseur", "BORNE"));
        assertThat(validerAvec("fournisseur", "a\nb").json()).isEqualTo(erreurDeChamp("fournisseur", "FORMAT"));
        assertThat(validerAvec("objet", "x".repeat(200)).statut()).isEqualTo(201);
        assertThat(validerAvec("objet", "x".repeat(201)).json()).isEqualTo(erreurDeChamp("objet", "BORNE"));
        assertThat(validerAvec("objet", "a\u0007b").json()).isEqualTo(erreurDeChamp("objet", "FORMAT"));
        assertThat(validerAvec("numeroInscriptionTps", "1".repeat(30)).statut()).isEqualTo(201);
        assertThat(validerAvec("numeroInscriptionTps", "1".repeat(31)).json()).isEqualTo(erreurDeChamp("numeroInscriptionTps", "BORNE"));
        assertThat(validerAvec("numeroInscriptionTvq", "12<3").json()).isEqualTo(erreurDeChamp("numeroInscriptionTvq", "FORMAT"));
    }

    @Test
    void ca18_unRenvoiAvecLaMemeCleRendLeMemeRecuSansRienAjouter() throws IOException {
        Map<String, Object> corps = RecuRequests.corps(RecuRequests.valeurs());
        Reponse premier = RecuRequests.valider(client, corps, RecuRequests.png());
        assertThat(premier.statut()).isEqualTo(201);
        Etat apres = etat();

        Reponse second = RecuRequests.valider(client, corps, RecuRequests.png());

        assertThat(second.statut()).isEqualTo(200);
        assertThat(second.json()).isEqualTo(premier.json());
        assertThat(etat()).isEqualTo(apres);
    }

    @Test
    void ca18_deuxEnvoisSimultanesNeCreentQuUnSeulRecu() throws IOException {
        Map<String, Object> corps = RecuRequests.corps(RecuRequests.valeurs());
        byte[] photo = RecuRequests.png(400);
        Etat avant = etat();

        List<Reponse> reponses;
        try (ExecutorService fils = Executors.newFixedThreadPool(4)) {
            List<CompletableFuture<Reponse>> envois = IntStream.range(0, 4)
                    .mapToObj(i -> CompletableFuture.supplyAsync(() -> RecuRequests.valider(client, corps, photo), fils))
                    .toList();
            reponses = envois.stream().map(CompletableFuture::join).toList();
        }

        assertThat(reponses).extracting(Reponse::statut).containsExactlyInAnyOrder(201, 200, 200, 200);
        assertThat(reponses).extracting(reponse -> reponse.json().get("id")).containsOnly(reponses.getFirst().json().get("id"));
        assertThat(etat()).isEqualTo(new Etat(avant.recus() + 1, avant.photos() + 1, avant.fichiers() + 1));
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
