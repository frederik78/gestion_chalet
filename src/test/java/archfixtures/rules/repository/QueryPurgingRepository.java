package archfixtures.rules.repository;

import archfixtures.rules.domain.CompliantEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** Violation volontaire : une suppression par requête, sous un nom qui ne le dit pas. */
public interface QueryPurgingRepository extends Repository<CompliantEntity, UUID> {

    @Modifying
    @Query("delete from CompliantEntity")
    void purgeOld();
}
