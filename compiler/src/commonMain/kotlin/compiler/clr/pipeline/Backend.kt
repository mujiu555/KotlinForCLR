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

package compiler.clr.pipeline

import compiler.clr.CLRConfigurationKeys
import compiler.clr.applyModuleProperties
import compiler.clr.backend.CSharpCodegenFactory
import compiler.clr.backend.KotlinToCSharpCompiler
import compiler.clr.backend.KotlinToCSharpCompiler.toBackendInput
import org.jetbrains.kotlin.cli.common.buildFile
import org.jetbrains.kotlin.cli.common.moduleChunk
import org.jetbrains.kotlin.cli.pipeline.CheckCompilationErrors
import org.jetbrains.kotlin.cli.pipeline.PipelinePhase
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.ir.declarations.impl.IrModuleFragmentImpl
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.modules.Module
import java.io.File
import kotlin.collections.setOf

object Backend : PipelinePhase<ClrFir2IrPipelineArtifact, ClrBinaryPipelineArtifact>(
	name = "ClrBackendPipelineStep",
	postActions = setOf(CheckCompilationErrors.CheckDiagnosticCollector)
) {
	override fun executePhase(input: ClrFir2IrPipelineArtifact): ClrBinaryPipelineArtifact? {
		val (fir2IrResult, configuration, environment, diagnosticCollector, _) = input
		val project = environment.project
		val chunk = configuration.moduleChunk!!.modules
		val moduleDescriptor = fir2IrResult.irModuleFragment.descriptor

		val baseBackendInput = fir2IrResult.toBackendInput(
			configuration = configuration
		)
		val codegenFactory = CSharpCodegenFactory()

		val codegenInputs = chunk.map { module ->
			val config = when {
				chunk.size == 1 -> configuration
				else -> configuration.createConfigurationForModule(module, configuration.buildFile)
			}

			val backendInput = when (chunk.size) {
				1 -> baseBackendInput
				else -> {
					val wholeModule = baseBackendInput.irModuleFragment
					val moduleCopy = IrModuleFragmentImpl(wholeModule.descriptor)
					wholeModule.files.filterTo(moduleCopy.files) { file ->
						file.fileEntry.name in module.getSourceFiles()
					}
					baseBackendInput.copy(irModuleFragment = moduleCopy)
				}
			}

			KotlinToCSharpCompiler.runLowerings(
				project = project,
				configuration = config,
				moduleDescriptor = moduleDescriptor,
				module = module,
				codegenFactory = codegenFactory,
				backendInput = backendInput,
				diagnosticsReporter = diagnosticCollector
			)
		}

		File(input.configuration.get(CLRConfigurationKeys.OUTPUT_DIRECTORY)!!, "CLR IR.txt").printWriter()
			.use { writer ->
				codegenInputs.forEach {
					writer.println(it.module.dump())
				}
			}

		val outputs = codegenInputs.map {
			KotlinToCSharpCompiler.runCodegen(
				codegenInput = it,
				codegenFactory = codegenFactory,
				diagnosticsReporter = diagnosticCollector,
				configuration = it.state.configuration
			)
			it.state
		}

		return ClrBinaryPipelineArtifact(outputs)
	}

	private fun CompilerConfiguration.createConfigurationForModule(
		module: Module,
		buildFile: File?,
	): CompilerConfiguration {
		return copy().apply {
			applyModuleProperties(module, buildFile)
		}
	}
}