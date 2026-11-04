package ca.gestionchalet.service;

import ca.gestionchalet.domain.PhotoFormat;
import ca.gestionchalet.domain.ReadingRules;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriter;
import javax.imageio.event.IIOReadUpdateListener;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Produit la copie d'une photo envoyée au modèle (PRD, section 7.3) : un JPEG recodé, donc sans aucune métadonnée, orientation appliquée, réduit. Un retrait sans décodage ne
 * suffirait pas : une photo de 15 Mo dépasse la limite du service, et l'orientation doit être appliquée. La photo n'est jamais décodée en entier en mémoire : le sous-échantillonnage
 * du lecteur ne garde que deux fois le côté voulu au plus, et le nombre de décodages simultanés est borné. La photo d'origine n'est pas modifiée (R-36).
 */
@Service
public class PhotoCopyService {

    private static final Logger LOG = LoggerFactory.getLogger(PhotoCopyService.class);

    private final ReadingRules rules;
    private final Semaphore decodings;

    public PhotoCopyService(ReadingRules rules) {
        this.rules = rules;
        this.decodings = new Semaphore(rules.decodagesSimultanes(), true);
    }

    /**
     * Vide si la photo, admise sur son seul en-tête (D-41), ne se décode pas : erreur du décodeur sur un fichier hostile ou tronqué, bornes de décodage franchies, ou place attendue
     * trop longtemps. Le sémaphore reste écrit à la main : {@code @ConcurrencyLimit} de Spring 7 n'offre que {@code BLOCK}, sans délai, ce qui bloquait toutes les lectures, ou
     * {@code REJECT}, immédiat, qui ferait échouer une lecture dès qu'une autre décode.
     */
    public Optional<byte[]> copy(MultipartFile file, PhotoFormat format) {
        try {
            if (!decodings.tryAcquire(rules.attenteDecodage().toMillis(), TimeUnit.MILLISECONDS)) {
                LOG.warn("Copie d'une photo impossible : aucune place de décodage");
                return Optional.empty();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
        try {
            int orientation = format == PhotoFormat.JPEG ? readOrientation(file) : ExifOrientation.NONE;
            return decodeAndEncode(file, format, orientation);
        } catch (IOException | RuntimeException e) {
            LOG.warn("Copie d'une photo impossible : {}", e.getClass().getName());
            return Optional.empty();
        } finally {
            decodings.release();
        }
    }

    private static int readOrientation(MultipartFile file) throws IOException {
        try (InputStream content = file.getInputStream()) {
            return ExifOrientation.of(content);
        }
    }

    private Optional<byte[]> decodeAndEncode(MultipartFile file, PhotoFormat format, int orientation) throws IOException {
        ImageReader reader = ImageIO.getImageReadersByMIMEType(format.mediaType()).next();
        try (InputStream content = file.getInputStream(); ImageInputStream input = new MemoryCacheImageInputStream(content)) {
            reader.setInput(input, true, true);
            int width = reader.getWidth(0);
            int height = reader.getHeight(0);
            int longSide = Math.max(width, height);
            if (longSide > rules.coteSourceMaxPixels()) {
                LOG.warn("Copie d'une photo impossible : côté au-delà de la limite");
                return Optional.empty();
            }
            ImageReadParam param = reader.getDefaultReadParam();
            int subsampling = Math.max(1, longSide / rules.coteMaxPixels());
            param.setSourceSubsampling(subsampling, subsampling, 0, 0);
            DecodingGuard guard = new DecodingGuard(reader, rules.passesDecodageMax(), System.nanoTime() + rules.delaiDecodage().toNanos());
            reader.addIIOReadUpdateListener(guard);
            BufferedImage decoded = reader.read(0, param);
            if (guard.stopped) {
                LOG.warn("Copie d'une photo impossible : bornes de décodage franchies");
                return Optional.empty();
            }
            int targetLongSide = Math.min(longSide, rules.coteMaxPixels());
            return Optional.of(encode(oriented(decoded, scaled(width, targetLongSide, longSide), scaled(height, targetLongSide, longSide), orientation)));
        } finally {
            reader.dispose();
        }
    }

    /** Arrondi au plus proche, en entiers. */
    private static int scaled(int side, int targetLongSide, int longSide) {
        return (int) Math.max(1, ((long) side * targetLongSide + longSide / 2) / longSide);
    }

    /** Sur fond blanc : le JPEG n'a pas de transparence. */
    private static BufferedImage oriented(BufferedImage source, int width, int height, int orientation) {
        boolean swapped = orientation > 4;
        BufferedImage target = new BufferedImage(swapped ? height : width, swapped ? width : height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, target.getWidth(), target.getHeight());
            graphics.transform(transform(orientation, width, height));
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    /** Les huit orientations d'Exif, de l'image enregistrée à l'image à afficher. */
    private static AffineTransform transform(int orientation, int w, int h) {
        return switch (orientation) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, w, 0);
            case 3 -> new AffineTransform(-1, 0, 0, -1, w, h);
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, h);
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
            case 6 -> new AffineTransform(0, 1, -1, 0, h, 0);
            case 7 -> new AffineTransform(0, -1, -1, 0, h, w);
            case 8 -> new AffineTransform(0, -1, 1, 0, 0, w);
            default -> new AffineTransform();
        };
    }

    /** En mémoire : le cache de {@code ImageIO.write} ferait transiter la copie du reçu, en clair, par le dossier temporaire. */
    private static byte[] encode(BufferedImage image) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            try (ImageOutputStream output = new MemoryCacheImageOutputStream(bytes)) {
                writer.setOutput(output);
                writer.write(image);
            }
            return bytes.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    /** Interrompt un décodage trop long : le JDK refait une passe complète à chaque balayage d'un JPEG progressif, même vide. */
    private static final class DecodingGuard implements IIOReadUpdateListener {

        private final ImageReader reader;
        private final int maxPasses;
        private final long deadlineNanos;
        private int passes;
        private boolean stopped;

        private DecodingGuard(ImageReader reader, int maxPasses, long deadlineNanos) {
            this.reader = reader;
            this.maxPasses = maxPasses;
            this.deadlineNanos = deadlineNanos;
        }

        private void stopIf(boolean exceeded) {
            if (exceeded && !stopped) {
                stopped = true;
                reader.abort();
            }
        }

        private boolean isLate() {
            return System.nanoTime() - deadlineNanos > 0;
        }

        @Override
        public void passStarted(ImageReader source, BufferedImage image, int pass, int minPass, int maxPass, int minX, int minY, int periodX, int periodY, int[] bands) {
            passes++;
            stopIf(passes > maxPasses || isLate());
        }

        @Override
        public void imageUpdate(ImageReader source, BufferedImage image, int minX, int minY, int width, int height, int periodX, int periodY, int[] bands) {
            stopIf(isLate());
        }

        @Override
        public void passComplete(ImageReader source, BufferedImage image) {
            // Rien à contrôler de plus qu'au début de la passe suivante.
        }

        @Override
        public void thumbnailPassStarted(ImageReader source, BufferedImage thumbnail, int pass, int minPass, int maxPass, int minX, int minY, int periodX, int periodY, int[] bands) {
            // Aucune vignette n'est lue.
        }

        @Override
        public void thumbnailUpdate(ImageReader source, BufferedImage thumbnail, int minX, int minY, int width, int height, int periodX, int periodY, int[] bands) {
            // Aucune vignette n'est lue.
        }

        @Override
        public void thumbnailPassComplete(ImageReader source, BufferedImage thumbnail) {
            // Aucune vignette n'est lue.
        }
    }
}
