package archfixtures.rules.repository;

import archfixtures.rules.domain.CompliantEntity;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Violation volontaire : une suppression dérivée du nom de la méthode. */
public interface DerivedDeleteRepository extends Repository<CompliantEntity, UUID> {

    void removeById(UUID id);
}
