package ca.gestionchalet.service;

import ca.gestionchalet.domain.MissingPhotoException;
import ca.gestionchalet.domain.PhotoFormat;
import ca.gestionchalet.domain.RecuRules;
import ca.gestionchalet.domain.UnsupportedPhotoFormatException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Contrôle les photos reçues d'après leurs octets, jamais d'après le type ou le nom déclarés (PRD, section 11.4). Toute route qui
 * reçoit des photos l'appelle avant d'en faire quoi que ce soit.
 */
@Service
public class PhotoValidationService {

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

    private PhotoFormat check(MultipartFile file) {
        try (InputStream content = file.getInputStream()) {
            return acceptedFormat(content.readNBytes(PhotoFormat.SIGNATURE_MAX_LENGTH));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private PhotoFormat acceptedFormat(byte[] start) {
        return PhotoFormat.ofSignature(start)
                .filter(format -> rules.formats().contains(format.mediaType()))
                .orElseThrow(UnsupportedPhotoFormatException::new);
    }
}
