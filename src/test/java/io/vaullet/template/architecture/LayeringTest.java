package io.vaullet.template.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.Architectures;
import com.tngtech.archunit.library.GeneralCodingRules;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/**
 * The layering rules, enforced by the build instead of by code review.
 *
 * <p>Architecture documented only in a README decays: the first pull request that injects a
 * repository into a controller because it was quicker will be approved by someone in a hurry, and
 * after three of those the layers exist in name only. These tests fail that pull request instead.
 *
 * <p>The rules are deliberately few. A rule nobody can justify gets suppressed rather than obeyed,
 * so each one here is a boundary that would actually cost something to lose.
 */
@AnalyzeClasses(
        packages = "io.vaullet.template",
        importOptions = ImportOption.DoNotIncludeTests.class)
class LayeringTest {

    @ArchTest
    static final ArchRule layers_are_respected = Architectures.layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("API")
            .definedBy("io.vaullet.template..api..")
            .layer("Service")
            .definedBy("io.vaullet.template..service..")
            .layer("DAO")
            .definedBy("io.vaullet.template..dao..")
            .whereLayer("API")
            .mayNotBeAccessedByAnyLayer()
            .whereLayer("Service")
            .mayOnlyBeAccessedByLayers("API")
            .whereLayer("DAO")
            .mayOnlyBeAccessedByLayers("Service");

    /**
     * The single most common shortcut: a controller that "just needs one query". It couples the HTTP
     * contract to the schema and puts the transaction boundary in the wrong place.
     */
    @ArchTest
    static final ArchRule controllers_do_not_reach_into_repositories = noClasses()
            .that()
            .haveSimpleNameEndingWith("Controller")
            .should()
            .dependOnClassesThat()
            .areAnnotatedWith(Repository.class)
            .because("controllers must go through the service layer, which owns transactions and rules");

    /**
     * Entities must not escape the DAO layer. Serialising one exposes the schema as the API
     * contract and invites lazy-initialisation failures inside the message converter.
     */
    @ArchTest
    static final ArchRule entities_stay_in_the_dao_layer = noClasses()
            .that()
            .resideInAPackage("io.vaullet.template..api..")
            .should()
            .dependOnClassesThat()
            .areAnnotatedWith(jakarta.persistence.Entity.class)
            .because("JPA entities are a persistence detail and must not appear in an HTTP payload");

    @ArchTest
    static final ArchRule no_field_injection = noFields()
            .should()
            .beAnnotatedWith(Autowired.class)
            .because("constructor injection makes dependencies explicit, final, and testable without a container");

    @ArchTest
    static final ArchRule no_standard_streams = GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

    @ArchTest
    static final ArchRule no_java_util_logging = GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

    @ArchTest
    static final ArchRule no_joda_or_legacy_date_api =
            GeneralCodingRules.NO_CLASSES_SHOULD_USE_JODATIME.because("java.time is the only date API this codebase uses");
}
