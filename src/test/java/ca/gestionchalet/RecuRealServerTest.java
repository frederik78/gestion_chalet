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
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

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

    @Test
    void laConfigurationAnnonceLaListeEtLesLimitesAuxEcrans() {
        Reponse reponse = client.get("/api/configuration");

        assertThat(reponse.statut()).isEqualTo(200);
        assertThat(reponse.json()).isEqualTo(Map.of(
                "typesDepense", List.of("Honoraires professionnel", "Fournitures", "Entretien / Réparations", "Électricité / Internet",
                        "Impôts / Taxes", "Assurances", "Publicité", "Autres dépenses", "Frais de voyage", "Intérêts", "Administration"),
                "photosParRecuMax", 5,
                "tailleParPhotoMaxOctets", 15728640,
                "formats", List.of("image/jpeg", "image/png")));
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

    @ParameterizedTest
    @CsvSource({"fournisseur, 0085", "fournisseur, 009B", "objet, 0085", "objet, 009B"})
    void unCaractereDeControleHorsAsciiEstRefuse(String champ, String pointDeCode) {
        String texte = "a" + (char) Integer.parseInt(pointDeCode, 16) + "b";

        assertThat(validerAvec(champ, texte).json()).isEqualTo(erreurDeChamp(champ, "FORMAT"));
    }

    @Test
    void lesTextesSontBornesEtSansCaractereDeControle() {
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

    private static final Map<String, Object> CLE_DEJA_UTILISEE = Map.of(
            "erreur", "Ce reçu est déjà enregistré avec un autre contenu.", "code", "CLE_DEJA_UTILISEE");

    private Map<Path, String> empreintesDesFichiers() throws IOException {
        Map<Path, String> empreintes = new HashMap<>();
        for (Path fichier : fichiers()) {
            empreintes.put(fichier, HexFormat.of().formatHex(Files.readAllBytes(fichier)));
        }
        return empreintes;
    }

    private void verifierConflit(Map<String, Object> corps, byte[]... photos) throws IOException {
        Etat etatAvant = etat();
        Map<Path, String> fichiersAvant = empreintesDesFichiers();
        List<Map<String, Object>> lignesAvant = base.queryForList("select * from recu order by id");

        Reponse reponse = RecuRequests.valider(client, corps, photos);

        assertThat(reponse.statut()).isEqualTo(409);
        assertThat(reponse.json()).isEqualTo(CLE_DEJA_UTILISEE);
        assertThat(etat()).isEqualTo(etatAvant);
        assertThat(empreintesDesFichiers()).isEqualTo(fichiersAvant);
        assertThat(base.queryForList("select * from recu order by id")).isEqualTo(lignesAvant);
    }

    @ParameterizedTest
    @CsvSource({"montantTotalCents, 9999", "fournisseur, Autre fournisseur", "date, 2026-10-10", "objet, Ajouté"})
    void ca18_unRenvoiAuxValeursDifferentesDonne409EtLeRecuResteInchange(String champ, String valeur) throws IOException {
        Map<String, Object> corps = RecuRequests.corps(RecuRequests.valeurs());
        byte[] photo = RecuRequests.png();
        assertThat(RecuRequests.valider(client, corps, photo).statut()).isEqualTo(201);

        Map<String, Object> valeurs = RecuRequests.valeurs();
        valeurs.put(champ, champ.endsWith("Cents") ? (Object) Long.valueOf(valeur) : valeur);
        Map<String, Object> corrige = new LinkedHashMap<>(corps);
        corrige.put("valeurs", valeurs);

        verifierConflit(corrige, photo);
    }

    private static final Map<String, Object> REQUETE_INVALIDE = Map.of("erreur", "La requête est invalide.", "code", "REQUETE_INVALIDE");

    static Stream<Object> datesMalFormees() {
        return Stream.of("2026-10-11T23:00:00Z", "2026-10-11T23:00:00", List.of(2026, 10, 11), "2026-02-30", "-2026-10-11", " 2026-10-11 ",
                "20261011", "2026-1-1", 20261011, "");
    }

    @ParameterizedTest
    @MethodSource("datesMalFormees")
    void uneDateHorsDuFormatAAAAMMJJEstRefuseeSansRienEnregistrer(Object date) throws IOException {
        Etat avant = etat();
        Map<String, Object> valeurs = RecuRequests.valeurs();
        valeurs.put("date", date);

        Reponse reponse = RecuRequests.valider(client, RecuRequests.corps(valeurs), RecuRequests.png());

        assertThat(reponse.statut()).isEqualTo(400);
        assertThat(reponse.json()).isEqualTo(REQUETE_INVALIDE);
        assertThat(etat()).isEqualTo(avant);
    }

    @ParameterizedTest
    @ValueSource(strings = {"AAAAAAAAAAAAAAAAAAAAAA==", "1-1-1-1-1", "00000000000000000000000000000000", " 00000000-0000-4000-8000-000000000000 ", ""})
    void uneCleHorsDuFormatUuidEstRefuseeSansRienEnregistrer(String cle) throws IOException {
        Etat avant = etat();
        Map<String, Object> corps = RecuRequests.corps(RecuRequests.valeurs());
        corps.put("cleIdempotence", cle);

        Reponse reponse = RecuRequests.valider(client, corps, RecuRequests.png());

        assertThat(reponse.statut()).isEqualTo(400);
        assertThat(reponse.json()).isEqualTo(REQUETE_INVALIDE);
        assertThat(etat()).isEqualTo(avant);
    }

    @Test
    void ca18_uneTaxeVideDevenueZeroEstUnContenuDifferent() throws IOException {
        Map<String, Object> corps = RecuRequests.corps(RecuRequests.valeurs());
        byte[] photo = RecuRequests.png();
        RecuRequests.valider(client, corps, photo);
        Map<String, Object> valeurs = RecuRequests.valeurs();
        valeurs.put("tpsCents", 0);
        Map<String, Object> corrige = new LinkedHashMap<>(corps);
        corrige.put("valeurs", valeurs);

        verifierConflit(corrige, photo);
    }

    @Test
    void ca18_unRenvoiAvecUnePhotoDifferenteDeMemeTailleDonne409() throws IOException {
        Map<String, Object> corps = RecuRequests.corps(RecuRequests.valeurs());
        byte[] photo = RecuRequests.png();
        assertThat(RecuRequests.valider(client, corps, photo).statut()).isEqualTo(201);
        byte[] memeTaille = photo.clone();
        memeTaille[memeTaille.length - 1] ^= 1;

        verifierConflit(corps, memeTaille);
    }

    @Test
    void ca18_desPhotosDansUnAutreOrdreOuEnNombreDifferentDonnent409() throws IOException {
        Map<String, Object> corps = RecuRequests.corps(RecuRequests.valeurs());
        byte[] a = RecuRequests.png(4);
        byte[] b = RecuRequests.png(8);
        assertThat(RecuRequests.valider(client, corps, a, b).statut()).isEqualTo(201);

        verifierConflit(corps, b, a);
        verifierConflit(corps, a);
    }

    @Test
    void ca18_lesMemesValeursApresNormalisationEtUnAutreTotalLuDonnent200() throws IOException {
        Map<String, Object> corps = RecuRequests.corps(RecuRequests.valeurs());
        byte[] photo = RecuRequests.png();
        Reponse premier = RecuRequests.valider(client, corps, photo);
        Etat apres = etat();
        Map<String, Object> valeurs = RecuRequests.valeurs();
        valeurs.put("fournisseur", "  " + valeurs.get("fournisseur") + "  ");
        Map<String, Object> renvoi = new LinkedHashMap<>(corps);
        renvoi.put("valeurs", valeurs);
        renvoi.put("montantTotalLuCents", 1234);

        Reponse second = RecuRequests.valider(client, renvoi, photo);

        assertThat(second.statut()).isEqualTo(200);
        assertThat(second.json()).isEqualTo(premier.json());
        assertThat(etat()).isEqualTo(apres);
    }

    @Test
    void ca72_aucuneRouteNeSupprimeUnRecuNiUnePhoto() throws IOException {
        byte[] photo = RecuRequests.png();
        String id = (String) RecuRequests.valider(client, RecuRequests.corps(RecuRequests.valeurs()), photo).json().get("id");
        Etat avant = etat();

        Reponse recu = client.appelAvecJeton("DELETE", "/api/recus/" + id, "");
        Reponse photoSupprimee = client.appelAvecJeton("DELETE", "/api/recus/" + id + "/photos/1", "");

        assertThat(recu.statut()).isIn(404, 405);
        assertThat(photoSupprimee.statut()).isIn(404, 405);
        assertThat(etat()).isEqualTo(avant);
    }

    @Test
    void cs17_lesChampsCalculesEnvoyesParLeClientSontSansEffet() {
        UUID idDuClient = UUID.randomUUID();
        Map<String, Object> valeurs = RecuRequests.valeurs();
        valeurs.put("etatCopie", "COPIEE");
        valeurs.put("alertes", List.of(Map.of("code", "FAUSSE")));
        Map<String, Object> corps = RecuRequests.corps(valeurs);
        corps.put("id", idDuClient.toString());
        corps.put("etatCopie", "COPIEE");
        corps.put("alertes", List.of(Map.of("code", "FAUSSE")));
        corps.put("motifBlocage", "FORMULE_PRESENTE");
        corps.put("version", 99);

        Reponse reponse = RecuRequests.valider(client, corps, RecuRequests.png());

        assertThat(reponse.statut()).isEqualTo(201);
        assertThat(reponse.json()).containsEntry("etatCopie", "A_COPIER").containsEntry("alertes", List.of())
                .containsEntry("motifBlocage", null).containsEntry("version", 1);
        assertThat(reponse.json().get("id")).isNotEqualTo(idDuClient.toString());
        assertThat(base.queryForObject("select count(*) from recu where id = ?", Long.class, idDuClient.toString())).isZero();
    }

    @Test
    void cs17_unPutPortantDesChampsCalculesNeChangeRien() {
        String id = (String) RecuRequests.valider(client, RecuRequests.corps(RecuRequests.valeurs()), RecuRequests.png()).json().get("id");

        Reponse reponse = client.appelAvecJeton("PUT", "/api/recus/" + id, "{\"etatCopie\":\"COPIEE\",\"alertes\":[],\"motifBlocage\":\"FORMULE_PRESENTE\"}");

        assertThat(reponse.statut()).isEqualTo(404);
        assertThat(base.queryForObject("select etat_copie from recu where id = ?", String.class, id)).isEqualTo("A_COPIER");
    }

    /** Un type qu'une désérialisation polymorphe instancierait : son constructeur laisse une trace. */
    public static final class Piege {

        static final AtomicInteger INSTANCES = new AtomicInteger();

        public Piege() {
            INSTANCES.incrementAndGet();
        }
    }

    private static String corpsAvecValeurs(String debutDeValeurs) {
        return "{\"cleIdempotence\":\"" + UUID.randomUUID() + "\",\"valeurs\":{" + debutDeValeurs
                + "\"date\":\"2026-10-11\",\"fournisseur\":\"F\",\"typeDepense\":\"Fournitures\",\"montantTotalCents\":3500}}";
    }

    @Test
    void cs27_lesMetadonneesDeTypeSontIgnoreesSansInstancierDeType() {
        String type = "\"@class\":\"" + Piege.class.getName() + "\",";
        String corps = "{" + type + corpsAvecValeurs(type).substring(1);

        Reponse reponse = RecuRequests.validerJson(client, corps, RecuRequests.png());

        assertThat(reponse.statut()).isEqualTo(201);
        assertThat(Piege.INSTANCES).hasValue(0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"@class\":\"%s\"}", "[\"%s\",{}]", "{\"a\":{\"b\":1}}"})
    void cs27_unChampImbriqueInattenduEstRefuseSansInstancierDeType(String imbrique) throws IOException {
        Etat avant = etat();
        String valeur = imbrique.formatted(Piege.class.getName());
        String corps = "{\"cleIdempotence\":\"" + UUID.randomUUID() + "\",\"valeurs\":{\"date\":\"2026-10-11\",\"fournisseur\":" + valeur
                + ",\"typeDepense\":\"Fournitures\",\"montantTotalCents\":3500}}";

        Reponse reponse = RecuRequests.validerJson(client, corps, RecuRequests.png());

        assertThat(reponse.statut()).isEqualTo(400);
        assertThat(reponse.json()).isEqualTo(Map.of("erreur", "La requête est invalide.", "code", "REQUETE_INVALIDE"));
        assertThat(Piege.INSTANCES).hasValue(0);
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
    void ca69_unePhotoDeFormatHorsListeEstRefusee() {
        Reponse reponse = RecuRequests.validerAvecType(client, RecuRequests.corps(RecuRequests.valeurs()), "image/gif", RecuRequests.gif(4));

        assertThat(reponse.statut()).isEqualTo(415);
        assertThat(reponse.json()).isEqualTo(Map.of("erreur", "Format non accepté.", "code", "FORMAT_NON_ACCEPTE"));
    }

    /** Un JSON brut : la valeur testée n'est pas une chaîne, et le texte reçu serait sinon converti (123 devenait "123"). */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "fournisseur | 123", "fournisseur | true", "fournisseur | 1.5", "fournisseur | [\"a\"]", "fournisseur | {\"a\":1}",
        "objet | 123", "objet | false", "objet | [\"a\"]", "objet | {\"a\":1}",
        "typeDepense | 123", "typeDepense | true", "typeDepense | [\"Fournitures\"]", "typeDepense | {\"a\":1}",
        "numeroInscriptionTps | 123456789", "numeroInscriptionTps | true", "numeroInscriptionTps | [\"1\"]", "numeroInscriptionTps | {\"a\":1}",
        "numeroInscriptionTvq | 123456789", "numeroInscriptionTvq | false", "numeroInscriptionTvq | [\"1\"]", "numeroInscriptionTvq | {\"a\":1}",
    })
    void unTexteRecuEnJsonDoitEtreUneChaine(String champ, String jsonBrut) throws IOException {
        Etat avant = etat();
        Map<String, Object> valeurs = RecuRequests.valeurs();
        valeurs.put(champ, "@@@");
        String json = JsonMapper.builder().build().writeValueAsString(RecuRequests.corps(valeurs)).replace("\"@@@\"", jsonBrut);

        Reponse reponse = RecuRequests.validerJson(client, json, RecuRequests.png());

        assertThat(reponse.statut()).isEqualTo(400);
        assertThat(reponse.json()).isEqualTo(Map.of("erreur", "La requête est invalide.", "code", "REQUETE_INVALIDE"));
        assertThat(etat()).isEqualTo(avant);
    }

    /** Espace de largeur nulle, inversion d'affichage, indicateur d'ordre, trait d'union conditionnel : invisibles à l'écran, ils changeraient le sens lu. */
    @ParameterizedTest
    @CsvSource({"fournisseur, 200B", "fournisseur, 202E", "fournisseur, FEFF", "fournisseur, 00AD", "objet, 200B", "objet, 202E", "objet, 2066"})
    void unCaractereDeFormatEstRefuse(String champ, String pointDeCode) {
        Reponse reponse = validerAvec(champ, "Fournisseur" + Character.toString(Integer.parseInt(pointDeCode, 16)) + "x");

        assertThat(reponse.statut()).isEqualTo(400);
        assertThat(reponse.json().get("champs")).isEqualTo(List.of(Map.of("champ", champ, "code", "FORMAT")));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> valeursDe(Reponse reponse) {
        return (Map<String, Object>) reponse.json().get("valeurs");
    }

    @ParameterizedTest
    @ValueSource(strings = {"objet", "numeroInscriptionTps", "numeroInscriptionTvq"})
    void unTexteFacultatifPerdSesEspacesEnBordure(String champ) {
        Reponse reponse = validerAvec(champ, "  123 45  ");

        assertThat(reponse.statut()).isEqualTo(201);
        assertThat(valeursDe(reponse)).containsEntry(champ, "123 45");
    }

    /** Un numéro fait d'espaces ne doit pas passer pour un numéro présent : l'alerte de numéros manquants compte sur le vide. */
    @ParameterizedTest
    @CsvSource({"objet, '   '", "objet, ''", "numeroInscriptionTps, '   '", "numeroInscriptionTps, ''", "numeroInscriptionTvq, '   '"})
    void unTexteFacultatifVideOuFaitDEspacesDevientVide(String champ, String valeur) {
        Reponse reponse = validerAvec(champ, valeur);

        assertThat(reponse.statut()).isEqualTo(201);
        assertThat(valeursDe(reponse).get(champ)).isNull();
    }
}
