package com.eskerra.go.architecture

import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.core.importer.Location
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.junit.ArchUnitRunner
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.freeze.FreezingArchRule
import org.junit.runner.RunWith

/**
 * Excludes Android unit-test and instrumentation-test compiled outputs so the
 * layer rules only analyze production classes.
 */
class ExcludeAndroidTestClasses : ImportOption {
    override fun includes(location: Location): Boolean = !location.contains("UnitTest") &&
        !location.contains("AndroidTest") &&
        !location.contains("/test/")
}

/**
 * Turns the prose layering rules in AGENTS.md / project-conventions.mdc into
 * enforced unit tests. Runs inside :app:testDebugUnitTest, so it is part of the
 * standard quality gate and CI — no separate mechanism.
 *
 * Rules verified clean against the codebase land active. The "UI must not touch
 * java.io/java.nio file APIs" rule has pre-existing violations (mostly image
 * loading), so it is frozen: the committed violation store may only shrink, new
 * violations fail the build. See specs/rules/change-safety.md (once it lands) for
 * the ratchet philosophy shared with the module budgets.
 */
@RunWith(ArchUnitRunner::class)
@AnalyzeClasses(
    packages = ["com.eskerra.go"],
    // Analyze production classes only. ImportOption.DoNotIncludeTests misses the
    // Android unit-test output dirs (…/debugUnitTest/…, …/AndroidTest/…), so the
    // layer rules would otherwise flag test code that legitimately drives JGit etc.
    importOptions = [ExcludeAndroidTestClasses::class]
)
class ArchitectureLayerRulesTest {

    @ArchTest
    val jgitIsAccessedOnlyFromDataGit: ArchRule =
        noClasses()
            .that().resideOutsideOfPackage("..data.git..")
            .should().dependOnClassesThat().resideInAnyPackage("org.eclipse.jgit..")
            .because("JGit access lives only in data/git; the rest goes through repositories")

    @ArchTest
    val uiDoesNotDependOnDataGit: ArchRule =
        noClasses()
            .that().resideInAnyPackage("..feature..", "..ui..")
            .should().dependOnClassesThat().resideInAnyPackage("..data.git..")
            .because("UI code must not call Git directly; it depends on repositories/use cases")

    @ArchTest
    val viewModelsDoNotDependOnAndroidContext: ArchRule =
        noClasses()
            .that().areAssignableTo("androidx.lifecycle.ViewModel")
            .should().dependOnClassesThat().haveFullyQualifiedName("android.content.Context")
            .because("ViewModels depend on repositories/use cases, not Android Context")

    @ArchTest
    val coreStaysBelowUiAndApp: ArchRule =
        noClasses()
            .that().resideInAPackage("..core..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..feature..", "..ui..", "..app..")
            .because(
                "core holds domain + parsing (markdown, wiki-links); UI depends inward on core, " +
                    "never the reverse, so parsing/resolution stays out of the UI layer"
            )

    @ArchTest
    val uiDoesNotTouchFileApis: ArchRule =
        FreezingArchRule.freeze(
            noClasses()
                .that().resideInAnyPackage("..feature..", "..ui..")
                .should().dependOnClassesThat(
                    JavaClass.Predicates.resideInAnyPackage("java.nio.file..")
                        .or(JavaClass.Predicates.equivalentTo(java.io.File::class.java))
                )
                .because("UI code must not read files directly; file access lives in data/")
        )
}
