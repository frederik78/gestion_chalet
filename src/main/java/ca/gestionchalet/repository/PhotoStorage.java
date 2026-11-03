package ca.gestionchalet.repository;

import ca.gestionchalet.domain.Photo;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
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

    /**
     * Écrit d'abord un fichier temporaire, forcé sur disque avant de prendre son nom, puis force le dossier : une écriture interrompue
     * ne laisse pas de photo tronquée, et une coupure de courant après le retour de cette méthode ne perd pas la photo, dans la limite
     * de ce que le système et le disque garantissent pour {@code fsync}.
     */
    public void write(Photo photo, InputStream content) {
        try {
            Path temporary = Files.createTempFile(root, "ecriture-", ".tmp");
            try {
                Files.copy(content, temporary, StandardCopyOption.REPLACE_EXISTING);
                try (FileChannel file = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                    file.force(true);
                }
                Files.move(temporary, pathOf(photo), StandardCopyOption.ATOMIC_MOVE);
                forceDirectory();
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Un système peut refuser d'ouvrir un dossier : la photo est écrite, l'envoi n'échoue pas pour autant. */
    private void forceDirectory() {
        try (FileChannel directory = FileChannel.open(root, StandardOpenOption.READ)) {
            directory.force(true);
        } catch (IOException e) {
            LOG.warn("Dossier des photos non forcé sur disque : {}", e.getClass().getName());
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
