package ca.gestionchalet.security;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.encrypt.AesBytesEncryptor;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.security.crypto.keygen.KeyGenerators;
import org.springframework.stereotype.Component;

/**
 * Chiffre les jetons Google au repos, en AES-256-GCM, par {@link AesBytesEncryptor} de Spring Security Crypto. La clé vient de l'environnement,
 * comme le condensé du mot de passe ; elle est directe, sans dérivation : {@code Encryptors.delux} demanderait un second secret, le sel, pour une clé déjà aléatoire.
 */
@Component
public final class GoogleTokenEncryptor implements TextEncryptor {

    private static final int KEY_BYTES = 32;
    /** Comme {@code Encryptors.stronger}. */
    private static final int IV_BYTES = 16;

    private final AesBytesEncryptor encryptor;

    public GoogleTokenEncryptor(@Value("${recus.google.cle-chiffrement:}") String key) {
        encryptor = new AesBytesEncryptor(new SecretKeySpec(decode(key), "AES"), KeyGenerators.secureRandom(IV_BYTES), AesBytesEncryptor.CipherAlgorithm.GCM);
    }

    private static byte[] decode(String key) {
        try {
            byte[] bytes = Base64.getDecoder().decode(key);
            if (bytes.length == KEY_BYTES) {
                return bytes;
            }
        } catch (IllegalArgumentException e) {
            // Le message ne cite jamais la valeur reçue : même refus qu'une clé de mauvaise taille, ci-dessous.
        }
        throw new IllegalStateException("Clé de chiffrement absente ou invalide : " + KEY_BYTES + " octets en base 64 requis (recus.google.cle-chiffrement).");
    }

    @Override
    public String encrypt(String token) {
        return Base64.getEncoder().encodeToString(encryptor.encrypt(token.getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public String decrypt(String encrypted) {
        return new String(encryptor.decrypt(Base64.getDecoder().decode(encrypted)), StandardCharsets.UTF_8);
    }
}
