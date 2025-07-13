/*
   Copyright 2025 Nyayurin

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */

package compiler.clr.backend

import compiler.clr.backend.lower.*
import org.jetbrains.kotlin.backend.common.lower.ConstEvaluationLowering
import org.jetbrains.kotlin.backend.common.phaser.PerformByIrFilePhase
import org.jetbrains.kotlin.backend.common.phaser.createFilePhases
import org.jetbrains.kotlin.backend.common.phaser.createModulePhases

private val clrFilePhases = createFilePhases(
	::MainMethodGenerationLowering,
	::ClrBuiltInsLowering,
	::ClrDefaultConstructorLowering
)

val clrLoweringPhases = createModulePhases(
	::ExternalPackageParentPatcherLowering,
	::ClrExpectDeclarationRemover,
	::ConstEvaluationLowering,
	::FileClassLowering
) + PerformByIrFilePhase(clrFilePhases)