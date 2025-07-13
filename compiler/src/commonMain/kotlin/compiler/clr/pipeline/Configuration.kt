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

import compiler.clr.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.jetbrains.kotlin.cli.common.*
import org.jetbrains.kotlin.cli.common.config.addKotlinSourceRoot
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity.*
import org.jetbrains.kotlin.cli.common.modules.ModuleChunk
import org.jetbrains.kotlin.cli.pipeline.AbstractConfigurationPhase
import org.jetbrains.kotlin.cli.pipeline.ArgumentsPipelineArtifact
import org.jetbrains.kotlin.cli.pipeline.CheckCompilationErrors
import org.jetbrains.kotlin.cli.pipeline.ConfigurationUpdater
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.metadata.deserialization.MetadataVersion
import org.jetbrains.kotlin.modules.Module
import org.jetbrains.kotlin.utils.KotlinPaths
import org.jetbrains.kotlin.utils.PathUtil
import java.io.File

object Configuration : AbstractConfigurationPhase<CLRCompilerArguments>(
	name = "ClrConfigurationPipelinePhase",
	postActions = setOf(CheckCompilationErrors.CheckMessageCollector),
	configurationUpdaters = listOf(Updater)
) {
	override fun createMetadataVersion(versionArray: IntArray) = MetadataVersion(*versionArray)
	override fun provideCustomScriptingPluginOptions(arguments: CLRCompilerArguments) = emptyList<String>()
}

private object Updater : ConfigurationUpdater<CLRCompilerArguments>() {
	private val json = Json { ignoreUnknownKeys = true }

	override fun fillConfiguration(
		input: ArgumentsPipelineArtifact<CLRCompilerArguments>,
		configuration: CompilerConfiguration,
	) {
		val (arguments, _, _, _, _) = input
		val collector = configuration.messageCollector
		collector.report(LOGGING, "Configuring the compilation environment")

		File(arguments.destination!!).run {
			delete()
			mkdirs()
		}

		if (!configuration.configureDotnet(arguments)) return
		configuration.moduleName = "main"
		configuration.configureStandardLibs(configuration.kotlinPaths, arguments)
		configuration.configureAssemblyResolver(arguments)
		configuration.setupModuleChunk(arguments)
		configuration.configureDotnetAssemblyRoots()
	}

	private fun CompilerConfiguration.configureDotnet(arguments: CLRCompilerArguments): Boolean {
		if (arguments.noDotnet) {
			put(CLRConfigurationKeys.NO_DOTNET, true)

			if (arguments.dotnetHome != null) {
				messageCollector.report(
					STRONG_WARNING,
					"The '-sdk-home' option is ignored because '-no-sdk' is specified"
				)
			}
			return true
		}

		if (arguments.dotnetHome != null) {
			val dotnetHome = File(arguments.dotnetHome!!)
			if (!dotnetHome.exists() || !dotnetHome.isDirectory) {
				messageCollector.report(ERROR, "Dotnet home directory does not exist or is invalid: $dotnetHome")
				return false
			}
			val dotnetVersion = arguments.dotnetVersion
			if (dotnetVersion == null) {
				messageCollector.report(ERROR, "Dotnet version is not set")
				return false
			}
			messageCollector.report(LOGGING, "Using Dotnet home directory: $dotnetHome")
			put(CLRConfigurationKeys.DOTNET_HOME, dotnetHome)
			put(CLRConfigurationKeys.DOTNET_VERSION, dotnetVersion)
			return true
		}

		return true
	}

	private fun CompilerConfiguration.configureStandardLibs(paths: KotlinPaths?, arguments: CLRCompilerArguments) {
		fun addRoot(
			moduleName: String,
			libraryName: String,
			getLibrary: (KotlinPaths) -> File,
			noLibraryArgument: String,
		) {
			val file = getLibraryFromHome(paths, getLibrary, libraryName, messageCollector, noLibraryArgument)
			when {
				file == null -> {}
				else -> add(CLIConfigurationKeys.CONTENT_ROOTS, ClrDllRoot(file))
			}
		}

		if (!arguments.noStdlib) {
			addRoot(
				"kotlin.stdlib",
				PathUtil.KOTLIN_JAVA_STDLIB_NAME + ".dll",
				{ it.libPath.resolve(PathUtil.KOTLIN_JAVA_STDLIB_NAME + ".dll") },
				"'-no-stdlib'"
			)
		}
	}

	private fun CompilerConfiguration.configureAssemblyResolver(arguments: CLRCompilerArguments) {
		put(CLRConfigurationKeys.ASSEMBLY_RESOLVER, File(arguments.kotlinHome!!, "resolver/AssemblyResolver.dll"))
	}

	private fun CompilerConfiguration.setupModuleChunk(arguments: CLRCompilerArguments) {
		val moduleChunk = configureModuleChunk(arguments)
		this.moduleChunk = moduleChunk
		configureSourceRoots(moduleChunk.modules)
	}

	private fun CompilerConfiguration.configureModuleChunk(arguments: CLRCompilerArguments): ModuleChunk {
		val destination = arguments.destination?.let { File(it) }

		if (destination != null) {
			put(CLRConfigurationKeys.OUTPUT_DIRECTORY, destination)
		}

		val module = ClrModuleBuilder(
			name = this[CommonConfigurationKeys.MODULE_NAME] ?: "main",
			outputDir = destination?.path ?: ".",
			type = "csharp-production"
		)
		module.configureFromArgs(arguments)

		return ModuleChunk(listOf(module))
	}

	private fun ClrModuleBuilder.configureFromArgs(args: CLRCompilerArguments) {
		args.assembly?.split(File.pathSeparator)?.forEach { addAssemblyEntry(it) }

		val commonSources = args.commonSources?.toSet().orEmpty()
		// With `-script` flag, the first free arg is considered as a path to the script file and others are as script arguments
		if (args.script) return
		for (arg in args.freeArgs) {
			if (arg.endsWith(".cs")) {
				addCSharpSourceRoot(CSharpRootPath(arg))
			} else {
				addSourceFiles(arg)
				if (arg in commonSources) {
					addCommonSourceFiles(arg)
				}

				if (File(arg).isDirectory) {
					addCSharpSourceRoot(CSharpRootPath(arg))
				}
			}
		}
	}

	private fun CompilerConfiguration.configureSourceRoots(chunk: List<Module>) {
		val chunk = chunk.map { it as ClrModule }
		val hmppCliModuleStructure = get(CommonConfigurationKeys.HMPP_MODULE_STRUCTURE)
		for (module in chunk) {
			val commonSources = module.getCommonSourceFiles().toSet()

			for (path in module.getSourceFiles()) {
				addKotlinSourceRoot(
					path,
					isCommon = hmppCliModuleStructure?.isFromCommonModule(path) ?: (path in commonSources),
					hmppCliModuleStructure?.getModuleNameForSource(path)
				)
			}
		}

		for (module in chunk) {
			for ((path, packagePrefix) in module.getCSharpSourceRoots()) {
				add(CLIConfigurationKeys.CONTENT_ROOTS, CSharpSourceRoot(File(path), packagePrefix))
			}
		}

		for (module in chunk) {
			for (classpathRoot in module.getAssemblyRoots()) {
				add(CLIConfigurationKeys.CONTENT_ROOTS, ClrDllRoot(File(classpathRoot)))
			}
		}
	}

	@OptIn(ExperimentalSerializationApi::class)
	private fun CompilerConfiguration.configureDotnetAssemblyRoots() {
		if (get(CLRConfigurationKeys.NO_DOTNET) == true) return
		val dotnetHome = get(CLRConfigurationKeys.DOTNET_HOME) ?: error("Dotnet home is not set")
		val dotnetVersion = get(CLRConfigurationKeys.DOTNET_VERSION) ?: error("Dotnet home is not set")
		val dotnetRoots = File(dotnetHome, "shared/Microsoft.NETCore.App/$dotnetVersion")
		val deps = File(dotnetRoots, "Microsoft.NETCore.App.deps.json").inputStream().use { stream ->
			json.decodeFromStream<Deps>(stream)
		}
		val dllRoots = deps.toList().map { File(dotnetRoots, it) }

		addAll(
			CLIConfigurationKeys.CONTENT_ROOTS,
			0,
			dllRoots.map { file -> ClrDllRoot(file, true) }
		)
	}
}

@Serializable
private data class Deps(
	val targets: Map<String, Map<String, Target>>,
) {
	@Serializable
	data class Target(
		val runtime: Map<String, Unit>,
	)

	fun toList(): List<String> = targets.values
		.flatMap { it.values }
		.flatMap { it.runtime.keys }
}