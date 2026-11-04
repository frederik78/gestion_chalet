package ca.gestionchalet.support;

import ca.gestionchalet.support.TestClient.Reponse;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.CRC32;
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
        return image("png", cote);
    }

    public static byte[] jpeg(int cote) {
        return image("jpeg", cote);
    }

    public static byte[] gif(int cote) {
        return image("gif", cote);
    }

    private static byte[] image(String format, int cote) {
        try (ByteArrayOutputStream octets = new ByteArrayOutputStream()) {
            ImageIO.write(new BufferedImage(cote, cote, BufferedImage.TYPE_INT_RGB), format, octets);
            return octets.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** La signature et un en-tête PNG annonçant ces dimensions, sans aucun pixel : une image légère, aussi grande qu'on veut. */
    public static byte[] entetePng(int largeur, int hauteur) {
        ByteBuffer bloc = ByteBuffer.allocate(4 + 13);
        bloc.put("IHDR".getBytes(StandardCharsets.US_ASCII)).putInt(largeur).putInt(hauteur);
        bloc.put(new byte[] {8, 2, 0, 0, 0});
        CRC32 crc = new CRC32();
        crc.update(bloc.array());
        return ByteBuffer.allocate(8 + 4 + 17 + 4)
                .put(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'})
                .putInt(13).put(bloc.array()).putInt((int) crc.getValue())
                .array();
    }

    /** Un vrai JPEG dont l'en-tête est réécrit pour annoncer ces dimensions. */
    public static byte[] jpegAnnoncant(int largeur, int hauteur) {
        byte[] octets = jpeg(4);
        for (int i = 2; i < octets.length - 8; i++) {
            if (octets[i] == (byte) 0xFF && octets[i + 1] == (byte) 0xC0) {
                ByteBuffer.wrap(octets, i + 5, 4).putShort((short) hauteur).putShort((short) largeur);
                return octets;
            }
        }
        throw new IllegalStateException("En-tête de trame absent");
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

    /** Le type et le nom déclarés par le client, que le serveur ne doit pas croire. */
    public static Reponse validerDeclare(TestClient client, Map<String, Object> corps, String type, String nom, byte[]... photos) {
        return envoyer(client, JSON.writeValueAsString(corps), type, nom, photos);
    }

    private static Reponse envoyer(TestClient client, String corpsJson, String type, byte[]... photos) {
        return envoyer(client, corpsJson, type, "recu.png", photos);
    }

    private static Reponse envoyer(TestClient client, String corpsJson, String type, String nom, byte[]... photos) {
        List<byte[]> morceaux = new ArrayList<>();
        morceaux.add(("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"recu\"\r\nContent-Type: application/json\r\n\r\n"
                + corpsJson + "\r\n").getBytes(StandardCharsets.UTF_8));
        for (byte[] photo : photos) {
            morceaux.add(("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"photos\"; filename=\"" + nom + "\"\r\nContent-Type: " + type + "\r\n\r\n")
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
