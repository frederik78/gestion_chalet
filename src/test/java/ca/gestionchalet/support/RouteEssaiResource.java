package ca.gestionchalet.support;

import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
