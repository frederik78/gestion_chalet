package ca.gestionchalet.web.rest.errors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.gestionchalet.web.rest.errors.ErreurDTO.ChampInvalideDTO;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ErreurDTOTest {

    @Test
    void cs08_laListeDesChampsEstUneCopieImmuable() {
        List<ChampInvalideDTO> source = new ArrayList<>();
        source.add(new ChampInvalideDTO("date", "OBLIGATOIRE"));

        ErreurDTO erreur = ErreurDTO.validation(source);
        source.clear();

        assertThat(erreur.champs()).containsExactly(new ChampInvalideDTO("date", "OBLIGATOIRE"));
        assertThatThrownBy(() -> erreur.champs().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
