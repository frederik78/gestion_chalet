package ca.gestionchalet.repository;

import ca.gestionchalet.domain.Photo;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Copie locale des photos, octet pour octet (R-36), sous un nom généré par le serveur ; le nom envoyé par le client n'y entre jamais. */
public final class PhotoStorage {

    private static final Logger LOG = LoggerFactory.getLogger(PhotoStorage.class);

    private final Path root;

    public PhotoStorage(Path root) {
        this.root = root;
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Écrit d'abord un fichier temporaire : une écriture interrompue ne laisse jamais de photo tronquée. */
    public void write(Photo photo, InputStream content) {
        try {
            Path temporary = Files.createTempFile(root, "ecriture-", ".tmp");
            try {
                Files.copy(content, temporary, StandardCopyOption.REPLACE_EXISTING);
                Files.move(temporary, pathOf(photo), StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Retire le fichier d'une validation qui n'a pas abouti, et rien d'autre : un reçu validé garde ses photos (R-35). Un retrait
     * impossible n'est que journalisé, pour ne pas masquer la panne d'origine : le fichier reste orphelin, sans reçu qui le désigne.
     */
    public void discard(Photo photo) {
        try {
            Files.deleteIfExists(pathOf(photo));
        } catch (IOException e) {
            LOG.error("Fichier de la photo {} non retiré : {}", photo.getId(), e.getClass().getName());
        }
    }

    private Path pathOf(Photo photo) {
        return root.resolve(photo.getId() + "." + photo.getFormat().extension());
    }
}
