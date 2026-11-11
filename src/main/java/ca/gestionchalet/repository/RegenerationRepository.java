package ca.gestionchalet.repository;

import ca.gestionchalet.domain.Regeneration;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface RegenerationRepository extends Repository<Regeneration, Integer> {

    Regeneration save(Regeneration regeneration);

    Optional<Regeneration> findById(int id);
}
