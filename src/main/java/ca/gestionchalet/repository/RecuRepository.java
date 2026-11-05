package ca.gestionchalet.repository;

import ca.gestionchalet.domain.EtatCopie;
import ca.gestionchalet.domain.MotifBlocage;
import ca.gestionchalet.domain.Recu;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Étend {@code Repository} et non {@code JpaRepository} : un reçu ne se supprime pas (R-35), le dépôt n'en offre donc pas le moyen. */
public interface RecuRepository extends Repository<Recu, UUID> {

    Recu saveAndFlush(Recu recu);

    List<Recu> findByValeursDateAndValeursMontantTotalCents(LocalDate date, Long montantTotalCents);

    Optional<Recu> findByCleIdempotence(UUID cleIdempotence);

    List<Recu> findByEtatCopie(EtatCopie etatCopie);

    List<Recu> findByEtatCopieAndMotifBlocage(EtatCopie etatCopie, MotifBlocage motifBlocage);
}
