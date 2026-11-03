package ca.gestionchalet.service.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import ca.gestionchalet.domain.RecuRules;
import ca.gestionchalet.service.dto.ConfigurationDTO;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigurationMapperTest {

    @Test
    void lesTypesEtLesFormatsArriventChacunDansLeurChamp() {
        RecuRules regles = new RecuRules(List.of("Type"), 5, 15L, List.of("image/png"));

        ConfigurationDTO dto = new ConfigurationMapperImpl().toDto(regles);

        assertThat(dto).isEqualTo(new ConfigurationDTO(List.of("Type"), 5, 15L, List.of("image/png")));
    }
}
