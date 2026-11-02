package ca.gestionchalet;

import static com.tngtech.archunit.base.DescribedPredicate.alwaysTrue;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import archfixtures.couches.client.ClientVersDomain;
import archfixtures.couches.client.ClientVersService;
import archfixtures.couches.config.ConfigVersWeb;
import archfixtures.couches.domain.DomainVersService;
import archfixtures.couches.repository.RepositoryVersDomain;
import archfixtures.couches.repository.RepositoryVersSecurity;
import archfixtures.couches.security.SecurityVersRepository;
import archfixtures.couches.service.ServiceVersClient;
import archfixtures.couches.service.ServiceVersRepository;
import archfixtures.couches.service.ServiceVersSecurity;
import archfixtures.couches.service.ServiceVersWeb;
import archfixtures.couches.web.WebVersClient;
import archfixtures.couches.web.WebVersConfig;
import archfixtures.couches.web.WebVersRepository;
import archfixtures.couches.web.WebVersSecurity;
import archfixtures.couches.web.WebVersService;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Droits d'accès entre couches de l'ADR 0001, chaque ligne du tableau étant prouvée par une violation volontaire. */
class TechnicalStructureTest {

    private static ArchRule couches(String racine) {
        return layeredArchitecture()
                .consideringAllDependencies()
                .withOptionalLayers(true)
                .layer("Config").definedBy(racine + ".config..")
                .layer("Web").definedBy(racine + ".web..")
                .layer("Service").definedBy(racine + ".service..")
                .layer("Security").definedBy(racine + ".security..")
                .layer("Repository").definedBy(racine + ".repository..")
                .layer("Client").definedBy(racine + ".client..")
                .layer("Domain").definedBy(racine + ".domain..")
                .whereLayer("Config").mayNotBeAccessedByAnyLayer()
                .whereLayer("Web").mayOnlyBeAccessedByLayers("Config")
                .whereLayer("Service").mayOnlyBeAccessedByLayers("Web", "Config")
                .whereLayer("Security").mayOnlyBeAccessedByLayers("Config", "Service", "Web")
                .whereLayer("Repository").mayOnlyBeAccessedByLayers("Service", "Security", "Config")
                .whereLayer("Client").mayOnlyBeAccessedByLayers("Service", "Config")
                .whereLayer("Domain").mayOnlyBeAccessedByLayers("Config", "Web", "Service", "Security", "Repository", "Client")
                .ignoreDependency(belongToAnyOf(GestionChaletApplication.class), alwaysTrue());
    }

    private static JavaClasses fixtures(Class<?> source) throws ClassNotFoundException {
        String cible = source.getSimpleName().replaceFirst(".*Vers", "").toLowerCase();
        return new ClassFileImporter().importClasses(source, Class.forName("archfixtures.couches." + cible + ".Cible"));
    }

    @Test
    void respectsTechnicalArchitectureLayers() {
        JavaClasses projet = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("ca.gestionchalet");
        couches("ca.gestionchalet").check(projet);
    }

    static Stream<Arguments> accesInterdits() {
        return Stream.of(
                Arguments.of(WebVersConfig.class, "config : personne"),
                Arguments.of(ServiceVersWeb.class, "web : config seulement"),
                Arguments.of(ClientVersService.class, "service : web et config"),
                Arguments.of(RepositoryVersSecurity.class, "security : config, service, web"),
                Arguments.of(WebVersRepository.class, "repository : service, security, config"),
                Arguments.of(WebVersClient.class, "client : service, config"),
                Arguments.of(DomainVersService.class, "le domaine ne dépend de rien"));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("accesInterdits")
    void uneViolationVolontaireFaitEchouerLaRegle(Class<?> source, String regle) throws ClassNotFoundException {
        JavaClasses classes = fixtures(source);
        assertThatThrownBy(() -> couches("archfixtures.couches").check(classes))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(source.getSimpleName());
    }

    static Stream<Class<?>> accesPermis() {
        return Stream.of(ConfigVersWeb.class, WebVersService.class, WebVersSecurity.class, ServiceVersSecurity.class,
                ServiceVersRepository.class, ServiceVersClient.class, SecurityVersRepository.class,
                ClientVersDomain.class, RepositoryVersDomain.class);
    }

    @ParameterizedTest
    @MethodSource("accesPermis")
    void unAccesPermisPasseLaRegle(Class<?> source) throws ClassNotFoundException {
        JavaClasses classes = fixtures(source);
        assertThatCode(() -> couches("archfixtures.couches").check(classes)).doesNotThrowAnyException();
    }
}
