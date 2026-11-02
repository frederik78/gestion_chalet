package ca.gestionchalet.support;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Routes présentes dans les seuls tests : une mutation dont l'effet se compte, et une panne provoquée. */
@RestController
@RequestMapping("/api/essai")
public class RouteEssaiResource {

    /** Message témoin : il ne doit jamais atteindre un client. */
    public static final String DETAIL_SECRET = "detail-secret-de-la-panne";

    public final AtomicInteger mutations = new AtomicInteger();

    @PostMapping("/mutation/{id}")
    void creer(@PathVariable String id) {
        mutations.incrementAndGet();
    }

    @PutMapping("/mutation/{id}")
    void modifier(@PathVariable String id) {
        mutations.incrementAndGet();
    }

    @GetMapping("/panne")
    String panne() {
        throw new IllegalStateException(DETAIL_SECRET);
    }

    @GetMapping("/session")
    String session() {
        return "valide";
    }

    /** La réponse est déjà engagée quand la panne survient : plus aucun corps d'erreur n'est possible. */
    @GetMapping("/panne-apres-flux")
    void panneApresFlux(HttpServletResponse reponse) throws IOException {
        reponse.getOutputStream().write('x');
        reponse.flushBuffer();
        throw new IllegalStateException(DETAIL_SECRET);
    }

    @GetMapping("/panne-apres-writer")
    void panneApresWriter(HttpServletResponse reponse) throws IOException {
        reponse.getWriter().write('x');
        reponse.flushBuffer();
        throw new IllegalStateException(DETAIL_SECRET);
    }

    /** Sa sérialisation échoue : la panne survient après la route, dans l'écriture de la réponse. */
    @GetMapping("/non-serialisable")
    NonSerialisable nonSerialisable() {
        return new NonSerialisable();
    }

    public static final class NonSerialisable {

        public String getValeur() {
            throw new IllegalStateException(DETAIL_SECRET);
        }
    }

    /** Une route à corps JSON validé : la borne de 64 Ko et les erreurs de lecture et de validation s'y vérifient. */
    @PostMapping("/json")
    void json(@Valid @RequestBody CorpsEssai corps) {
        mutations.incrementAndGet();
    }

    /** Une route qui lit vraiment un multipart : les erreurs d'analyse s'y vérifient. */
    @PostMapping("/photos")
    void photos(@RequestPart("photos") MultipartFile photo) {
        mutations.incrementAndGet();
    }

    @GetMapping("/inactivite")
    int inactivite(HttpSession session) {
        return session.getMaxInactiveInterval();
    }

    /** Tomcat compte l'inactivité configurée en minutes entières : pour la franchir sans attendre, le test la réduit à une seconde. */
    @GetMapping("/expirer-bientot")
    void expirerBientot(HttpSession session) {
        session.setMaxInactiveInterval(1);
    }

    public record CorpsEssai(@NotBlank @Size(max = 10) String valeur) {
    }
}
