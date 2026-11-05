package ca.gestionchalet.service;

import ca.gestionchalet.domain.Valeurs;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/**
 * Empreinte SHA-256 du contenu d'une validation : valeurs normalisées, puis octets de chaque photo, dans leur ordre. Elle dit si un
 * renvoi porte le même contenu sans relire les fichiers conservés (R-11). Chaque élément est préfixé de sa longueur, et l'absence
 * se distingue du vide : deux contenus différents ne peuvent pas produire la même suite d'octets.
 */
final class ContentFingerprint {

    private static final String ALGORITHM = "SHA-256";

    private ContentFingerprint() {
    }

    static String of(Valeurs valeurs, List<MultipartFile> files) {
        MessageDigest digest = newDigest();
        for (Object field : valeurs.contentFields()) {
            addField(digest, field);
        }
        for (MultipartFile file : files) {
            digest.update(digestOf(file));
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void addField(MessageDigest digest, Object field) {
        if (field == null) {
            digest.update((byte) 0);
            return;
        }
        byte[] text = field.toString().getBytes(StandardCharsets.UTF_8);
        digest.update((byte) 1);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(text.length).array());
        digest.update(text);
    }

    private static byte[] digestOf(MultipartFile file) {
        MessageDigest digest = newDigest();
        try (InputStream content = new DigestInputStream(file.getInputStream(), digest)) {
            content.transferTo(OutputStream.nullOutputStream());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return digest.digest();
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
