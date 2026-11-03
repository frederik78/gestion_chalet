package archfixtures.rules.repository;

import archfixtures.rules.domain.CompliantEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Violation volontaire : hériter de {@code JpaRepository} offre la suppression. */
public interface CrudOfferingRepository extends JpaRepository<CompliantEntity, UUID> {
}
