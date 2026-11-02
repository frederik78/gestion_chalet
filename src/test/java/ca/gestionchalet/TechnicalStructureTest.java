package ca.gestionchalet;

import static com.tngtech.archunit.base.DescribedPredicate.alwaysTrue;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import archfixtures.layers.other.OtherToSecurity;
import archfixtures.layers.client.ClientToDomain;
import archfixtures.layers.client.ClientToService;
import archfixtures.layers.config.ConfigToWeb;
import archfixtures.layers.domain.DomainToService;
import archfixtures.layers.repository.RepositoryToDomain;
import archfixtures.layers.repository.RepositoryToSecurity;
import archfixtures.layers.security.SecurityToRepository;
import archfixtures.layers.service.ServiceToClient;
import archfixtures.layers.service.ServiceToRepository;
import archfixtures.layers.service.ServiceToSecurity;
import archfixtures.layers.service.ServiceToWeb;
import archfixtures.layers.web.WebToClient;
import archfixtures.layers.web.WebToConfig;
import archfixtures.layers.web.WebToRepository;
import archfixtures.layers.web.WebToSecurity;
import archfixtures.layers.web.WebToService;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Droits d'accès entre couches de l'ADR 0001, chaque ligne du tableau étant prouvée par une violation volontaire. */
@AnalyzeClasses(packagesOf = GestionChaletApplication.class, importOptions = ImportOption.DoNotIncludeTests.class)
class TechnicalStructureTest {

    @ArchTest
    static final ArchRule respectsTechnicalArchitectureLayers = layeredArchitecture()
            .consideringAllDependencies()
            .withOptionalLayers(true)
            .layer("Config").definedBy("..config..")
            .layer("Web").definedBy("..web..")
            .layer("Service").definedBy("..service..")
            .layer("Security").definedBy("..security..")
            .layer("Repository").definedBy("..repository..")
            .layer("Client").definedBy("..client..")
            .layer("Domain").definedBy("..domain..")
            .whereLayer("Config").mayNotBeAccessedByAnyLayer()
            .whereLayer("Web").mayOnlyBeAccessedByLayers("Config")
            .whereLayer("Service").mayOnlyBeAccessedByLayers("Web", "Config")
            .whereLayer("Security").mayOnlyBeAccessedByLayers("Config", "Service", "Web")
            .whereLayer("Repository").mayOnlyBeAccessedByLayers("Service", "Security", "Config")
            .whereLayer("Client").mayOnlyBeAccessedByLayers("Service", "Config")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Config", "Web", "Service", "Security", "Repository", "Client")
            .ignoreDependency(belongToAnyOf(GestionChaletApplication.class), alwaysTrue());

    private static JavaClasses fixtures(Class<?> source) throws ClassNotFoundException {
        String cible = source.getSimpleName().replaceFirst(".*To", "").toLowerCase();
        return new ClassFileImporter().importClasses(source, Class.forName("archfixtures.layers." + cible + ".Target"));
    }

    static Stream<Arguments> accesInterdits() {
        return Stream.of(
                Arguments.of(WebToConfig.class, "config : personne"),
                Arguments.of(ServiceToWeb.class, "web : config seulement"),
                Arguments.of(ClientToService.class, "service : web et config"),
                Arguments.of(RepositoryToSecurity.class, "security : config, service, web"),
                Arguments.of(OtherToSecurity.class, "security : refusée aussi à une classe hors couche"),
                Arguments.of(WebToRepository.class, "repository : service, security, config"),
                Arguments.of(WebToClient.class, "client : service, config"),
                Arguments.of(DomainToService.class, "le domaine ne dépend de rien"));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("accesInterdits")
    void uneViolationVolontaireFaitEchouerLaRegle(Class<?> source, String regle) throws ClassNotFoundException {
        JavaClasses classes = fixtures(source);
        assertThatThrownBy(() -> respectsTechnicalArchitectureLayers.check(classes))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(source.getSimpleName());
    }

    static Stream<Class<?>> accesPermis() {
        return Stream.of(ConfigToWeb.class, WebToService.class, WebToSecurity.class, ServiceToSecurity.class,
                ServiceToRepository.class, ServiceToClient.class, SecurityToRepository.class,
                ClientToDomain.class, RepositoryToDomain.class);
    }

    @ParameterizedTest
    @MethodSource("accesPermis")
    void unAccesPermisPasseLaRegle(Class<?> source) throws ClassNotFoundException {
        JavaClasses classes = fixtures(source);
        assertThatCode(() -> respectsTechnicalArchitectureLayers.check(classes)).doesNotThrowAnyException();
    }
}
