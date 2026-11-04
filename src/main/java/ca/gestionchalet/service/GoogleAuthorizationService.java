package ca.gestionchalet.service;

import ca.gestionchalet.domain.AutorisationGoogle;
import ca.gestionchalet.domain.EtatAutorisationGoogle;
import ca.gestionchalet.repository.AutorisationGoogleRepository;
import ca.gestionchalet.service.dto.GoogleAuthorizationStateDTO;
import ca.gestionchalet.service.mapper.GoogleAuthorizationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** L'autorisation Google de l'application : son état, et l'effet de ses changements sur les reçus en attente. */
@Service
public class GoogleAuthorizationService {

    private final AutorisationGoogleRepository autorisationRepository;
    private final GoogleAuthorizationMapper mapper;

    public GoogleAuthorizationService(AutorisationGoogleRepository autorisationRepository, GoogleAuthorizationMapper mapper) {
        this.autorisationRepository = autorisationRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public GoogleAuthorizationStateDTO state() {
        return mapper.toDto(currentState());
    }

    @Transactional(readOnly = true)
    public boolean isValid() {
        return currentState() == EtatAutorisationGoogle.VALIDE;
    }

    private EtatAutorisationGoogle currentState() {
        return autorisationRepository.findById(AutorisationGoogle.UNIQUE_ID)
                .map(AutorisationGoogle::getEtat)
                .orElse(EtatAutorisationGoogle.ABSENTE);
    }
}
