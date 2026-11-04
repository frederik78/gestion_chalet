package ca.gestionchalet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.gestionchalet.domain.IdempotencyKeyConflictException;
import ca.gestionchalet.domain.Photo;
import ca.gestionchalet.domain.Recu;
import ca.gestionchalet.domain.RecuRules;
import ca.gestionchalet.domain.Valeurs;
import ca.gestionchalet.repository.PhotoStorage;
import ca.gestionchalet.repository.RecuRepository;
import ca.gestionchalet.service.dto.RecuValidationDTO;
import ca.gestionchalet.service.dto.RecuValidationResult;
import ca.gestionchalet.service.dto.ValeursDTO;
import ca.gestionchalet.service.mapper.RecuMapper;
import ca.gestionchalet.service.mapper.RecuMapperImpl;
import ca.gestionchalet.support.RecuRequests;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

class RecuServiceTest {

    private static final UUID CLE = UUID.fromString("00000000-0000-4000-8000-000000000001");

    private final RecuRepository depot = mock(RecuRepository.class);
    private final PhotoStorage stockage = mock(PhotoStorage.class);
    private final RecuMapper mapper = new RecuMapperImpl();
    private final Clock horloge = Clock.fixed(Instant.parse("2026-10-11T12:00:00Z"), ZoneOffset.UTC);
    private final RecuService service = new RecuService(depot, stockage, mapper,
            new PhotoValidationService(new RecuRules(List.of("Fournitures"), 5, 1_000_000L, List.of("image/jpeg", "image/png"), 64L)),
            new TransactionTemplate(mock(PlatformTransactionManager.class)), horloge);
    private final ValeursDTO valeurs = new ValeursDTO(LocalDate.of(2026, 10, 11), "F", null, "Fournitures", null, null, null, 1150L, null, null);

    /** Le service compte sur une transaction pour retirer les fichiers : sa synchronisation est ouverte à la main, sans Spring. */
    @BeforeEach
    void ouvrirLaSynchronisation() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void fermerLaSynchronisation() {
        TransactionSynchronizationManager.clear();
    }

    private static MultipartFile photo(int cote) {
        return new MockMultipartFile("photos", "x.png", "image/png", RecuRequests.png(cote));
    }

    private RecuValidationDTO validation() {
        return new RecuValidationDTO(CLE, valeurs, null);
    }

    private void cleConnuePour(List<MultipartFile> photos) {
        Valeurs enregistrees = mapper.toValeurs(valeurs);
        Recu connu = Recu.valider(CLE, enregistrees, ContentFingerprint.of(enregistrees, photos), null, horloge.instant(),
                List.of());
        when(depot.findByCleIdempotence(CLE)).thenReturn(Optional.of(connu));
    }

    private static void terminerLaTransaction(int statut) {
        TransactionSynchronizationUtils.invokeAfterCompletion(TransactionSynchronizationManager.getSynchronizations(), statut);
    }

    @Test
    void ca18_uneCleConnueAuMemeContenuRendLeRecuSansRienEcrire() {
        List<MultipartFile> photos = List.of(photo(1));
        cleConnuePour(photos);

        RecuValidationResult resultat = service.validate(validation(), photos);

        assertThat(resultat.created()).isFalse();
        verify(stockage, never()).write(any(), any());
        verify(depot, never()).saveAndFlush(any());
    }

    @Test
    void ca18_uneCleConnueAuContenuDifferentEstRefuseeSansRienEcrire() {
        cleConnuePour(List.of(photo(1)));

        assertThatThrownBy(() -> service.validate(validation(), List.of(photo(2)))).isInstanceOf(IdempotencyKeyConflictException.class);

        verify(stockage, never()).write(any(), any());
        verify(depot, never()).saveAndFlush(any());
    }

    @Test
    void ca19_unEchecDuStockageRetireLesFichiersAuRetourDeLaTransaction() {
        when(depot.findByCleIdempotence(CLE)).thenReturn(Optional.empty());
        UncheckedIOException panne = new UncheckedIOException(new IOException("panne"));
        doNothing().doThrow(panne).when(stockage).write(any(), any());

        assertThatThrownBy(() -> service.validate(validation(), List.of(photo(1), photo(2)))).isSameAs(panne);
        verify(stockage, never()).discard(any());

        terminerLaTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(stockage, times(2)).discard(any(Photo.class));
    }

    @Test
    void ca19_unStatutDeTransactionInconnuNeRetireAucunFichier() {
        when(depot.findByCleIdempotence(CLE)).thenReturn(Optional.empty());
        when(depot.saveAndFlush(any())).then(returnsFirstArg());

        service.validate(validation(), List.of(photo(1)));
        terminerLaTransaction(TransactionSynchronization.STATUS_UNKNOWN);

        verify(stockage, never()).discard(any());
    }

    @Test
    void ca19_uneTransactionValideeGardeSesFichiers() {
        when(depot.findByCleIdempotence(CLE)).thenReturn(Optional.empty());
        when(depot.saveAndFlush(any())).then(returnsFirstArg());

        RecuValidationResult resultat = service.validate(validation(), List.of(photo(1)));
        terminerLaTransaction(TransactionSynchronization.STATUS_COMMITTED);

        assertThat(resultat.created()).isTrue();
        verify(stockage, never()).discard(any());
    }
}
