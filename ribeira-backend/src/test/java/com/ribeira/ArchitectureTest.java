package com.ribeira;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.Repository;

/** Module boundaries of the modular monolith (ADR-0001, ADR-0007). */
class ArchitectureTest {

  private static JavaClasses classes;

  @BeforeAll
  static void importClasses() {
    classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.ribeira");
  }

  @Test
  void modulesHaveNoCyclicDependencies() {
    slices().matching("com.ribeira.(*)..").should().beFreeOfCycles().check(classes);
  }

  @Test
  void sharedKernelDoesNotDependOnBusinessModules() {
    noClasses()
        .that()
        .resideInAPackage("com.ribeira.shared..")
        .should()
        .dependOnClassesThat(
            resideInAPackage("com.ribeira..").and(resideOutsideOfPackage("com.ribeira.shared..")))
        .check(classes);
  }

  @Test
  void controllersDoNotUseRepositories() {
    noClasses()
        .that()
        .resideInAPackage("..api..")
        .should()
        .dependOnClassesThat()
        .areAssignableTo(Repository.class)
        .check(classes);
  }

  @Test
  void repositoriesAreOnlyUsedInsideTheirModule() {
    ArchRuleDefinition.classes().should(onlyUseRepositoriesOfTheirOwnModule()).check(classes);
  }

  private static ArchCondition<JavaClass> onlyUseRepositoriesOfTheirOwnModule() {
    return new ArchCondition<>("only use repositories of their own module") {
      @Override
      public void check(JavaClass item, ConditionEvents events) {
        String module = moduleOf(item);
        item.getDirectDependenciesFromSelf().stream()
            .map(d -> d.getTargetClass())
            .filter(target -> target.isAssignableTo(Repository.class))
            .filter(target -> target.getPackageName().startsWith("com.ribeira"))
            .filter(target -> !moduleOf(target).equals(module))
            .forEach(
                target ->
                    events.add(
                        SimpleConditionEvent.violated(
                            item, item.getName() + " uses repository " + target.getName())));
      }
    };
  }

  private static String moduleOf(JavaClass javaClass) {
    String[] parts = javaClass.getPackageName().split("\\.");
    return parts.length > 2 ? parts[2] : "";
  }
}
