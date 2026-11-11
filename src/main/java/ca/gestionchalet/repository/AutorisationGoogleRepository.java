package ca.gestionchalet.repository;

import ca.gestionchalet.domain.AutorisationGoogle;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface AutorisationGoogleRepository extends Repository<AutorisationGoogle, Integer> {

    AutorisationGoogle save(AutorisationGoogle autorisation);

    Optional<AutorisationGoogle> findById(int id);
}
