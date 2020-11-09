/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.generators.tests.generator

import junit.framework.TestCase
import org.jetbrains.kotlin.generators.tests.generator.InconsistencyChecker.Companion.hasDryRunArg
import org.jetbrains.kotlin.generators.tests.generator.InconsistencyChecker.Companion.inconsistencyChecker
import org.jetbrains.kotlin.generators.tests.generator.generators.impl.*
import org.jetbrains.kotlin.test.TargetBackend
import java.io.File
import java.util.*
import java.util.regex.Pattern

class TestGroup(
    private val testsRoot: String,
    val testDataRoot: String,
    val testRunnerMethodName: String,
    val additionalRunnerArguments: List<String> = emptyList(),
    val annotations: List<AnnotationModel> = emptyList(),
) {
    private val _testClasses: MutableList<TestClass> = mutableListOf()
    val testClasses: List<TestClass>
        get() = _testClasses

    inline fun <reified T : TestCase> testClass(
        suiteTestClassName: String = getDefaultSuiteTestClassName(T::class.java.simpleName),
        useJunit4: Boolean = false,
        annotations: List<AnnotationModel> = emptyList(),
        noinline init: TestClass.() -> Unit
    ) {
        testClass(T::class.java.name, suiteTestClassName, useJunit4, annotations, init)
    }

    fun testClass(
        baseTestClassName: String,
        suiteTestClassName: String = getDefaultSuiteTestClassName(baseTestClassName.substringAfterLast('.')),
        useJunit4: Boolean,
        annotations: List<AnnotationModel> = emptyList(),
        init: TestClass.() -> Unit
    ) {
        _testClasses += TestClass(baseTestClassName, suiteTestClassName, useJunit4, annotations).apply(init)
    }

    inner class TestClass(
        val baseTestClassName: String,
        val suiteTestClassName: String,
        val useJunit4: Boolean,
        val annotations: List<AnnotationModel>
    ) {
        val baseDir: String
            get() = this@TestGroup.testsRoot

        val testModels = ArrayList<TestClassModel>()

        fun model(
            relativeRootPath: String,
            recursive: Boolean = true,
            excludeParentDirs: Boolean = false,
            extension: String? = "kt", // null string means dir (name without dot)
            pattern: String = if (extension == null) """^([^\.]+)$""" else "^(.+)\\.$extension\$",
            excludedPattern: String? = null,
            testMethod: String = "doTest",
            singleClass: Boolean = false, // if true then tests from subdirectories will be flatten to single class
            testClassName: String? = null, // specific name for generated test class
            // which backend will be used in test. Specifying value may affect some test with
            // directives TARGET_BACKEND/DONT_TARGET_EXACT_BACKEND won't be generated
            targetBackend: TargetBackend = TargetBackend.ANY,
            excludeDirs: List<String> = listOf(),
            filenameStartsLowerCase: Boolean? = null, // assert that file is properly named
            skipIgnored: Boolean = false, // pretty meaningless flag, affects only few test names in one test runner
            deep: Int? = null, // specifies how deep recursive search will follow directory with testdata
            skipTestsForExperimentalCoroutines: Boolean = false
        ) {
            val rootFile = File("$testDataRoot/$relativeRootPath")
            val compiledPattern = Pattern.compile(pattern)
            val compiledExcludedPattern = excludedPattern?.let { Pattern.compile(it) }
            val className = testClassName ?: TestGeneratorUtil.fileNameToJavaIdentifier(rootFile)
            testModels.add(
                if (singleClass) {
                    if (excludeDirs.isNotEmpty()) error("excludeDirs is unsupported for SingleClassTestModel yet")
                    SingleClassTestModel(
                        rootFile, compiledPattern, compiledExcludedPattern, filenameStartsLowerCase, testMethod, className,
                        targetBackend, skipIgnored, testRunnerMethodName, additionalRunnerArguments, annotations
                    )
                } else {
                    SimpleTestClassModel(
                        rootFile, recursive, excludeParentDirs,
                        compiledPattern, compiledExcludedPattern, filenameStartsLowerCase, testMethod, className,
                        targetBackend, excludeDirs, skipIgnored, testRunnerMethodName, additionalRunnerArguments, deep, annotations,
                        skipTestsForExperimentalCoroutines
                    )
                }
            )
        }
    }
}

fun testGroupSuite(
    init: TestGroupSuite.() -> Unit
): TestGroupSuite {
    return TestGroupSuite().apply(init)
}

fun generateTestGroupSuite(
    args: Array<String>,
    init: TestGroupSuite.() -> Unit
) {
    generateTestGroupSuite(hasDryRunArg(args), init)
}

fun generateTestGroupSuite(
    dryRun: Boolean = false,
    init: TestGroupSuite.() -> Unit
) {
    val suite = testGroupSuite(init)
    for (testGroup in suite.testGroups) {
        for (testClass in testGroup.testClasses) {
            val (changed, testSourceFilePath) = TestGeneratorImpl.generateAndSave(testClass, dryRun)
            if (changed) {
                inconsistencyChecker(dryRun).add(testSourceFilePath)
            }
        }
    }
}

class TestGroupSuite {
    private val _testGroups = mutableListOf<TestGroup>()
    val testGroups: List<TestGroup>
        get() = _testGroups

    fun testGroup(
        testsRoot: String,
        testDataRoot: String,
        testRunnerMethodName: String = RunTestMethodModel.METHOD_NAME,
        additionalRunnerArguments: List<String> = emptyList(),
        init: TestGroup.() -> Unit
    ) {
        _testGroups += TestGroup(
            testsRoot,
            testDataRoot,
            testRunnerMethodName,
            additionalRunnerArguments,
        ).apply(init)
    }
}

interface InconsistencyChecker {
    fun add(affectedFile: String)

    val affectedFiles: List<String>

    companion object {
        fun hasDryRunArg(args: Array<String>) = args.any { it == "dryRun" }

        fun inconsistencyChecker(dryRun: Boolean) = if (dryRun) DefaultInconsistencyChecker else EmptyInconsistencyChecker
    }
}

object DefaultInconsistencyChecker : InconsistencyChecker {
    private val files = mutableListOf<String>()

    override fun add(affectedFile: String) {
        files.add(affectedFile)
    }

    override val affectedFiles: List<String>
        get() = files
}

object EmptyInconsistencyChecker : InconsistencyChecker {
    override fun add(affectedFile: String) {
    }

    override val affectedFiles: List<String>
        get() = emptyList()
}

fun getDefaultSuiteTestClassName(baseTestClassName: String): String {
    require(baseTestClassName.startsWith("Abstract")) { "Doesn't start with \"Abstract\": $baseTestClassName" }
    return baseTestClassName.substringAfter("Abstract") + "Generated"
}
