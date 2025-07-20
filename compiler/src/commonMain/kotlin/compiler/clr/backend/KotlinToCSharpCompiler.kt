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

import compiler.clr.CLRConfigurationKeys
import compiler.clr.backend.codegen.clean
import compiler.clr.backend.codegen.render
import compiler.clr.backend.codegen.visit
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.fir.FirDiagnosticsCompilerResultsReporter
import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.moduleName
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.diagnostics.impl.BaseDiagnosticsCollector
import org.jetbrains.kotlin.fir.pipeline.Fir2IrActualizedResult
import org.jetbrains.kotlin.ir.declarations.name
import org.jetbrains.kotlin.modules.Module
import java.io.File
import java.io.FileWriter

object KotlinToCSharpCompiler {
	internal fun Fir2IrActualizedResult.toBackendInput(
		configuration: CompilerConfiguration
	): CSharpCodegenFactory.BackendInput {
		return CSharpCodegenFactory.BackendInput(
			irModuleFragment,
			irBuiltIns,
			symbolTable,
			components.irProviders,
			ClrGeneratorExtensionsImpl(configuration)
		)
	}

	internal fun runLowerings(
		project: Project,
		configuration: CompilerConfiguration,
		moduleDescriptor: ModuleDescriptor,
		module: Module?,
		codegenFactory: CSharpCodegenFactory,
		backendInput: CSharpCodegenFactory.BackendInput,
		diagnosticsReporter: BaseDiagnosticsCollector
	): CSharpCodegenFactory.CodegenInput {
		val state = GenerationState(
			project,
			moduleDescriptor,
			configuration,
			moduleName = module?.getModuleName() ?: configuration.moduleName,
			diagnosticReporter = diagnosticsReporter,
		)

		return codegenFactory.invokeLowerings(state, backendInput)
	}

	internal fun runCodegen(
		codegenInput: CSharpCodegenFactory.CodegenInput,
		codegenFactory: CSharpCodegenFactory,
		diagnosticsReporter: BaseDiagnosticsCollector,
		configuration: CompilerConfiguration
	) {
		val map = codegenFactory.invokeCodegen(codegenInput)
		File(configuration.get(CLRConfigurationKeys.OUTPUT_DIRECTORY)!!, "BIR@Raw.xml").printWriter().use { writer ->
			map?.let { writer.println(it.render()) }
		}
		val cleanedMap = map?.mapValues { it.value.clean() }
		File(configuration.get(CLRConfigurationKeys.OUTPUT_DIRECTORY)!!, "BIR@Cleaned.xml").printWriter().use { writer ->
			cleanedMap?.let { writer.println(it.render()) }
		}
		FirDiagnosticsCompilerResultsReporter.reportToMessageCollector(
			diagnosticsReporter,
			configuration.getNotNull(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY),
			configuration.getBoolean(CLIConfigurationKeys.RENDER_DIAGNOSTIC_INTERNAL_NAME)
		)
		val destination = configuration.get(CLRConfigurationKeys.OUTPUT_DIRECTORY)!!
		cleanedMap?.let { map ->
			for ((irFile, node) in map) {
				FileWriter(File(destination, irFile.name + ".cs")).use {
					it.write(node.visit())
				}
			}
		}
	}
}