package ca.gestionchalet.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConnexionDTOTest {

    @Test
    void cs04_leMotDePasseNApparaitJamaisDansToString() {
        assertThat(new ConnexionDTO("secret-temoin")).hasToString("ConnexionDTO[motDePasse=***]");
    }
}
