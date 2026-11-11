package ca.gestionchalet.repository;

import ca.gestionchalet.domain.OngletGenere;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface OngletGenereRepository extends Repository<OngletGenere, Integer> {

    OngletGenere save(OngletGenere onglet);

    Optional<OngletGenere> findById(int annee);
}
