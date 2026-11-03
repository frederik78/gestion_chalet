package archfixtures.rules.repository;

import archfixtures.rules.domain.CompliantEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.Repository;

/** Violation volontaire : {@code JpaSpecificationExecutor} offre {@code delete(Specification)}. */
public interface SpecificationRepository extends Repository<CompliantEntity, UUID>, JpaSpecificationExecutor<CompliantEntity> {
}
