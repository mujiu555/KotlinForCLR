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

import compiler.EnvironmentConfigFiles
import compiler.clr.CLRConfigurationKeys
import compiler.clr.ClrAssemblyFileFinderFactory
import compiler.clr.ClrMetadataFinderFactory
import compiler.clr.clrDllRoots
import compiler.clr.frontend.*
import compiler.clr.frontend.KotlinCoreEnvironment.Companion.configureProjectEnvironment
import kotlinx.coroutines.*
import org.jetbrains.kotlin.cli.common.*
import org.jetbrains.kotlin.cli.common.config.KotlinSourceRoot
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.JvmPackagePartProvider
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCliJavaFileManagerImpl
import org.jetbrains.kotlin.cli.jvm.compiler.setupHighestLanguageLevel
import org.jetbrains.kotlin.cli.jvm.compiler.setupIdeaStandaloneExecution
import org.jetbrains.kotlin.cli.jvm.index.JavaRoot
import org.jetbrains.kotlin.cli.jvm.index.JvmDependenciesDynamicCompoundIndex
import org.jetbrains.kotlin.cli.jvm.index.JvmDependenciesIndexImpl
import org.jetbrains.kotlin.cli.jvm.index.SingleJavaFileRootsIndex
import org.jetbrains.kotlin.cli.pipeline.CheckCompilationErrors
import org.jetbrains.kotlin.cli.pipeline.ConfigurationPipelineArtifact
import org.jetbrains.kotlin.cli.pipeline.PerformanceNotifications
import org.jetbrains.kotlin.cli.pipeline.PipelinePhase
import org.jetbrains.kotlin.com.intellij.core.CoreJavaFileManager
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import org.jetbrains.kotlin.com.intellij.openapi.vfs.StandardFileSystems
import org.jetbrains.kotlin.com.intellij.openapi.vfs.VirtualFileManager
import org.jetbrains.kotlin.com.intellij.openapi.vfs.VirtualFileSystem
import org.jetbrains.kotlin.com.intellij.psi.PsiManager
import org.jetbrains.kotlin.com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.fir.DependencyListForCliModule
import org.jetbrains.kotlin.fir.declarations.builder.buildImport
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.fir.pipeline.FirResult
import org.jetbrains.kotlin.fir.pipeline.buildFirViaLightTree
import org.jetbrains.kotlin.fir.pipeline.resolveAndCheckFir
import org.jetbrains.kotlin.fir.renderer.FirRenderer
import org.jetbrains.kotlin.fir.session.environment.AbstractProjectFileSearchScope
import org.jetbrains.kotlin.load.kotlin.MetadataFinderFactory
import org.jetbrains.kotlin.load.kotlin.PackagePartProvider
import org.jetbrains.kotlin.load.kotlin.VirtualFileFinderFactory
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import java.io.File

object Frontend : PipelinePhase<ConfigurationPipelineArtifact, ClrFrontendPipelineArtifact>(
	name = "ClrFrontendPipelinePhase",
	postActions = setOf(PerformanceNotifications.AnalysisFinished, CheckCompilationErrors.CheckDiagnosticCollector)
) {
	override fun executePhase(input: ConfigurationPipelineArtifact): ClrFrontendPipelineArtifact? {
		val (configuration, diagnosticsCollector, rootDisposable) = input
		val collector = configuration.messageCollector

		val chunk = configuration.moduleChunk!!
		val moduleName = when {
			chunk.modules.size > 1 -> chunk.modules.joinToString(separator = "+") { it.getModuleName() }
			else -> configuration.moduleName!!
		}
		val (libraryList, assemblies) = createLibraryListForClr(moduleName, configuration)

		val (environment, sourcesProvider) = createEnvironmentAndSources(
			configuration,
			rootDisposable,
			assemblies
		) ?: return null
		val sources = sourcesProvider()
		val allSources = sources.allFiles

		if (allSources.isEmpty()) {
			collector.report(CompilerMessageSeverity.ERROR, "No source files")
			return null
		}

		val sessionsWithSources = prepareClrSessions(
			files = allSources,
			rootModuleName = Name.special("<$moduleName>"),
			configuration = configuration,
			projectEnvironment = environment,
			librariesScope = environment.getSearchScopeForProjectLibraries(),
			libraryList = libraryList,
			assemblies = assemblies,
			isCommonSource = sources.isCommonSourceForLt,
			fileBelongsToModule = sources.fileBelongsToModuleForLt
		)

		val outputs = sessionsWithSources.map { (session, sources) ->
			val rawFirFiles = session.buildFirViaLightTree(sources, diagnosticsCollector, null)
			rawFirFiles.forEach {
				listOf(
					"kotlin",
					"kotlin.annotation",
					"kotlin.collections",
					"kotlin.comparisons",
					"kotlin.io",
					"kotlin.ranges",
					"kotlin.sequences",
					"kotlin.text",
					"kotlin.clr",
				).forEach { pack ->
					if (
						!it.imports.any { import ->
							import.importedFqName?.asString() == pack
						}
					) {
						(it.imports as MutableList) += buildImport {
							importedFqName = FqName(pack)
							isAllUnder = true
						}
					}
				}
			}
			File(input.configuration.get(CLRConfigurationKeys.OUTPUT_DIRECTORY)!!, "Raw Front IR.txt").printWriter()
				.use { writer ->
					rawFirFiles.forEach { fir ->
						writer.println(FirRenderer().renderElementAsString(fir))
					}
				}
			resolveAndCheckFir(session, rawFirFiles, diagnosticsCollector)
		}

		val firResult = FirResult(outputs)
		File(input.configuration.get(CLRConfigurationKeys.OUTPUT_DIRECTORY)!!, "Front IR.txt").printWriter()
			.use { writer ->
				firResult.outputs.forEach { output ->
					output.fir.forEach { fir ->
						writer.println(FirRenderer().renderElementAsString(fir))
					}
				}
			}

		return ClrFrontendPipelineArtifact(firResult, configuration, environment, diagnosticsCollector, allSources)
	}

	fun createLibraryListForClr(
		moduleName: String,
		configuration: CompilerConfiguration,
	): Pair<DependencyListForCliModule, Map<String, NodeAssembly>> {
		// 收集所有DLL路径
		val dllPaths = configuration.clrDllRoots

		// 使用AssemblyResolver解析DLL
		val assemblies = runBlocking {
			val scope = CoroutineScope(Dispatchers.IO)
			dllPaths
				.map { it.absolutePath }
				.map {
					scope.async {
						resolveAssembly(
							dotnetHome = configuration.get(CLRConfigurationKeys.DOTNET_HOME)?.absolutePath,
							programPath = configuration.get(CLRConfigurationKeys.ASSEMBLY_RESOLVER)!!.absolutePath,
							assemblies = dllPaths.map(File::getAbsolutePath),
							assembly = it
						)
					}
				}
				.awaitAll()
				.associateBy { it.name }
		}

		val libraryList = DependencyListForCliModule.build(Name.identifier(moduleName)) {
			dependencies(dllPaths.map { it.absolutePath })
		}

		return libraryList to assemblies
	}

	private fun createEnvironmentAndSources(
		configuration: CompilerConfiguration,
		rootDisposable: Disposable,
		assemblies: Map<String, NodeAssembly>,
	): EnvironmentAndSources? {
		val messageCollector = configuration.messageCollector
		val environment = createProjectEnvironment(
			configuration,
			rootDisposable,
			EnvironmentConfigFiles.CLR_CONFIG_FILES,
			messageCollector,
			assemblies
		)
		val sources = { collectSources(configuration, environment.project, messageCollector) }
		return EnvironmentAndSources(environment, sources).takeUnless { messageCollector.hasErrors() }
	}

	fun createProjectEnvironment(
		configuration: CompilerConfiguration,
		parentDisposable: Disposable,
		configFiles: EnvironmentConfigFiles,
		messageCollector: MessageCollector,
		assemblies: Map<String, NodeAssembly>,
	): VfsBasedProjectEnvironment {
		setupIdeaStandaloneExecution()
		val appEnv =
			KotlinCoreEnvironment.getOrCreateApplicationEnvironmentForProduction(parentDisposable, configuration)
		val projectEnvironment = KotlinCoreEnvironment.ProjectEnvironment(parentDisposable, appEnv, configuration)

		projectEnvironment.configureProjectEnvironment(configuration, configFiles)

		val project = projectEnvironment.project
		val localFileSystem = VirtualFileManager.getInstance().getFileSystem(StandardFileSystems.FILE_PROTOCOL)

		val javaFileManager = project.getService(CoreJavaFileManager::class.java) as KotlinCliJavaFileManagerImpl

		val outputDirectory = configuration.get(CLRConfigurationKeys.OUTPUT_DIRECTORY)?.absolutePath

		val contentRoots = configuration.getList(CLIConfigurationKeys.CONTENT_ROOTS)

		val dllRootsResolver = DllRootsResolver(
			PsiManager.getInstance(project),
			messageCollector,
			{ null },
			outputDirectory?.let { localFileSystem.findFileByPath(it) },
			contentRoots.any { it is KotlinSourceRoot },
		)

		val (initialRoots, _) = dllRootsResolver.convertAssemblyRoots(contentRoots)

		val (roots, singleCSharpFileRoots) =
			initialRoots.partition { (file) -> file.isDirectory || file.extension != "cs" }

		// 创建依赖索引
		val rootsIndex = JvmDependenciesDynamicCompoundIndex(shouldOnlyFindFirstClass = true).apply {
			addIndex(JvmDependenciesIndexImpl(roots, shouldOnlyFindFirstClass = true))
			indexedRoots.forEach {
				projectEnvironment.addSourcesToClasspath(it.file)
			}
		}

		// 注册CLR程序集和元数据查找器
		val fileFinderFactory = ClrAssemblyFileFinderFactory(assemblies)
		project.registerService(VirtualFileFinderFactory::class.java, fileFinderFactory)
		project.registerService(MetadataFinderFactory::class.java, ClrMetadataFinderFactory(fileFinderFactory))

		project.setupHighestLanguageLevel()

		return ProjectEnvironmentWithCoreEnvironmentEmulation(
			project,
			localFileSystem,
			{ JvmPackagePartProvider(configuration.languageVersionSettings, it) },
			initialRoots, configuration
		).also {
			javaFileManager.initialize(
				rootsIndex,
				it.packagePartProviders,
				SingleJavaFileRootsIndex(singleCSharpFileRoots),
				configuration.getBoolean(JVMConfigurationKeys.USE_PSI_CLASS_FILES_READING),
				null
			)
		}
	}

	private class ProjectEnvironmentWithCoreEnvironmentEmulation(
		project: Project,
		localFileSystem: VirtualFileSystem,
		getPackagePartProviderFn: (GlobalSearchScope) -> PackagePartProvider,
		val initialRoots: List<JavaRoot>,
		val configuration: CompilerConfiguration,
	) : VfsBasedProjectEnvironment(project, localFileSystem, getPackagePartProviderFn) {
		val packagePartProviders = mutableListOf<JvmPackagePartProvider>()

		override fun getPackagePartProvider(fileSearchScope: AbstractProjectFileSearchScope): PackagePartProvider {
			return super.getPackagePartProvider(fileSearchScope).also {
				(it as? JvmPackagePartProvider)?.run {
					addRoots(initialRoots, configuration.getNotNull(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY))
					packagePartProviders += this
				}
			}
		}
	}

	fun <F> prepareClrSessions(
		files: List<F>,
		rootModuleName: Name,
		configuration: CompilerConfiguration,
		projectEnvironment: VfsBasedProjectEnvironment,
		librariesScope: AbstractProjectFileSearchScope,
		libraryList: DependencyListForCliModule,
		assemblies: Map<String, NodeAssembly>,
		isCommonSource: (F) -> Boolean,
		fileBelongsToModule: (F, String) -> Boolean,
	): List<SessionWithSources<F>> {
		val extensionRegistrars = FirExtensionRegistrar.getInstances(projectEnvironment.project)
		return SessionConstructionUtils.prepareSessions(
			files = files,
			configuration = configuration,
			rootModuleName = rootModuleName,
			targetPlatform = ClrPlatforms.unspecifiedClrPlatform,
			metadataCompilationMode = false,
			libraryList = libraryList,
			isCommonSource = isCommonSource,
			isScript = { false },
			fileBelongsToModule = fileBelongsToModule,
			createSharedLibrarySession = { sessionProvider ->
				FirClrSessionFactory.createSharedLibrarySession(
					rootModuleName,
					sessionProvider,
					projectEnvironment,
					extensionRegistrars,
					librariesScope,
					configuration.languageVersionSettings,
					assemblies,
				)
			},
			createLibrarySession = { sessionProvider, sharedLibrarySession ->
				FirClrSessionFactory.createLibrarySession(
					sessionProvider,
					sharedLibrarySession,
					libraryList.moduleDataProvider,
					projectEnvironment,
					extensionRegistrars,
					librariesScope,
					configuration.languageVersionSettings,
					assemblies,
				)
			},
			createSourceSession = { moduleFiles, moduleData, sessionProvider, sessionConfigurator ->
				FirClrSessionFactory.createSourceSession(
					moduleData,
					sessionProvider,
					projectEnvironment,
					extensionRegistrars,
					configuration,
					assemblies,
					sessionConfigurator,
				)
			}
		)
	}

	private data class EnvironmentAndSources(
		val environment: VfsBasedProjectEnvironment,
		val sources: () -> GroupedKtSources,
	)
}