/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.test.frontend.fir.handlers

import org.jetbrains.kotlin.fir.AbstractFirDiagnosticsTest
import org.jetbrains.kotlin.test.frontend.fir.FirSourceArtifact
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.TestServices

class FirCfgConsistencyHandler(testServices: TestServices) : FirAnalysisHandler(testServices) {
    override fun processModule(module: TestModule, info: FirSourceArtifact) {
        info.firFiles.values.forEach { it.accept(AbstractFirDiagnosticsTest.CfgConsistencyChecker) }
    }

    override fun processAfterAllModules() {}
}
