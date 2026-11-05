package ca.gestionchalet.service;

import ca.gestionchalet.domain.EtatCopie;
import ca.gestionchalet.domain.MotifBlocage;
import ca.gestionchalet.domain.Recu;
import ca.gestionchalet.repository.RecuRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Le cycle de la copie d'un reçu validé vers Drive et le Sheet (D-60) : les appelants signalent des faits (un reçu validé, une autorisation accordée ou perdue),
 * et ce module décide de l'état de la copie. Seul ce module change l'état de la copie d'un reçu.
 */
@Service
public class RecuCopyService {

    private final RecuRepository recuRepository;
    private final GoogleAuthorizationService googleAuthorizationService;

    public RecuCopyService(RecuRepository recuRepository, GoogleAuthorizationService googleAuthorizationService) {
        this.recuRepository = recuRepository;
        this.googleAuthorizationService = googleAuthorizationService;
    }

    /** Un reçu vient d'être validé, dans la transaction de la validation : sans autorisation Google, il attend (CA64). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void receiptValidated(Recu recu) {
        if (!googleAuthorizationService.isValid()) {
            recu.bloquer(MotifBlocage.AUTORISATION_GOOGLE);
        }
    }

    /** Une nouvelle autorisation reprend les reçus bloqués pour ce motif (CA65). */
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void on(GoogleAuthorizationGranted event) {
        recuRepository.findByEtatCopieAndMotifBlocage(EtatCopie.BLOQUEE, MotifBlocage.AUTORISATION_GOOGLE).forEach(Recu::reprendre);
    }

    /** Une autorisation perdue bloque les reçus en attente (CA66). */
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void on(GoogleAuthorizationLost event) {
        recuRepository.findByEtatCopie(EtatCopie.A_COPIER).forEach(recu -> recu.bloquer(MotifBlocage.AUTORISATION_GOOGLE));
    }
}
