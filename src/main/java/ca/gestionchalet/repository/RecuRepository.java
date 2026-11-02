package ca.gestionchalet.repository;

import ca.gestionchalet.domain.Recu;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Étend {@code Repository} et non {@code JpaRepository} : un reçu ne se supprime pas (R-35), le dépôt n'en offre donc pas le moyen. */
public interface RecuRepository extends Repository<Recu, UUID> {

    Recu saveAndFlush(Recu recu);
}
