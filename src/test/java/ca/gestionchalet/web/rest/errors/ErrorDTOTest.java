package ca.gestionchalet.web.rest.errors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.gestionchalet.web.rest.errors.ErrorDTO.InvalidFieldDTO;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ErrorDTOTest {

    @Test
    void cs08_laListeDesChampsEstUneCopieImmuable() {
        List<InvalidFieldDTO> source = new ArrayList<>();
        source.add(new InvalidFieldDTO("date", "OBLIGATOIRE"));

        ErrorDTO erreur = ErrorDTO.validation(source);
        source.clear();

        assertThat(erreur.champs()).containsExactly(new InvalidFieldDTO("date", "OBLIGATOIRE"));
        assertThatThrownBy(() -> erreur.champs().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
