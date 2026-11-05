package ca.gestionchalet.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/** Serveur HTTP local qui double Google : il répond ce que le test choisit, et garde chaque requête reçue. Aucun appel ne quitte la machine. */
public final class GoogleStub implements AutoCloseable {

    public static final String SHEETS = "https://www.googleapis.com/auth/spreadsheets";
    public static final String DRIVE_FILE = "https://www.googleapis.com/auth/drive.file";
    public static final String JETON_RENOUVELLEMENT = "jeton-renouvellement-temoin";
    public static final String JETON_ACCES = "jeton-acces-temoin";

    /** Une requête reçue : chemin et, pour un formulaire, ses paramètres. */
    public record Requete(String chemin, Map<String, String> parametres) {
    }

    private HttpServer serveur;
    private final int port;
    /** Plusieurs fils : une réponse retardée ne retient pas les requêtes du test suivant. */
    private final ExecutorService fils = Executors.newCachedThreadPool(tache -> {
        Thread fil = new Thread(tache);
        fil.setDaemon(true);
        return fil;
    });
    private final List<Requete> requetes = new CopyOnWriteArrayList<>();
    private volatile int statut;
    private volatile String reponse;
    private volatile long attenteMillis;
    private volatile String typeReponse;

    public GoogleStub() throws IOException {
        serveur = ecouter(0);
        port = serveur.getAddress().getPort();
        reinitialiser();
    }

    private HttpServer ecouter(int portDemande) throws IOException {
        HttpServer nouveau = HttpServer.create(new InetSocketAddress("localhost", portDemande), 0);
        nouveau.createContext("/", this::traiter);
        nouveau.setExecutor(fils);
        nouveau.start();
        return nouveau;
    }

    /** Plus personne n'écoute : la connexion est refusée. */
    public void arreter() {
        serveur.stop(0);
    }

    public void redemarrer() throws IOException {
        serveur = ecouter(port);
    }

    public String adresse() {
        return "http://localhost:" + port;
    }

    public String adresseConsentement() {
        return adresse() + "/consentement";
    }

    public String adresseJeton() {
        return adresse() + "/jeton";
    }

    public List<Requete> requetes() {
        return List.copyOf(requetes);
    }

    /** Tous les droits accordés, avec un jeton de renouvellement. */
    public void reinitialiser() {
        requetes.clear();
        attenteMillis = 0;
        repondreJeton(3600, SHEETS + " " + DRIVE_FILE, JETON_RENOUVELLEMENT);
    }

    /** {@code jetonRenouvellement} ou {@code droits} à {@code null} : absents de la réponse. */
    public void repondreJeton(int expireDans, String droits, String jetonRenouvellement) {
        Map<String, String> champs = new LinkedHashMap<>();
        champs.put("access_token", JETON_ACCES);
        champs.put("token_type", "Bearer");
        if (droits != null) {
            champs.put("scope", droits);
        }
        if (jetonRenouvellement != null) {
            champs.put("refresh_token", jetonRenouvellement);
        }
        String corps = champs.entrySet().stream()
                .map(champ -> "\"" + champ.getKey() + "\":\"" + champ.getValue() + "\"")
                .collect(Collectors.joining(","));
        repondre(200, "{" + corps + ",\"expires_in\":" + expireDans + "}");
    }

    /** La prochaine réponse n'arrive qu'après ce délai. */
    public void retarder(long millis) {
        attenteMillis = millis;
    }

    public void repondre(int statut, String corps) {
        this.statut = statut;
        this.reponse = corps;
        this.typeReponse = "application/json";
    }

    /** Comme un mandataire devant Google. */
    public void repondreHtml(int statut, String corps) {
        repondre(statut, corps);
        this.typeReponse = "text/html";
    }

    private void traiter(HttpExchange echange) throws IOException {
        try (echange) {
            String corps = new String(echange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requetes.add(new Requete(echange.getRequestURI().getPath(), formulaire(corps)));
            attendre();
            byte[] octets = reponse.getBytes(StandardCharsets.UTF_8);
            echange.getResponseHeaders().add("Content-Type", typeReponse);
            echange.sendResponseHeaders(statut, octets.length);
            echange.getResponseBody().write(octets);
        }
    }

    private void attendre() {
        try {
            Thread.sleep(attenteMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static Map<String, String> formulaire(String corps) {
        return Arrays.stream(corps.split("&"))
                .filter(paire -> paire.contains("="))
                .map(paire -> paire.split("=", 2))
                .collect(Collectors.toMap(paire -> paire[0], paire -> URLDecoder.decode(paire[1], StandardCharsets.UTF_8)));
    }

    @Override
    public void close() {
        serveur.stop(0);
        fils.shutdownNow();
    }
}
