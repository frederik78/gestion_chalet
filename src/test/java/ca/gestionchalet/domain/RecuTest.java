package ca.gestionchalet.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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

    /** Place un reçu neuf dans l'état voulu par les seules transitions autorisées. */
    private static Recu recuA(EtatCopie etat) {
        Recu recu = recu("e");
        switch (etat) {
            case A_COPIER -> { }
            case BLOQUEE -> recu.bloquer(MotifBlocage.AUTORISATION_GOOGLE);
            case COPIEE -> recu.terminerCopie(recu.getVersion());
        }
        return recu;
    }

    private record Transition(String nom, Consumer<Recu> action) {
        @Override
        public String toString() {
            return nom;
        }
    }

    private static final Transition BLOQUER = new Transition("bloquer", recu -> recu.bloquer(MotifBlocage.AUTORISATION_GOOGLE));
    private static final Transition REPRENDRE = new Transition("reprendre", Recu::reprendre);
    private static final Transition TERMINER = new Transition("terminerCopie", recu -> recu.terminerCopie(recu.getVersion()));
    private static final Transition REESSAYER = new Transition("reessayer", Recu::reessayer);
    private static final Transition CORRIGER = new Transition("corriger", Recu::corriger);

    /** Le tableau « Transitions autorisées » de la section 4.2 du PRD, écrit ligne à ligne ; tout le reste est refusé. */
    static Stream<Arguments> tableauDesTransitions() {
        return Stream.of(
                Arguments.of(EtatCopie.A_COPIER, BLOQUER, EtatCopie.BLOQUEE),
                Arguments.of(EtatCopie.A_COPIER, TERMINER, EtatCopie.COPIEE),
                Arguments.of(EtatCopie.A_COPIER, REESSAYER, EtatCopie.A_COPIER),
                Arguments.of(EtatCopie.A_COPIER, CORRIGER, EtatCopie.A_COPIER),
                Arguments.of(EtatCopie.A_COPIER, REPRENDRE, null),
                Arguments.of(EtatCopie.BLOQUEE, REPRENDRE, EtatCopie.A_COPIER),
                Arguments.of(EtatCopie.BLOQUEE, CORRIGER, EtatCopie.A_COPIER),
                Arguments.of(EtatCopie.BLOQUEE, BLOQUER, null),
                Arguments.of(EtatCopie.BLOQUEE, TERMINER, null),
                Arguments.of(EtatCopie.BLOQUEE, REESSAYER, null),
                Arguments.of(EtatCopie.COPIEE, CORRIGER, EtatCopie.A_COPIER),
                Arguments.of(EtatCopie.COPIEE, BLOQUER, null),
                Arguments.of(EtatCopie.COPIEE, REPRENDRE, null),
                Arguments.of(EtatCopie.COPIEE, TERMINER, null),
                Arguments.of(EtatCopie.COPIEE, REESSAYER, null));
    }

    @ParameterizedTest(name = "{0} par {1} : {2}")
    @MethodSource("tableauDesTransitions")
    void cs18_seulesLesTransitionsDeLaSection42Aboutissent(EtatCopie depart, Transition transition, EtatCopie attendu) {
        Recu recu = recuA(depart);

        if (attendu == null) {
            assertThatThrownBy(() -> transition.action().accept(recu)).isInstanceOf(IllegalStateException.class);
            assertThat(recu.getEtatCopie()).isEqualTo(depart);
        } else {
            transition.action().accept(recu);
            assertThat(recu.getEtatCopie()).isEqualTo(attendu);
        }
    }

    @Test
    void cs18_lesQuinzeCouplesEtatEtTransitionSontTousDansLeTableau() {
        assertThat(tableauDesTransitions().map(arguments -> arguments.get()[0] + "/" + arguments.get()[1])).doesNotHaveDuplicates().hasSize(15);
    }

    @Test
    void cs18_unBlocageLeveOuUneCorrectionEffaceLeMotif() {
        Recu bloque = recuA(EtatCopie.BLOQUEE);
        bloque.corriger();
        assertThat(bloque.getMotifBlocage()).isNull();
    }

    @Test
    void cs18_uneCopieTerminee_alorsQueLaVersionAChangeResteACopier() {
        Recu recu = recu("e");
        int versionCopiee = recu.getVersion();
        recu.corriger();

        assertThat(recu.terminerCopie(versionCopiee)).isFalse();

        assertThat(recu.getEtatCopie()).isEqualTo(EtatCopie.A_COPIER);
        assertThat(recu.getVersion()).isEqualTo(versionCopiee + 1);
    }

    @Test
    void cs18_uneCopieDeLaVersionCouranteAboutit() {
        Recu recu = recu("e");

        assertThat(recu.terminerCopie(recu.getVersion())).isTrue();

        assertThat(recu.getEtatCopie()).isEqualTo(EtatCopie.COPIEE);
    }
}
