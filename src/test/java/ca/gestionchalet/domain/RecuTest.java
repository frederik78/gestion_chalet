package ca.gestionchalet.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecuTest {

    private static Recu recu(String empreinte) {
        Valeurs valeurs = new Valeurs(LocalDate.of(2026, 10, 11), "F", null, "Fournitures", null, null, null, 1150L, null, null);
        return Recu.valider(UUID.randomUUID(), valeurs, empreinte, null, Instant.parse("2026-10-11T12:00:00Z"),
                List.of(Photo.of(1, PhotoFormat.PNG, 10L)), List.of());
    }

    @Test
    void cs17_laFabriqueDonneToujoursUnRecuACopier() {
        assertThat(recu("e").getEtatCopie()).isEqualTo(EtatCopie.A_COPIER);
    }

    @Test
    void ca18_unRecuReconnaitSeulementSonPropreContenu() {
        Recu recu = recu("empreinte-a");

        assertThat(recu.hasContent("empreinte-a")).isTrue();
        assertThat(recu.hasContent("empreinte-b")).isFalse();
    }

    @Test
    void ca66_seulUnRecuACopierSeBloqueEtSeulUnRecuBloqueSeReprend() {
        Recu recu = recu("e");
        assertThatThrownBy(recu::reprendre).isInstanceOf(IllegalStateException.class);

        recu.bloquer(MotifBlocage.AUTORISATION_GOOGLE);
        assertThat(recu.getEtatCopie()).isEqualTo(EtatCopie.BLOQUEE);
        assertThat(recu.getMotifBlocage()).isEqualTo(MotifBlocage.AUTORISATION_GOOGLE);
        assertThatThrownBy(() -> recu.bloquer(MotifBlocage.AUTORISATION_GOOGLE)).isInstanceOf(IllegalStateException.class);

        recu.reprendre();
        assertThat(recu.getEtatCopie()).isEqualTo(EtatCopie.A_COPIER);
        assertThat(recu.getMotifBlocage()).isNull();
    }
}
