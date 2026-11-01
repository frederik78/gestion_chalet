package ca.gestionchalet;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import archfixtures.regles.client.ClientFournisseur;
import archfixtures.regles.client.FrontiereExterne;
import archfixtures.regles.domain.DomaineAvecPersistance;
import archfixtures.regles.domain.DomaineAvecServlet;
import archfixtures.regles.domain.DomaineAvecSpring;
import archfixtures.regles.domain.EntiteAvecSetter;
import archfixtures.regles.domain.EntiteConforme;
import archfixtures.regles.domain.EntiteConstructeurPublic;
import archfixtures.regles.domain.HorsPaquetDTO;
import archfixtures.regles.domain.MontantFlottant;
import archfixtures.regles.service.AppelDirectFournisseurService;
import archfixtures.regles.service.CalculFlottantService;
import archfixtures.regles.service.ControleurHorsWebResource;
import archfixtures.regles.service.EntiteHorsDomaine;
import archfixtures.regles.service.LectureObjetService;
import archfixtures.regles.service.MalNomme;
import archfixtures.regles.service.PortInutile;
import archfixtures.regles.service.dto.ClasseDTO;
import archfixtures.regles.service.dto.ConformeDTO;
import archfixtures.regles.service.dto.PolymorpheDTO;
import archfixtures.regles.web.rest.AccesDonneesResource;
import archfixtures.regles.web.rest.RessourceMalNommee;
import archfixtures.regles.web.rest.SerialiseEntiteResource;
import archfixtures.regles.web.rest.ServiceHorsCoucheService;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.JavaParameter;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

/**
 * Règles de code de l'ADR 0001 et d'AGENTS.md. Chaque règle s'applique au projet et est prouvée par une violation
 * volontaire (classes de {@code archfixtures.regles}, hors de l'analyse de composants de Spring).
 */
class ReglesAgentsTest {

    /** Emprunts du domaine à Spring ou à Hibernate, chacun justifié ici. Aucun à ce jour. */
    private static final Set<String> EXCEPTIONS_DOMAINE = Set.of();

    private static final String ENTITE = "jakarta.persistence.Entity";

    private static ArchRule ressourcesNommeesDansWebRest(String racine) {
        return classes().that().areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                .should().resideInAPackage(racine + ".web.rest..")
                .andShould().haveSimpleNameEndingWith("Resource")
                .allowEmptyShould(true);
    }

    private static ArchRule webSansAccesAuxDonnees(String racine) {
        return noClasses().that().resideInAPackage(racine + ".web..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        racine + ".repository..", racine + ".client..",
                        "jakarta.persistence..", "org.springframework.data..", "java.sql..", "javax.sql..")
                .allowEmptyShould(true);
    }

    private static ArchRule domaineSansWeb(String racine) {
        return noClasses().that().resideInAPackage(racine + ".domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        racine + ".web..", "jakarta.servlet..", "org.springframework.web..", "org.springframework.security..")
                .allowEmptyShould(true);
    }

    private static ArchRule domaineSansSpringNiHibernate(String racine) {
        return noClasses().that().resideInAPackage(racine + ".domain..")
                .and(DescribedPredicate.describe("hors exceptions nommées", classe -> !EXCEPTIONS_DOMAINE.contains(classe.getName())))
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "org.hibernate..")
                .allowEmptyShould(true);
    }

    private static ArchRule interfacesSeulementDansClient(String racine) {
        DescribedPredicate<JavaClass> depotOuProjection = assignableTo("org.springframework.data.repository.Repository")
                .or(annotatedWith("org.mapstruct.Mapper"));
        return noClasses().that().areInterfaces()
                .and().areNotAnnotations()
                .and(not(depotOuProjection))
                .should().resideOutsideOfPackage(racine + ".client..")
                .allowEmptyShould(true);
    }

    private static ArchRule entitesDansLeDomaine(String racine) {
        return classes().that().areAnnotatedWith(ENTITE)
                .should().resideInAPackage(racine + ".domain..")
                .allowEmptyShould(true);
    }

    private static ArchRule entitesSansSetterPublic() {
        return noMethods().that().areDeclaredInClassesThat().areAnnotatedWith(ENTITE)
                .and().arePublic()
                .should().haveNameMatching("set[A-Z].*")
                .allowEmptyShould(true);
    }

    private static ArchRule entitesAvecConstructeurProtege() {
        ArchCondition<JavaClass> protege = new ArchCondition<>("avoir un constructeur sans argument protected") {
            @Override
            public void check(JavaClass classe, ConditionEvents evenements) {
                boolean conforme = classe.tryGetConstructor()
                        .map(constructeur -> constructeur.getModifiers().contains(JavaModifier.PROTECTED))
                        .orElse(false);
                evenements.add(new SimpleConditionEvent(classe, conforme,
                        classe.getName() + " : constructeur sans argument non protected"));
            }
        };
        return classes().that().areAnnotatedWith(ENTITE).should(protege).allowEmptyShould(true);
    }

    private static ArchRule dtoEnRecordsAuBonEndroit(String racine) {
        return classes().that().haveSimpleNameEndingWith("DTO")
                .should().beAssignableTo(Record.class)
                .andShould().resideInAnyPackage(racine + ".service.dto..", racine + ".web.rest.errors..")
                .allowEmptyShould(true);
    }

    private static ArchRule entiteJamaisSerialisee(String racine) {
        return noClasses().that().resideInAnyPackage(racine + ".web..", racine + ".service.dto..")
                .should().dependOnClassesThat().areAnnotatedWith(ENTITE)
                .allowEmptyShould(true);
    }

    private static ArchRule jamaisDeFlottant() {
        ArchCondition<JavaClass> sansFlottant = new ArchCondition<>("n'exposer aucun flottant") {
            private static final Set<String> FLOTTANTS = Set.of("float", "double", "java.lang.Float", "java.lang.Double");

            @Override
            public void check(JavaClass classe, ConditionEvents evenements) {
                Stream<String> champs = classe.getFields().stream().map(JavaField::getRawType).map(JavaClass::getName);
                Stream<String> retours = classe.getMethods().stream().map(m -> m.getRawReturnType().getName());
                Stream<String> parametres = classe.getCodeUnits().stream()
                        .flatMap(unite -> unite.getParameters().stream())
                        .map(JavaParameter::getRawType).map(JavaClass::getName);
                Stream.of(champs, retours, parametres).flatMap(Function.identity())
                        .filter(FLOTTANTS::contains)
                        .forEach(type -> evenements.add(SimpleConditionEvent.violated(classe, classe.getName() + " utilise " + type)));
            }
        };
        return classes().should(sansFlottant).allowEmptyShould(true);
    }

    private static ArchRule jamaisDeDeserialisationPolymorphe() {
        return noClasses().should().dependOnClassesThat().haveFullyQualifiedName(ObjectInputStream.class.getName())
                .orShould().dependOnClassesThat().haveFullyQualifiedName("com.fasterxml.jackson.annotation.JsonTypeInfo")
                .orShould().callMethodWhere(JavaCall.Predicates.target(DescribedPredicate.describe("activer le typage par défaut",
                        cible -> cible.getName().matches("(enable|activate)DefaultTyping.*"))))
                .allowEmptyShould(true);
    }

    private static ArchRule fournisseursDerriereClient(String racine) {
        return noClasses().that().resideOutsideOfPackages(racine + ".client..", racine + ".config..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework.ai..", "com.anthropic..", "com.google..")
                .allowEmptyShould(true);
    }

    private static ArchRule servicesNommesDansService(String racine) {
        return classes().that().areAnnotatedWith("org.springframework.stereotype.Service")
                .should().resideInAPackage(racine + ".service..")
                .andShould().haveSimpleNameEndingWith("Service")
                .allowEmptyShould(true);
    }

    private static final JavaClasses PROJET = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("ca.gestionchalet");

    static Stream<ArchRule> reglesSurLeProjet() {
        String racine = "ca.gestionchalet";
        return Stream.of(ressourcesNommeesDansWebRest(racine), webSansAccesAuxDonnees(racine), domaineSansWeb(racine),
                domaineSansSpringNiHibernate(racine), interfacesSeulementDansClient(racine), entitesDansLeDomaine(racine),
                entitesSansSetterPublic(), entitesAvecConstructeurProtege(), dtoEnRecordsAuBonEndroit(racine),
                entiteJamaisSerialisee(racine), jamaisDeFlottant(), jamaisDeDeserialisationPolymorphe(),
                fournisseursDerriereClient(racine), servicesNommesDansService(racine));
    }

    @ParameterizedTest
    @MethodSource("reglesSurLeProjet")
    void leProjetRespecteLaRegle(ArchRule regle) {
        regle.check(PROJET);
    }

    static Stream<Arguments> violations() {
        String racine = "archfixtures.regles";
        return Stream.of(
                Arguments.of(ressourcesNommeesDansWebRest(racine), List.of(RessourceMalNommee.class)),
                Arguments.of(ressourcesNommeesDansWebRest(racine), List.of(ControleurHorsWebResource.class)),
                Arguments.of(webSansAccesAuxDonnees(racine), List.of(AccesDonneesResource.class)),
                Arguments.of(domaineSansWeb(racine), List.of(DomaineAvecServlet.class)),
                Arguments.of(domaineSansSpringNiHibernate(racine), List.of(DomaineAvecSpring.class)),
                Arguments.of(interfacesSeulementDansClient(racine), List.of(PortInutile.class)),
                Arguments.of(entitesDansLeDomaine(racine), List.of(EntiteHorsDomaine.class)),
                Arguments.of(entitesSansSetterPublic(), List.of(EntiteAvecSetter.class)),
                Arguments.of(entitesAvecConstructeurProtege(), List.of(EntiteConstructeurPublic.class)),
                Arguments.of(dtoEnRecordsAuBonEndroit(racine), List.of(ClasseDTO.class)),
                Arguments.of(dtoEnRecordsAuBonEndroit(racine), List.of(HorsPaquetDTO.class)),
                Arguments.of(entiteJamaisSerialisee(racine), List.of(SerialiseEntiteResource.class, EntiteConforme.class)),
                Arguments.of(jamaisDeFlottant(), List.of(MontantFlottant.class)),
                Arguments.of(jamaisDeFlottant(), List.of(CalculFlottantService.class)),
                Arguments.of(jamaisDeDeserialisationPolymorphe(), List.of(LectureObjetService.class)),
                Arguments.of(jamaisDeDeserialisationPolymorphe(), List.of(PolymorpheDTO.class)),
                Arguments.of(fournisseursDerriereClient(racine), List.of(AppelDirectFournisseurService.class)),
                Arguments.of(servicesNommesDansService(racine), List.of(ServiceHorsCoucheService.class)),
                Arguments.of(servicesNommesDansService(racine), List.of(MalNomme.class)));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("violations")
    void uneViolationVolontaireFaitEchouerLaRegle(ArchRule regle, List<Class<?>> classes) {
        JavaClasses importees = new ClassFileImporter().importClasses(classes);
        assertThatThrownBy(() -> regle.check(importees))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(classes.getFirst().getSimpleName());
    }

    static Stream<Arguments> conformes() {
        String racine = "archfixtures.regles";
        return Stream.of(
                Arguments.of(domaineSansSpringNiHibernate(racine), DomaineAvecPersistance.class),
                Arguments.of(interfacesSeulementDansClient(racine), FrontiereExterne.class),
                Arguments.of(entitesSansSetterPublic(), EntiteConforme.class),
                Arguments.of(entitesAvecConstructeurProtege(), EntiteConforme.class),
                Arguments.of(entitesDansLeDomaine(racine), EntiteConforme.class),
                Arguments.of(dtoEnRecordsAuBonEndroit(racine), ConformeDTO.class),
                Arguments.of(fournisseursDerriereClient(racine), ClientFournisseur.class));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("conformes")
    void uneClasseConformePasseLaRegle(ArchRule regle, Class<?> classe) {
        JavaClasses importee = new ClassFileImporter().importClasses(classe);
        assertThatCode(() -> regle.check(importee)).doesNotThrowAnyException();
    }

    private static void verifierDdlAuto(Resource yaml) throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load("application", yaml);
        boolean valide = sources.stream()
                .anyMatch(source -> "validate".equals(source.getProperty("spring.jpa.hibernate.ddl-auto")));
        if (!valide) {
            throw new AssertionError("spring.jpa.hibernate.ddl-auto doit valoir validate : Liquibase seul gère le schéma");
        }
    }

    @Test
    void hibernateNeFaitQueValiderLeSchemaGereParLiquibase() throws IOException {
        verifierDdlAuto(new ClassPathResource("application.yml"));
    }

    @Test
    void unDdlAutoAutreQueValidateFaitEchouerLaRegle() {
        Resource violation = new ByteArrayResource("spring.jpa.hibernate.ddl-auto: update".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> verifierDdlAuto(violation)).isInstanceOf(AssertionError.class);
    }
}
