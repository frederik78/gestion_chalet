package ca.gestionchalet.support;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.json.JsonMapper;

/** Envoie des octets bruts sur une socket : seul moyen de produire une requête qu'un client HTTP correct refuserait d'émettre. */
public final class ClientBrut {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final int DELAI_MS = 10_000;
    private static final Pattern CONTENT_LENGTH = Pattern.compile("content-length: (\\d+)");

    private ClientBrut() {
    }

    public record Reponse(int statut, String corps, Map<String, List<String>> enTetes) {

        public Map<String, Object> json() {
            @SuppressWarnings("unchecked")
            Map<String, Object> valeurs = JSON.readValue(corps, Map.class);
            return valeurs;
        }
    }

    public static Reponse envoyer(int port, String requete) {
        try (Socket socket = new Socket("localhost", port)) {
            socket.setSoTimeout(DELAI_MS);
            OutputStream sortie = socket.getOutputStream();
            sortie.write(requete.getBytes(StandardCharsets.ISO_8859_1));
            sortie.flush();
            return analyser(new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Envoie le début d'une requête dont le corps annoncé n'arrivera jamais, et lit la réponse sans attendre la fermeture. Si le
     * serveur tentait de lire ce corps, il ne répondrait pas : le délai de lecture fait alors échouer l'appel.
     */
    public static Reponse envoyerSansLaFinDuCorps(int port, String debut, int delaiMs) {
        try (Socket socket = new Socket("localhost", port)) {
            socket.setSoTimeout(delaiMs);
            OutputStream sortie = socket.getOutputStream();
            sortie.write(debut.getBytes(StandardCharsets.ISO_8859_1));
            sortie.flush();
            StringBuilder recu = new StringBuilder();
            byte[] tampon = new byte[1024];
            while (!estComplete(recu.toString())) {
                int lus = socket.getInputStream().read(tampon);
                if (lus < 0) {
                    break;
                }
                recu.append(new String(tampon, 0, lus, StandardCharsets.UTF_8));
            }
            return analyser(recu.toString());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Comme {@link #envoyerSansLaFinDuCorps}, pour une réponse fragmentée : Tomcat n'en écrit la fin qu'après avoir purgé le corps. Lit jusqu'au texte attendu. */
    public static String lireJusqua(int port, String debut, String attendu, int delaiMs) {
        try (Socket socket = new Socket("localhost", port)) {
            socket.setSoTimeout(delaiMs);
            socket.getOutputStream().write(debut.getBytes(StandardCharsets.ISO_8859_1));
            StringBuilder recu = new StringBuilder();
            byte[] tampon = new byte[1024];
            while (!recu.toString().contains(attendu)) {
                int lus = socket.getInputStream().read(tampon);
                if (lus < 0) {
                    break;
                }
                recu.append(new String(tampon, 0, lus, StandardCharsets.UTF_8));
            }
            return recu.toString();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean estComplete(String recu) {
        int finEnTetes = recu.indexOf("\r\n\r\n");
        if (finEnTetes < 0) {
            return false;
        }
        String enTetes = recu.substring(0, finEnTetes).toLowerCase(Locale.ROOT);
        String corps = recu.substring(finEnTetes + 4);
        if (enTetes.contains("transfer-encoding: chunked")) {
            return corps.endsWith("0\r\n\r\n");
        }
        Matcher longueur = CONTENT_LENGTH.matcher(enTetes);
        return longueur.find() && corps.getBytes(StandardCharsets.UTF_8).length >= Integer.parseInt(longueur.group(1));
    }

    private static Reponse analyser(String brut) {
        int finEnTetes = brut.indexOf("\r\n\r\n");
        if (finEnTetes < 0) {
            throw new IllegalStateException("Réponse sans en-têtes : " + brut);
        }
        String[] lignes = brut.substring(0, finEnTetes).split("\r\n");
        Map<String, List<String>> enTetes = new LinkedHashMap<>();
        for (int i = 1; i < lignes.length; i++) {
            String[] nomValeur = lignes[i].split(":\\s*", 2);
            enTetes.computeIfAbsent(nomValeur[0].toLowerCase(Locale.ROOT), nom -> new ArrayList<>()).add(nomValeur.length > 1 ? nomValeur[1] : "");
        }
        String corps = brut.substring(finEnTetes + 4);
        if (enTetes.getOrDefault("transfer-encoding", List.of()).contains("chunked")) {
            corps = desassembler(corps);
        }
        return new Reponse(Integer.parseInt(lignes[0].split(" ")[1]), corps, enTetes);
    }

    private static String desassembler(String corps) {
        StringBuilder resultat = new StringBuilder();
        int position = 0;
        while (position < corps.length()) {
            int finLigne = corps.indexOf("\r\n", position);
            int taille = Integer.parseInt(corps.substring(position, finLigne).trim(), 16);
            if (taille == 0) {
                break;
            }
            byte[] restant = corps.substring(finLigne + 2).getBytes(StandardCharsets.UTF_8);
            String morceau = new String(restant, 0, taille, StandardCharsets.UTF_8);
            resultat.append(morceau);
            position = finLigne + 2 + morceau.length() + 2;
        }
        return resultat.toString();
    }
}
