package ca.gestionchalet.config;

import static org.assertj.core.api.Assertions.assertThat;

import ca.gestionchalet.config.ApplicationProperties.YearSheet;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/** D-18 : la correspondance année vers fichier et onglet se renseigne par l'environnement, une année à la fois, sans entrée dans {@code application.yml}. */
class CopyYearsBindingTest {

    private static Map<Integer, YearSheet> bind(Map<String, Object> environment) {
        MutablePropertySources sources = new MutablePropertySources();
        sources.addFirst(new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, environment));
        return new Binder(ConfigurationPropertySources.from(sources)).bind("recus.copie.annees", Bindable.mapOf(Integer.class, YearSheet.class)).orElse(Map.of());
    }

    @Test
    void d18_desVariablesDEnvironnementDonnentUneEntreeParAnnee() {
        Map<Integer, YearSheet> annees = bind(Map.of(
                "RECUS_COPIE_ANNEES_2026_FICHIER", "fichier-a", "RECUS_COPIE_ANNEES_2026_ONGLET", "Comptes 2026",
                "RECUS_COPIE_ANNEES_2027_FICHIER", "fichier-b", "RECUS_COPIE_ANNEES_2027_ONGLET", "Comptes 2027"));

        assertThat(annees).containsOnlyKeys(2026, 2027);
        assertThat(annees.get(2026)).isEqualTo(new YearSheet("fichier-a", "Comptes 2026"));
        assertThat(annees.get(2027)).isEqualTo(new YearSheet("fichier-b", "Comptes 2027"));
    }

    @Test
    void d62_sansVariableLaCorrespondanceEstVide() {
        assertThat(bind(Map.of())).isEmpty();
    }

    @Test
    void d62_uneVariableSeuleDonneUneEntreeIncompleteQueLaCorrespondanceIgnorera() {
        Map<Integer, YearSheet> annees = bind(Map.of("RECUS_COPIE_ANNEES_2026_FICHIER", "fichier-a"));

        assertThat(annees).containsOnlyKeys(2026);
        assertThat(annees.get(2026).onglet()).isNull();
    }
}
