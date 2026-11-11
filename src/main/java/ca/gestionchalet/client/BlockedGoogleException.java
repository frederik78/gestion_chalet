package ca.gestionchalet.client;

import ca.gestionchalet.domain.MotifBlocage;

/** Google refuse, ou n'est pas autorisé : la copie attend une action de l'utilisateur (section 4.2 du PRD), pour le motif donné. */
public class BlockedGoogleException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final MotifBlocage motif;

    public BlockedGoogleException(MotifBlocage motif) {
        super(motif.name());
        this.motif = motif;
    }

    public MotifBlocage motif() {
        return motif;
    }
}
