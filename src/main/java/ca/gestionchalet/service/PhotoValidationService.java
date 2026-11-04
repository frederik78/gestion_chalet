package ca.gestionchalet.service;

import ca.gestionchalet.domain.ImageTooLargeException;
import ca.gestionchalet.domain.MissingPhotoException;
import ca.gestionchalet.domain.PhotoFormat;
import ca.gestionchalet.domain.RecuRules;
import ca.gestionchalet.domain.UnreadableImageException;
import ca.gestionchalet.domain.UnsupportedPhotoFormatException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Contrôle les photos reçues d'après leurs octets, jamais d'après le type ou le nom déclarés (PRD, section 11.4). Toute route qui
 * reçoit des photos l'appelle avant d'en faire quoi que ce soit.
 */
@Service
public class PhotoValidationService {

    private static final long PIXELS_PER_MEGAPIXEL = 1_000_000L;

    private final RecuRules rules;

    public PhotoValidationService(RecuRules rules) {
        this.rules = rules;
    }

    /** Rend le format réel de chaque photo, dans leur ordre ; lève l'exception du premier refus. */
    public List<PhotoFormat> check(List<MultipartFile> files) {
        if (files == null || files.isEmpty() || files.stream().anyMatch(MultipartFile::isEmpty)) {
            throw new MissingPhotoException();
        }
        List<PhotoFormat> formats = new ArrayList<>();
        for (MultipartFile file : files) {
            formats.add(check(file));
        }
        return formats;
    }

    /** Un seul flux par photo : la signature en est lue, puis rejouée pour le lecteur d'en-tête. Une panne à l'ouverture reste une panne, et non un refus de la photo. */
    private PhotoFormat check(MultipartFile file) {
        try (InputStream content = file.getInputStream()) {
            byte[] start = content.readNBytes(PhotoFormat.SIGNATURE_MAX_LENGTH);
            PhotoFormat format = acceptedFormat(start);
            checkPixels(format, new SequenceInputStream(new ByteArrayInputStream(start), content));
            return format;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private PhotoFormat acceptedFormat(byte[] start) {
        return PhotoFormat.ofSignature(start)
                .filter(format -> rules.formats().contains(format.mediaType()))
                .orElseThrow(UnsupportedPhotoFormatException::new);
    }

    private void checkPixels(PhotoFormat format, InputStream content) {
        if (pixelsAnnounced(format, content) > rules.dimensionsMaxMegapixels() * PIXELS_PER_MEGAPIXEL) {
            throw new ImageTooLargeException();
        }
    }

    /**
     * Lit les dimensions dans l'en-tête seul, sans décoder aucun pixel ni allouer aucune image. Les erreurs du décodeur sont celles d'un
     * fichier hostile : toutes valent un fichier illisible, y compris une exception non vérifiée, et rien de leur message n'est conservé.
     */
    private static long pixelsAnnounced(PhotoFormat format, InputStream content) {
        ImageReader reader = ImageIO.getImageReadersByMIMEType(format.mediaType()).next();
        try (ImageInputStream header = new MemoryCacheImageInputStream(content)) {
            reader.setInput(header, true, true);
            return (long) reader.getWidth(0) * reader.getHeight(0);
        } catch (IOException | RuntimeException e) {
            throw new UnreadableImageException();
        } finally {
            reader.dispose();
        }
    }
}
