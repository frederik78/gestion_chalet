package ca.gestionchalet;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import archfixtures.rules.client.ProviderClient;
import archfixtures.rules.client.ExternalBoundary;
import archfixtures.rules.domain.DomainWithPersistence;
import archfixtures.rules.domain.DomainWithServlet;
import archfixtures.rules.domain.DomainWithSpring;
import archfixtures.rules.domain.EntityWithSetter;
import archfixtures.rules.domain.CompliantEntity;
import archfixtures.rules.domain.EntityWithPublicConstructor;
import archfixtures.rules.domain.OutsidePackageDTO;
import archfixtures.rules.domain.FloatingAmount;
import archfixtures.rules.repository.CrudOfferingRepository;
import archfixtures.rules.repository.DerivedDeleteRepository;
import archfixtures.rules.repository.NativePurgingRepository;
import archfixtures.rules.repository.NeighbourStorage;
import archfixtures.rules.repository.SpecificationRepository;
import archfixtures.rules.repository.QueryPurgingRepository;
import archfixtures.rules.service.DirectProviderCallService;
import archfixtures.rules.service.EntityManagerRemovingService;
import archfixtures.rules.service.JdbcPurgingService;
import archfixtures.rules.service.LegacyFileDeletingService;
import archfixtures.rules.service.ScheduledDiscardingService;
import archfixtures.rules.service.FileDeletingService;
import archfixtures.rules.service.PhotoDiscardingByReferenceService;
import archfixtures.rules.service.PhotoDiscardingService;
import archfixtures.rules.service.FloatingCalculationService;
import archfixtures.rules.service.ControllerOutsideWebResource;
import archfixtures.rules.service.EntityOutsideDomain;
import archfixtures.rules.service.ObjectReadingService;
import archfixtures.rules.service.BadlyNamed;
import archfixtures.rules.service.UselessPort;
import archfixtures.rules.service.dto.ClassDTO;
import archfixtures.rules.service.dto.CompliantDTO;
import archfixtures.rules.service.dto.PolymorphicDTO;
import archfixtures.rules.web.rest.DataAccessResource;
import archfixtures.rules.web.rest.ResourceBadlyNamed;
import archfixtures.rules.web.rest.SerializesEntityResource;
import archfixtures.rules.web.rest.ServiceOutsideLayerService;
import ca.gestionchalet.domain.Photo;
import ca.gestionchalet.repository.PhotoStorage;
import ca.gestionchalet.repository.RecuRepository;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.JavaParameter;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
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
 * volontaire (classes de {@code archfixtures.rules}, hors de l'analyse de composants de Spring).
 */
class AgentRulesTest {

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

    /** R-35 : aucun code ne retire un fichier, hors du retrait d'une validation qui n'a pas abouti. */
    private static ArchRule fichiersRetiresSeulementParLeStockage(String racine) {
        return noClasses().that().doNotHaveFullyQualifiedName(racine + ".repository.PhotoStorage")
                .should().callMethodWhere(JavaCall.Predicates.target(DescribedPredicate.describe("retirer un fichier",
                        cible -> cible.getOwner().isEquivalentTo(Files.class) && cible.getName().startsWith("delete"))))
                .allowEmptyShould(true);
    }

    private static final String RETRAIT_DE_PHOTO = "discard";

    /**
     * La méthode est nommée par une chaîne : si elle n'existe plus, la règle échoue au lieu de devenir muette. Seuls le retour de
     * transaction de {@code RecuService} et {@code PhotoStorage} lui-même y ont droit. Les accès comptent les références de méthode,
     * que {@code callMethod} ne voit pas (mesuré). Limite : un appel indirect, par une méthode intermédiaire de ces classes, n'est pas suivi.
     */
    private static ArchRule seulLeRetourDeTransactionRetireUnePhoto(String racine, String methode) {
        try {
            PhotoStorage.class.getMethod(methode, Photo.class);
        } catch (NoSuchMethodException e) {
            throw new AssertionError("PhotoStorage." + methode + " n'existe plus : la règle serait muette", e);
        }
        ArchCondition<JavaClass> sansRetrait = new ArchCondition<>("ne pas retirer de photo hors du retour de transaction") {
            @Override
            public void check(JavaClass classe, ConditionEvents evenements) {
                boolean stockage = classe.getName().equals(racine + ".repository.PhotoStorage");
                boolean service = classe.getName().startsWith(racine + ".service.RecuService$");
                classe.getAccessesFromSelf().stream()
                        .filter(acces -> acces.getTargetOwner().isEquivalentTo(PhotoStorage.class) && acces.getName().equals(methode))
                        .filter(acces -> !stockage && !(service && acces.getOrigin().getName().equals("afterCompletion")))
                        .forEach(acces -> evenements.add(SimpleConditionEvent.violated(classe, acces.getDescription())));
            }
        };
        return classes().should(sansRetrait).allowEmptyShould(true);
    }

    private static final Set<String> SQL_DIRECT = Set.of("org.springframework.jdbc.core.JdbcTemplate",
            "org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate", "org.springframework.jdbc.core.simple.JdbcClient");

    /** Classes autorisées à écrire du SQL direct : aucune à ce jour. */
    private static final Set<String> SQL_DIRECT_AUTORISE = Set.of();

    /** Limite : les requêtes de {@code EntityManager.createQuery} ne sont pas lues. */
    private static ArchRule niEntityManagerRemoveNiSqlDirect() {
        ArchCondition<JavaClass> sansSuppression = new ArchCondition<>("ne pas supprimer par EntityManager ni par SQL direct") {
            @Override
            public void check(JavaClass classe, ConditionEvents evenements) {
                classe.getAccessesFromSelf().stream()
                        .filter(acces -> acces.getTargetOwner().isEquivalentTo(EntityManager.class) && acces.getName().equals("remove"))
                        .forEach(acces -> evenements.add(SimpleConditionEvent.violated(classe, acces.getDescription())));
                if (!SQL_DIRECT_AUTORISE.contains(classe.getName())) {
                    classe.getDirectDependenciesFromSelf().stream()
                            .filter(dependance -> SQL_DIRECT.contains(dependance.getTargetClass().getName()))
                            .forEach(dependance -> evenements.add(SimpleConditionEvent.violated(classe, dependance.getDescription())));
                }
            }
        };
        return classes().should(sansSuppression).allowEmptyShould(true);
    }

    private static final Pattern REQUETE_DE_SUPPRESSION = Pattern.compile("(?i)\\b(delete|truncate|drop)\\b");

    private static boolean retireUnFichier(String proprietaire, String methode) {
        return switch (proprietaire) {
            case "java.io.File" -> methode.equals("delete") || methode.equals("deleteOnExit");
            case "java.nio.file.Files" -> methode.startsWith("delete");
            case "org.springframework.util.FileSystemUtils" -> methode.startsWith("delete");
            default -> false;
        };
    }

    /** CA72 : seuls le retrait d'une validation en échec et le fichier temporaire d'une écriture retirent un fichier. */
    private static ArchRule aucuneSuppressionDeFichierAilleurs(String racine) {
        String stockage = racine + ".repository.PhotoStorage";
        ArchCondition<JavaClass> sansSuppression = new ArchCondition<>("ne retirer aucun fichier") {
            @Override
            public void check(JavaClass classe, ConditionEvents evenements) {
                classe.getAccessesFromSelf().stream()
                        .filter(acces -> retireUnFichier(acces.getTargetOwner().getName(), acces.getName()))
                        .filter(acces -> !(classe.getName().equals(stockage) && Set.of("discard", "write").contains(acces.getOrigin().getName())))
                        .forEach(acces -> evenements.add(SimpleConditionEvent.violated(classe, acces.getDescription())));
            }
        };
        return classes().should(sansSuppression).allowEmptyShould(true);
    }

    /** CA72 : ni héritage qui offre la suppression, ni méthode dérivée, ni requête, quel que soit son nom. */
    private static ArchRule depotsSansSuppression() {
        ArchCondition<JavaClass> sansSuppression = new ArchCondition<>("n'offrir aucune suppression") {
            private static final String REQUETE = "org.springframework.data.jpa.repository.Query";

            @Override
            public void check(JavaClass depot, ConditionEvents evenements) {
                if (depot.isAssignableTo("org.springframework.data.repository.CrudRepository")
                        || depot.isAssignableTo("org.springframework.data.repository.ListCrudRepository")
                        || depot.isAssignableTo("org.springframework.data.jpa.repository.JpaSpecificationExecutor")) {
                    evenements.add(SimpleConditionEvent.violated(depot, depot.getName() + " hérite de la suppression"));
                }
                depot.getMethods().stream().filter(methode -> methode.getName().matches("(delete|remove).*")
                        || methode.isAnnotatedWith("org.springframework.data.jpa.repository.Modifying")
                        || (methode.isAnnotatedWith(REQUETE)
                                && REQUETE_DE_SUPPRESSION.matcher(String.valueOf(methode.getAnnotationOfType(REQUETE).get("value").orElse(""))).find()))
                        .forEach(methode -> evenements.add(SimpleConditionEvent.violated(depot, depot.getName() + "." + methode.getName() + " supprime")));
            }
        };
        return classes().that().areAssignableTo("org.springframework.data.repository.Repository")
                .should(sansSuppression).allowEmptyShould(true);
    }

    /**
     * CA72 : la planification reste permise, car 001-08 (« nouveaux essais et rythme », reprise de la copie) peut en avoir besoin ;
     * la règle porte donc sur la suppression : une tâche planifiée n'appelle pas le retrait d'une photo.
     */
    private static ArchRule tachePlanifieeSansRetrait() {
        ArchCondition<JavaMethod> sansRetrait = new ArchCondition<>("ne retirer aucune photo") {
            @Override
            public void check(JavaMethod tache, ConditionEvents evenements) {
                tache.getAccessesFromSelf().stream()
                        .filter(acces -> acces.getTargetOwner().isEquivalentTo(PhotoStorage.class) && acces.getName().equals(RETRAIT_DE_PHOTO))
                        .forEach(acces -> evenements.add(SimpleConditionEvent.violated(tache, tache.getOwner().getName() + " : " + acces.getDescription())));
            }
        };
        return methods().that().areAnnotatedWith("org.springframework.scheduling.annotation.Scheduled")
                .should(sansRetrait).allowEmptyShould(true);
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
                fournisseursDerriereClient(racine), servicesNommesDansService(racine),
                fichiersRetiresSeulementParLeStockage(racine));
    }

    @Test
    void ca72_leDepotDesRecusNOffreAucuneSuppression() {
        assertThat(RecuRepository.class.getMethods())
                .extracting(Method::getName)
                .noneMatch(nom -> nom.startsWith("delete") || nom.startsWith("remove"));
    }

    @Test
    void ca72_seulLeRetourDeTransactionAccedeDirectementAuRetraitDePhoto() {
        seulLeRetourDeTransactionRetireUnePhoto("ca.gestionchalet", RETRAIT_DE_PHOTO).check(PROJET);
    }

    @Test
    void ca72_uneMethodeDeRetraitRenommeeFaitEchouerLaRegle() {
        assertThatThrownBy(() -> seulLeRetourDeTransactionRetireUnePhoto("ca.gestionchalet", "methodeRenommee"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("methodeRenommee");
    }

    /** Limite : ni l'écrasement d'un fichier ({@code Files.write}, {@code REPLACE_EXISTING}), ni un appel indirect, ni un {@code <delete>} Liquibase. */
    @Test
    void ca72_aucunAccesDirectAUneSuppressionDeFichierHorsDuStockage() {
        aucuneSuppressionDeFichierAilleurs("ca.gestionchalet").check(PROJET);
    }

    /** Limite : les déclarations du dépôt seulement, pas le SQL d'une autre classe, voir {@link #ca72_niEntityManagerRemoveNiSqlDirect()}. */
    @Test
    void ca72_aucunDepotNeDeclareNiNHeriteDeSuppression() {
        depotsSansSuppression().check(PROJET);
    }

    @Test
    void ca72_niEntityManagerRemoveNiSqlDirect() {
        niEntityManagerRemoveNiSqlDirect().check(PROJET);
    }

    /** Limite : l'accès direct depuis une méthode {@code @Scheduled}, sans analyse transitive ; {@code TaskScheduler}, {@code CommandLineRunner} et {@code @EventListener} ne sont pas vus. */
    @Test
    void ca72_uneMethodeScheduledNAccedePasDirectementAuRetraitDePhoto() {
        tachePlanifieeSansRetrait().check(PROJET);
    }

    @ParameterizedTest
    @MethodSource("reglesSurLeProjet")
    void leProjetRespecteLaRegle(ArchRule regle) {
        regle.check(PROJET);
    }

    static Stream<Arguments> violations() {
        String racine = "archfixtures.rules";
        return Stream.of(
                Arguments.of(ressourcesNommeesDansWebRest(racine), List.of(ResourceBadlyNamed.class)),
                Arguments.of(ressourcesNommeesDansWebRest(racine), List.of(ControllerOutsideWebResource.class)),
                Arguments.of(webSansAccesAuxDonnees(racine), List.of(DataAccessResource.class)),
                Arguments.of(domaineSansWeb(racine), List.of(DomainWithServlet.class)),
                Arguments.of(domaineSansSpringNiHibernate(racine), List.of(DomainWithSpring.class)),
                Arguments.of(interfacesSeulementDansClient(racine), List.of(UselessPort.class)),
                Arguments.of(entitesDansLeDomaine(racine), List.of(EntityOutsideDomain.class)),
                Arguments.of(entitesSansSetterPublic(), List.of(EntityWithSetter.class)),
                Arguments.of(entitesAvecConstructeurProtege(), List.of(EntityWithPublicConstructor.class)),
                Arguments.of(dtoEnRecordsAuBonEndroit(racine), List.of(ClassDTO.class)),
                Arguments.of(dtoEnRecordsAuBonEndroit(racine), List.of(OutsidePackageDTO.class)),
                Arguments.of(entiteJamaisSerialisee(racine), List.of(SerializesEntityResource.class, CompliantEntity.class)),
                Arguments.of(jamaisDeFlottant(), List.of(FloatingAmount.class)),
                Arguments.of(jamaisDeFlottant(), List.of(FloatingCalculationService.class)),
                Arguments.of(jamaisDeDeserialisationPolymorphe(), List.of(ObjectReadingService.class)),
                Arguments.of(jamaisDeDeserialisationPolymorphe(), List.of(PolymorphicDTO.class)),
                Arguments.of(fournisseursDerriereClient(racine), List.of(DirectProviderCallService.class)),
                Arguments.of(servicesNommesDansService(racine), List.of(ServiceOutsideLayerService.class)),
                Arguments.of(servicesNommesDansService(racine), List.of(BadlyNamed.class)),
                Arguments.of(fichiersRetiresSeulementParLeStockage(racine), List.of(FileDeletingService.class)),
                Arguments.of(seulLeRetourDeTransactionRetireUnePhoto(racine, RETRAIT_DE_PHOTO), List.of(PhotoDiscardingService.class)),
                Arguments.of(seulLeRetourDeTransactionRetireUnePhoto(racine, RETRAIT_DE_PHOTO), List.of(PhotoDiscardingByReferenceService.class)),
                Arguments.of(aucuneSuppressionDeFichierAilleurs(racine), List.of(LegacyFileDeletingService.class)),
                Arguments.of(aucuneSuppressionDeFichierAilleurs(racine), List.of(FileDeletingService.class)),
                Arguments.of(aucuneSuppressionDeFichierAilleurs(racine), List.of(archfixtures.rules.repository.PhotoStorage.class)),
                Arguments.of(depotsSansSuppression(), List.of(QueryPurgingRepository.class)),
                Arguments.of(depotsSansSuppression(), List.of(NativePurgingRepository.class)),
                Arguments.of(depotsSansSuppression(), List.of(DerivedDeleteRepository.class)),
                Arguments.of(depotsSansSuppression(), List.of(CrudOfferingRepository.class)),
                Arguments.of(depotsSansSuppression(), List.of(SpecificationRepository.class)),
                Arguments.of(seulLeRetourDeTransactionRetireUnePhoto(racine, RETRAIT_DE_PHOTO), List.of(archfixtures.rules.service.RecuService.class)),
                Arguments.of(seulLeRetourDeTransactionRetireUnePhoto(racine, RETRAIT_DE_PHOTO), List.of(NeighbourStorage.class)),
                Arguments.of(niEntityManagerRemoveNiSqlDirect(), List.of(EntityManagerRemovingService.class)),
                Arguments.of(niEntityManagerRemoveNiSqlDirect(), List.of(JdbcPurgingService.class)),
                Arguments.of(tachePlanifieeSansRetrait(), List.of(ScheduledDiscardingService.class)));
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
        String racine = "archfixtures.rules";
        return Stream.of(
                Arguments.of(domaineSansSpringNiHibernate(racine), DomainWithPersistence.class),
                Arguments.of(interfacesSeulementDansClient(racine), ExternalBoundary.class),
                Arguments.of(entitesSansSetterPublic(), CompliantEntity.class),
                Arguments.of(entitesAvecConstructeurProtege(), CompliantEntity.class),
                Arguments.of(entitesDansLeDomaine(racine), CompliantEntity.class),
                Arguments.of(dtoEnRecordsAuBonEndroit(racine), CompliantDTO.class),
                Arguments.of(fournisseursDerriereClient(racine), ProviderClient.class));
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
