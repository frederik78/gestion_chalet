package archfixtures.rules.repository;

import archfixtures.rules.domain.CompliantEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** Violation volontaire : une requête de suppression sans {@code @Modifying}. */
public interface NativePurgingRepository extends Repository<CompliantEntity, UUID> {

    @Query(value = "DELETE FROM recu", nativeQuery = true)
    void clean();
}
