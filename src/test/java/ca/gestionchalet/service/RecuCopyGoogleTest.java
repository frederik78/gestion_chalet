package ca.gestionchalet.service;

import static org.assertj.core.api.Assertions.assertThat;

import ca.gestionchalet.service.dto.RecuValidationDTO;
import ca.gestionchalet.service.dto.ValeursDTO;
import ca.gestionchalet.support.ApiStub;
import ca.gestionchalet.support.ApiStub.Request;
import ca.gestionchalet.support.ApiStub.Response;
import ca.gestionchalet.support.RecuRequests;
import ca.gestionchalet.support.TestDoublesConfiguration;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * La copie par les vrais adaptateurs, qui parlent à un serveur local à la place de Drive et du Sheet, avec le vrai jeton d'accès de Spring Security : la forme exacte de ce
 * qui part vers Google, tout recueilli sur le fil. Aucun appel ne quitte la machine.
 */
@SpringBootTest(properties = {"recus.securite.condense-mot-de-passe=" + TestDoublesConfiguration.CONDENSE, "essai.google-en-memoire=false"})
@Import(TestDoublesConfiguration.class)
class RecuCopyGoogleTest {

    private static final ApiStub GOOGLE_API = demarrer();

    private static ApiStub demarrer() {
        try {
            return new ApiStub();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void adresses(DynamicPropertyRegistry registre) {
        registre.add("recus.copie.adresse-drive", GOOGLE_API::address);
        registre.add("recus.copie.adresse-sheets", GOOGLE_API::address);
        registre.add("recus.copie.annees.2026.fichier", () -> "fichier-essai");
        registre.add("recus.copie.annees.2026.onglet", () -> "Comptes 2026");
    }

    @AfterAll
    static void arreter() {
        GOOGLE_API.close();
    }

    @Autowired
    RecuCopyService copie;

    @Autowired
    RecuService recus;

    @Autowired
    GoogleAuthorizationService autorisations;

    @Autowired
    ClientRegistrationRepository enregistrements;

    @Autowired
    JdbcTemplate base;

    @BeforeEach
    void repartirDeZero() {
        base.update("delete from recu_alerte");
        base.update("delete from photo");
        base.update("delete from recu");
        base.update("delete from autorisation_google");
        GOOGLE_API.forget();
        GOOGLE_API.answer(RecuCopyGoogleTest::unGoogleVide);
    }

    /** Un Drive sans dossier ni fichier, et un Sheet dont l'onglet de 2026 n'a que sa ligne d'en-têtes et les titres de septembre et d'octobre. */
    private static Response unGoogleVide(Request requete) {
        if (requete.is("GET", "/drive/v3/files")) {
            return Response.json("{\"files\":[]}");
        }
        if (requete.is("POST", "/drive/v3/files")) {
            return Response.json("{\"id\":\"dossier-drive\"}");
        }
        if (requete.is("POST", "/upload/drive/v3/files")) {
            return Response.json("{\"id\":\"fichier-drive-" + (GOOGLE_API.requests().stream().filter(r -> r.path().startsWith("/upload")).count()) + "\"}");
        }
        if (requete.is("GET", "/v4/spreadsheets/fichier-essai")) {
            return Response.json("{\"sheets\":[{\"properties\":{\"sheetId\":7,\"title\":\"Comptes 2026\",\"gridProperties\":{\"rowCount\":100}}}]}");
        }
        if (requete.path().endsWith("/developerMetadata:search")) {
            return Response.json("{}");
        }
        if (requete.path().contains("/values/")) {
            return Response.json("{\"values\":[[\"DATE\",\"BOUTIQUE\",\"OBJET\",\"TYPE\",\"MONTANT HT\",\"TPS\",\"TVQ\",\"MONTANT TOTAL\",\"NOTES\",\"PHOTO\"],[\"septembre\"],[\"octobre\"]]}");
        }
        return requete.path().endsWith(":batchUpdate") ? Response.json("{}") : Response.status(404);
    }

    /** Une autorisation valide, jetons chiffrés comme le fait l'application : le jeton d'accès ne doit pas être renouvelé. */
    private void autoriser() {
        ClientRegistration enregistrement = enregistrements.findByRegistrationId("google");
        Instant maintenant = Instant.now();
        OAuth2AccessToken acces = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "jeton-acces-temoin", maintenant, maintenant.plusSeconds(3600),
                Set.copyOf(enregistrement.getScopes()));
        autorisations.saveAuthorizedClient(new OAuth2AuthorizedClient(enregistrement, "application", acces,
                new OAuth2RefreshToken("jeton-renouvellement-temoin", maintenant)), null);
    }

    private UUID valider(ValeursDTO valeurs, int photos) {
        List<MockMultipartFile> fichiers = new java.util.ArrayList<>();
        for (int i = 0; i < photos; i++) {
            fichiers.add(new MockMultipartFile("photos", "x.png", "image/png", RecuRequests.png(4 + i)));
        }
        return recus.validate(new RecuValidationDTO(UUID.randomUUID(), valeurs, null), List.copyOf(fichiers)).recu().id();
    }

    private String etat(UUID id) {
        return base.queryForObject("select etat_copie from recu where id = ?", String.class, id);
    }

    private static String toutCeQuiEstParti() {
        StringBuilder tout = new StringBuilder();
        for (Request requete : GOOGLE_API.requests()) {
            tout.append(requete.method()).append(' ').append(requete.path()).append('?').append(requete.query()).append(' ')
                    .append(requete.headers()).append(' ').append(new String(requete.body(), StandardCharsets.UTF_8)).append('\n');
        }
        return tout.toString();
    }

    @Test
    void ca38_ca39_cs16_cs26_leRecuCopieParLesVraisAdaptateursNeLaisseAucunNumeroNiPermissionEtLesLiensViennentDeDrive() {
        autoriser();
        String formule = "=IMPORTXML(\"https://exemple.invalid\",\"//a\")";
        UUID id = valider(new ValeursDTO(LocalDate.of(2026, 9, 25), formule, "+objet", "Fournitures", 26500L, 1325L, 2643L, 30468L,
                "123456789RT0001", "1234567890TQ0001"), 2);

        assertThat(copie.processNext()).isTrue();

        assertThat(etat(id)).isEqualTo("COPIEE");
        List<Request> requetes = GOOGLE_API.requests();
        int dernierDepot = requetes.stream().map(Request::path).toList().lastIndexOf("/upload/drive/v3/files");
        int premiereLigne = requetes.stream().map(Request::path).toList().indexOf("/v4/spreadsheets/fichier-essai");
        assertThat(dernierDepot).isGreaterThanOrEqualTo(0).isLessThan(premiereLigne);
        assertThat(requetes).allSatisfy(requete -> assertThat(requete.header("Authorization")).isEqualTo("Bearer jeton-acces-temoin"));
        String tout = toutCeQuiEstParti();
        assertThat(tout).doesNotContain("123456789RT0001").doesNotContain("1234567890TQ0001").doesNotContainIgnoringCase("permission");
        assertThat(tout).contains("\"stringValue\":\"=IMPORTXML(\\\"https://exemple.invalid\\\",\\\"//a\\\")\"").contains("\"stringValue\":\"+objet\"").doesNotContain("formulaValue");
        assertThat(tout).contains("\"uri\":\"https://drive.google.com/file/d/fichier-drive-1/view\"").contains("\"uri\":\"https://drive.google.com/file/d/fichier-drive-2/view\"")
                .contains("\"stringValue\":\"Photo 1, Photo 2\"");
        assertThat(requetes).allSatisfy(requete -> assertThat(requete.path()).matches("/(drive|upload/drive|v4/spreadsheets)/.*"));
    }

    @Test
    void d47_uneCleDeChiffrementChangeeEstDetecteeAuPremierAppelDeLaCopieQuiAttendUneAutorisation() {
        base.update("insert into autorisation_google (id, etat, jeton_acces_chiffre, jeton_acces_expire_le, jeton_renouvellement_chiffre) "
                + "values (1, 'VALIDE', 'chiffre-avec-une-autre-cle', '2999-01-01T00:00:00Z', 'chiffre-avec-une-autre-cle')");
        UUID id = valider(new ValeursDTO(LocalDate.of(2026, 10, 11), "F", null, "Fournitures", null, null, null, 1586L, null, null), 1);
        assertThat(etat(id)).isEqualTo("A_COPIER");
        assertThat(autorisations.state().etat()).isEqualTo("VALIDE");

        assertThat(copie.processNext()).isTrue();

        assertThat(autorisations.state().etat()).isEqualTo("A_RENOUVELER");
        assertThat(etat(id)).isEqualTo("BLOQUEE");
        assertThat(base.queryForObject("select motif_blocage from recu where id = ?", String.class, id)).isEqualTo("AUTORISATION_GOOGLE");
        assertThat(GOOGLE_API.requests()).isEmpty();
    }
}
