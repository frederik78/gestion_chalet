package ca.gestionchalet.client;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Google Drive par son API REST (v3). Le droit demandé se limite aux fichiers créés par l'application ({@code drive.file}) : elle ne voit que son dossier et ses photos (R-28).
 * Aucune permission de partage n'est jamais demandée, et le lien d'une photo se déduit de l'identifiant que Drive renvoie. Pas de bibliothèque cliente Google :
 * {@code RestClient} suffit à trois requêtes, et une dépendance de plus est à tenir à jour (ADR 0001).
 */
public class GoogleDriveClient implements DriveClient {

    private static final String FILES = "/drive/v3/files";
    private static final String UPLOAD = "/upload/drive/v3/files";
    private static final String FOLDER_TYPE = "application/vnd.google-apps.folder";
    private static final String PHOTO_KEY = "photoId";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final RestClient restClient;
    private final GoogleCalls calls;
    private final String folderName;

    public GoogleDriveClient(RestClient restClient, Supplier<Optional<String>> accessTokens, String folderName) {
        this.restClient = restClient;
        this.calls = new GoogleCalls(accessTokens);
        this.folderName = folderName;
    }

    @Override
    public String deposit(DrivePhoto photo) {
        String authorization = calls.bearer();
        String folderId = findOrCreateFolder(authorization);
        return find(authorization, "appProperties has { key='" + PHOTO_KEY + "' and value='" + escape(photo.photoId().toString()) + "' } and '" + escape(folderId)
                + "' in parents and trashed=false").orElseGet(() -> upload(authorization, folderId, photo));
    }

    private String findOrCreateFolder(String authorization) {
        return find(authorization, "mimeType='" + FOLDER_TYPE + "' and name='" + escape(folderName) + "' and trashed=false")
                .orElseGet(() -> {
                    Map<String, Object> metadata = new LinkedHashMap<>();
                    metadata.put("name", folderName);
                    metadata.put("mimeType", FOLDER_TYPE);
                    return identifier(calls.call(() -> restClient.post().uri(uri -> uri.path(FILES).queryParam("fields", "id").build())
                            .header(HttpHeaders.AUTHORIZATION, authorization).contentType(MediaType.APPLICATION_JSON).body(metadata).retrieve().body(JsonNode.class)));
                });
    }

    private Optional<String> find(String authorization, String query) {
        JsonNode result = calls.call(() -> restClient.get()
                .uri(uri -> uri.path(FILES).queryParam("q", "{q}").queryParam("fields", "files(id)").queryParam("pageSize", 1).build(Map.of("q", query)))
                .header(HttpHeaders.AUTHORIZATION, authorization).retrieve().body(JsonNode.class));
        if (result == null || !result.path("files").isArray()) {
            throw new TransientGoogleException("REPONSE_ILLISIBLE");
        }
        return result.get("files").isEmpty() ? Optional.empty() : Optional.of(identifier(result.get("files").get(0)));
    }

    private String upload(String authorization, String folderId, DrivePhoto photo) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("name", photo.name());
        metadata.put("parents", List.of(folderId));
        metadata.put("appProperties", Map.of(PHOTO_KEY, photo.photoId().toString()));
        String boundary = "limite-" + UUID.randomUUID();
        byte[] body = related(boundary, JSON.writeValueAsString(metadata), photo);
        return identifier(calls.call(() -> restClient.post().uri(uri -> uri.path(UPLOAD).queryParam("uploadType", "multipart").queryParam("fields", "id").build())
                .header(HttpHeaders.AUTHORIZATION, authorization).contentType(MediaType.parseMediaType("multipart/related; boundary=" + boundary))
                .body(body).retrieve().body(JsonNode.class)));
    }

    /**
     * {@code multipart/related} : les métadonnées en JSON, puis les octets de la photo tels quels (R-36). Écrit à la main, après essai du convertisseur de Spring
     * ({@code FormHttpMessageConverter}, parties {@code HttpEntity}, {@code MediaType.MULTIPART_RELATED}) : il ajoute à chaque partie {@code Content-Disposition: form-data; name="…"}
     * et {@code Content-Length}, alors que le format documenté par Google pour {@code uploadType=multipart} ne porte que {@code Content-Type} par partie. L'écart n'est pas
     * contourné sans accord de l'utilisateur ; qu'il soit refusé ou accepté par Drive ne se sait que par un essai réel (revue 001-06, I-1).
     */
    private static byte[] related(String boundary, String metadata, DrivePhoto photo) {
        byte[] head = ("--" + boundary + "\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n" + metadata + "\r\n--" + boundary + "\r\nContent-Type: "
                + photo.mediaType() + "\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] tail = ("\r\n--" + boundary + "--").getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[head.length + photo.content().length + tail.length];
        System.arraycopy(head, 0, body, 0, head.length);
        System.arraycopy(photo.content(), 0, body, head.length, photo.content().length);
        System.arraycopy(tail, 0, body, head.length + photo.content().length, tail.length);
        return body;
    }

    private static String identifier(JsonNode file) {
        if (file == null || !file.path("id").isString() || file.get("id").asString().isBlank()) {
            throw new TransientGoogleException("REPONSE_ILLISIBLE");
        }
        return file.get("id").asString();
    }

    /** Une valeur placée entre apostrophes dans une requête de Drive : la barre oblique inverse et l'apostrophe y sont échappées. */
    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }
}
