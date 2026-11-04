package ca.gestionchalet.service;

import ca.gestionchalet.domain.AutorisationGoogle;
import ca.gestionchalet.domain.EtatAutorisationGoogle;
import ca.gestionchalet.domain.EtatCopie;
import ca.gestionchalet.domain.MotifBlocage;
import ca.gestionchalet.domain.Recu;
import ca.gestionchalet.repository.AutorisationGoogleRepository;
import ca.gestionchalet.repository.RecuRepository;
import ca.gestionchalet.service.dto.GoogleAuthorizationStateDTO;
import ca.gestionchalet.service.mapper.GoogleAuthorizationMapper;
import ca.gestionchalet.security.GoogleTokenEncryptor;
import ca.gestionchalet.security.UnreadableTokenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * L'autorisation Google de l'application : son état, ses jetons et l'effet de ses changements sur les reçus en attente.
 * Spring Security l'appelle à chaque jeton obtenu, et au retrait sur {@code invalid_grant} : ce sont les transitions de la section 7.6.
 * Le nom du compte est ignoré : l'autorisation est unique pour l'application (PRD, section 6).
 */
@Service
public class GoogleAuthorizationService implements OAuth2AuthorizedClientService {

    private static final Logger LOG = LoggerFactory.getLogger(GoogleAuthorizationService.class);
    private static final String REGISTRATION_ID = "google";

    private final AutorisationGoogleRepository autorisationRepository;
    private final RecuRepository recuRepository;
    private final ClientRegistrationRepository clientRegistrations;
    private final GoogleAuthorizationMapper mapper;
    private final TextEncryptor encryptor;

    public GoogleAuthorizationService(AutorisationGoogleRepository autorisationRepository, RecuRepository recuRepository,
            ClientRegistrationRepository clientRegistrations, GoogleAuthorizationMapper mapper, GoogleTokenEncryptor encryptor) {
        this.autorisationRepository = autorisationRepository;
        this.recuRepository = recuRepository;
        this.clientRegistrations = clientRegistrations;
        this.mapper = mapper;
        this.encryptor = encryptor;
    }

    @Transactional(readOnly = true)
    public GoogleAuthorizationStateDTO state() {
        return mapper.toDto(currentState());
    }

    @Transactional(readOnly = true)
    public boolean isValid() {
        return currentState() == EtatAutorisationGoogle.VALIDE;
    }

    /** Un jeton illisible, après un changement de clé, se traite comme un {@code invalid_grant} : il n'est pas réutilisé. */
    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(String clientRegistrationId, String principalName) {
        ClientRegistration registration = clientRegistrations.findByRegistrationId(clientRegistrationId);
        AutorisationGoogle autorisation = current();
        if (registration == null || autorisation.getEtat() != EtatAutorisationGoogle.VALIDE) {
            return null;
        }
        try {
            OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, encryptor.decrypt(autorisation.getJetonAccesChiffre()),
                    null, autorisation.getJetonAccesExpireLe(), registration.getScopes());
            OAuth2RefreshToken refreshToken = new OAuth2RefreshToken(encryptor.decrypt(autorisation.getJetonRenouvellementChiffre()), null);
            return (T) new OAuth2AuthorizedClient(registration, principalName, accessToken, refreshToken);
        } catch (UnreadableTokenException e) {
            requireRenewal(autorisation);
            return null;
        }
    }

    @Override
    @Transactional
    public void saveAuthorizedClient(OAuth2AuthorizedClient client, Authentication principal) {
        AutorisationGoogle autorisation = current();
        OAuth2AccessToken accessToken = client.getAccessToken();
        OAuth2RefreshToken refreshToken = client.getRefreshToken();
        if (refreshToken != null && accessToken.getScopes().containsAll(client.getClientRegistration().getScopes())) {
            autorisation.accorder(encryptor.encrypt(accessToken.getTokenValue()), accessToken.getExpiresAt(), encryptor.encrypt(refreshToken.getTokenValue()));
            recuRepository.findByEtatCopieAndMotifBlocage(EtatCopie.BLOQUEE, MotifBlocage.AUTORISATION_GOOGLE).forEach(Recu::reprendre);
            autorisationRepository.save(autorisation);
            LOG.info("Autorisation Google : VALIDE");
        } else {
            requireRenewal(autorisation);
        }
    }

    /** Appelé par Spring Security quand Google répond {@code invalid_grant} au renouvellement. */
    @Override
    @Transactional
    public void removeAuthorizedClient(String clientRegistrationId, String principalName) {
        requireRenewal(current());
    }

    private void requireRenewal(AutorisationGoogle autorisation) {
        autorisation.demanderRenouvellement();
        recuRepository.findByEtatCopie(EtatCopie.A_COPIER).forEach(recu -> recu.bloquer(MotifBlocage.AUTORISATION_GOOGLE));
        autorisationRepository.save(autorisation);
        LOG.warn("Autorisation Google : A_RENOUVELER");
    }

    /** Sans identifiants configurés, une autorisation conservée ne peut pas servir : l'état est {@code ABSENTE} (D-42), et elle resservira à leur retour. */
    private EtatAutorisationGoogle currentState() {
        return clientRegistrations.findByRegistrationId(REGISTRATION_ID) == null ? EtatAutorisationGoogle.ABSENTE : current().getEtat();
    }

    private AutorisationGoogle current() {
        return autorisationRepository.findById(AutorisationGoogle.UNIQUE_ID).orElseGet(AutorisationGoogle::absente);
    }
}
