package ca.gestionchalet.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ValeursTest {

    /** Valeurs toutes différentes : un champ persistant absent de l'empreinte y donnerait un faux 200. */
    @Test
    void ca18_chaqueChampPersistantFaitPartieDuContenuCompare() throws ReflectiveOperationException {
        Valeurs valeurs = new Valeurs(LocalDate.of(2026, 10, 11), "Fournisseur", "Objet", "Fournitures", 1L, 2L, 3L, 4L, "TPS", "TVQ");

        RecordComponent[] champs = Valeurs.class.getRecordComponents();
        assertThat(valeurs.contentFields()).hasSameSizeAs(champs);
        for (RecordComponent champ : champs) {
            assertThat(valeurs.contentFields()).as(champ.getName()).contains(champ.getAccessor().invoke(valeurs));
        }
    }
}
