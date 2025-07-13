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

import compiler.clr.backend.codegen.ClassCodegen
import compiler.clr.backend.codegen.CodeNode
import org.jetbrains.kotlin.backend.common.ir.isBytecodeGenerationSuppressed
import org.jetbrains.kotlin.config.phaseConfig
import org.jetbrains.kotlin.config.phaser.PhaseConfig
import org.jetbrains.kotlin.config.phaser.invokeToplevel
import org.jetbrains.kotlin.diagnostics.impl.BaseDiagnosticsCollector
import org.jetbrains.kotlin.ir.IrBuiltIns
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.linkage.IrProvider
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.ExternalDependenciesGenerator
import org.jetbrains.kotlin.ir.util.SymbolTable

class CSharpCodegenFactory {
	data class BackendInput(
		val irModuleFragment: IrModuleFragment,
		val irBuiltIns: IrBuiltIns,
		val symbolTable: SymbolTable,
		val irProviders: List<IrProvider>,
		val extensions: ClrGeneratorExtensions,
	)

	data class CodegenInput(
		val state: GenerationState,
		val context: ClrBackendContext,
		val module: IrModuleFragment,
		val allBuiltins: List<IrFile>,
	)

	fun invokeLowerings(state: GenerationState, input: BackendInput): CodegenInput {
		val (irModuleFragment, irBuiltIns, symbolTable, irProviders, extensions) = input
		val context = ClrBackendContext(
			state,
			irBuiltIns,
			symbolTable
		)

		ExternalDependenciesGenerator(symbolTable, irProviders).generateUnboundSymbolsAsDependencies()

		val allBuiltins = emptyList<IrFile>()

		clrLoweringPhases.invokeToplevel(state.configuration.phaseConfig ?: PhaseConfig(), context, irModuleFragment)

		return CodegenInput(state, context, irModuleFragment, allBuiltins)
	}

	fun invokeCodegen(input: CodegenInput): Map<IrFile, CodeNode>? {
		val (state, context, module, _) = input

		fun hasErrors() = (state.diagnosticReporter as? BaseDiagnosticsCollector)?.hasErrors == true

		if (hasErrors()) return null

		val map = buildMap {
			for (irFile in module.files) {
				put(irFile, generateFile(context, irFile))
			}
		}

		if (hasErrors()) return null

		return map
	}

	@OptIn(UnsafeDuringIrConstructionAPI::class)
	private fun generateFile(context: ClrBackendContext, file: IrFile) = ClassCodegen(context).run { file.visit() }
}