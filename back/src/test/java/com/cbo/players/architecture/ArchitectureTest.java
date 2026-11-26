package com.cbo.players.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

@AnalyzeClasses(packages = "com.cbo.players", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {

    @ArchTest
    static final ArchRule layeredArchitectureIsRespected = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Controllers").definedBy("com.cbo.players.controller..")
            .layer("Services").definedBy("com.cbo.players.service..")
            .layer("Repositories").definedBy("com.cbo.players.repository..")
            .layer("Adapters").definedBy("com.cbo.players.adapter..")
            .whereLayer("Controllers").mayNotBeAccessedByAnyLayer()
            .whereLayer("Services").mayOnlyBeAccessedByLayers("Controllers", "Services")
            .whereLayer("Repositories").mayOnlyBeAccessedByLayers("Services", "Repositories")
            .whereLayer("Adapters").mayOnlyBeAccessedByLayers("Services", "Adapters");

    @ArchTest
    static final ArchRule controllersShouldNotDependOnRepositories = noClasses()
            .that().resideInAPackage("..controller..")
            .should().dependOnClassesThat().resideInAPackage("..repository..");

    @ArchTest
    static final ArchRule controllersShouldNotDependOnAdapters = noClasses()
            .that().resideInAPackage("..controller..")
            .should().dependOnClassesThat().resideInAPackage("..adapter..");

    @ArchTest
    static final ArchRule repositoriesShouldNotDependOnServicesOrControllers = noClasses()
            .that().resideInAPackage("..repository..")
            .should().dependOnClassesThat().resideInAnyPackage("..service..", "..controller..");

    @ArchTest
    static final ArchRule adaptersShouldNotDependOnControllers = noClasses()
            .that().resideInAPackage("..adapter..")
            .should().dependOnClassesThat().resideInAPackage("..controller..");

    @ArchTest
    static final ArchRule noCyclicDependenciesBetweenPackages = slices()
            .matching("com.cbo.players.(*)..")
            .should().beFreeOfCycles();

    @ArchTest
    static final ArchRule controllersMustNotExposeJpaEntities = methods()
            .that().areDeclaredInClassesThat().resideInAPackage("..controller..")
            .and().arePublic()
            .should(notExposeJpaEntityInReturnType());

    @ArchTest
    static final ArchRule restControllersFollowPackageAndNamingConventions = classes()
            .that().areAnnotatedWith(RestController.class)
            .should().resideInAPackage("..controller..")
            .andShould().haveSimpleNameEndingWith("Controller");

    @ArchTest
    static final ArchRule servicesFollowPackageAndNamingConventions = classes()
            .that().areAnnotatedWith(Service.class)
            .should().resideInAPackage("..service..")
            .andShould().haveSimpleNameEndingWith("Service");

    @ArchTest
    static final ArchRule repositoriesFollowPackageAndNamingConventions = classes()
            .that().areAnnotatedWith(Repository.class)
            .should().resideInAPackage("..repository..")
            .andShould().haveSimpleNameEndingWith("Repository");

    private static ArchCondition<JavaMethod> notExposeJpaEntityInReturnType() {
        return new ArchCondition<>("not return JPA @Entity types directly or inside generic wrappers") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                if (containsJpaEntity(method.getReturnType())) {
                    String message = String.format(
                            "El método público de controlador %s expone una entidad JPA (%s); debe usar DTOs.",
                            method.getFullName(), method.getReturnType().getName());
                    events.add(SimpleConditionEvent.violated(method, message));
                }
            }
        };
    }

    private static boolean containsJpaEntity(JavaType type) {
        if (type == null) {
            return false;
        }
        JavaClass rawClass = type.toErasure();
        if (rawClass.isAnnotatedWith(Entity.class)) {
            return true;
        }
        if (type instanceof JavaParameterizedType parameterized) {
            for (JavaType arg : parameterized.getActualTypeArguments()) {
                if (containsJpaEntity(arg)) {
                    return true;
                }
            }
        }
        return false;
    }
}
