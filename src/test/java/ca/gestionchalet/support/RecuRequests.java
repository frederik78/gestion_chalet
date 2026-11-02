package ca.gestionchalet.support;

import ca.gestionchalet.support.TestClient.Reponse;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import tools.jackson.databind.json.JsonMapper;

/** Validations de reçus telles que la PWA les envoie : un vrai multipart, avec le jeton CSRF. Les images sont générées, jamais de photo réelle. */
public final class RecuRequests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String BOUNDARY = "limite-essai";

    private RecuRequests() {
    }

    /** Les seuls champs obligatoires ; une date passée, sur laquelle l'horloge des tests ne pèse pas. */
    public static Map<String, Object> valeurs() {
        Map<String, Object> valeurs = new LinkedHashMap<>();
        valeurs.put("date", "2026-10-11");
        valeurs.put("fournisseur", "Fournisseur témoin");
        valeurs.put("typeDepense", "Fournitures");
        valeurs.put("montantTotalCents", 3500);
        return valeurs;
    }

    public static Map<String, Object> corps(Map<String, Object> valeurs) {
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("cleIdempotence", UUID.randomUUID().toString());
        corps.put("valeurs", valeurs);
        return corps;
    }

    public static byte[] png() {
        return png(4);
    }

    public static byte[] png(int cote) {
        try (ByteArrayOutputStream octets = new ByteArrayOutputStream()) {
            ImageIO.write(new BufferedImage(cote, cote, BufferedImage.TYPE_INT_RGB), "png", octets);
            return octets.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Reponse valider(TestClient client, Map<String, Object> corps, byte[]... photos) {
        return validerJson(client, JSON.writeValueAsString(corps), photos);
    }

    public static Reponse validerAvecType(TestClient client, Map<String, Object> corps, String type, byte[]... photos) {
        return envoyer(client, JSON.writeValueAsString(corps), type, photos);
    }

    public static Reponse validerJson(TestClient client, String corpsJson, byte[]... photos) {
        return envoyer(client, corpsJson, "image/png", photos);
    }

    private static Reponse envoyer(TestClient client, String corpsJson, String type, byte[]... photos) {
        List<byte[]> morceaux = new ArrayList<>();
        morceaux.add(("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"recu\"\r\nContent-Type: application/json\r\n\r\n"
                + corpsJson + "\r\n").getBytes(StandardCharsets.UTF_8));
        for (byte[] photo : photos) {
            morceaux.add(("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"photos\"; filename=\"recu.png\"\r\nContent-Type: " + type + "\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            morceaux.add(photo);
            morceaux.add("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        morceaux.add(("--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return client.envoyer(client.requete("/api/recus")
                .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .header("X-XSRF-TOKEN", client.cookie("XSRF-TOKEN"))
                .POST(HttpRequest.BodyPublishers.ofByteArrays(morceaux))
                .build());
    }
}
