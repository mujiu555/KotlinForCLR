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

package compiler.clr.frontend

import compiler.EnvironmentConfigFiles
import compiler.clr.*
import compiler.clr.frontend.KotlinCoreEnvironment.Companion.resetApplicationManager
import org.jetbrains.kotlin.asJava.KotlinAsJavaSupport
import org.jetbrains.kotlin.asJava.LightClassGenerationSupport
import org.jetbrains.kotlin.asJava.finder.JavaElementFinder
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.CliModuleVisibilityManagerImpl
import org.jetbrains.kotlin.cli.common.CompilerSystemProperties
import org.jetbrains.kotlin.cli.common.config.ContentRoot
import org.jetbrains.kotlin.cli.common.config.KotlinSourceRoot
import org.jetbrains.kotlin.cli.common.config.kotlinSourceRoots
import org.jetbrains.kotlin.cli.common.environment.setIdeaIoUseFallback
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.toBooleanLenient
import org.jetbrains.kotlin.cli.jvm.compiler.*
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.config.JavaSourceRoot
import org.jetbrains.kotlin.cli.jvm.index.*
import org.jetbrains.kotlin.cli.jvm.modules.CliJavaModuleFinder
import org.jetbrains.kotlin.com.intellij.codeInsight.ExternalAnnotationsManager
import org.jetbrains.kotlin.com.intellij.codeInsight.InferredAnnotationsManager
import org.jetbrains.kotlin.com.intellij.core.CoreApplicationEnvironment
import org.jetbrains.kotlin.com.intellij.core.CoreJavaFileManager
import org.jetbrains.kotlin.com.intellij.core.JavaCoreProjectEnvironment
import org.jetbrains.kotlin.com.intellij.ide.highlighter.JavaFileType
import org.jetbrains.kotlin.com.intellij.lang.java.JavaParserDefinition
import org.jetbrains.kotlin.com.intellij.mock.MockProject
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.openapi.application.Application
import org.jetbrains.kotlin.com.intellij.openapi.application.ApplicationManager
import org.jetbrains.kotlin.com.intellij.openapi.extensions.ExtensionsArea
import org.jetbrains.kotlin.com.intellij.openapi.fileTypes.PlainTextFileType
import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.openapi.util.io.FileUtilRt
import org.jetbrains.kotlin.com.intellij.openapi.vfs.PersistentFSConstants
import org.jetbrains.kotlin.com.intellij.openapi.vfs.VfsUtilCore
import org.jetbrains.kotlin.com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.com.intellij.openapi.vfs.VirtualFileSystem
import org.jetbrains.kotlin.com.intellij.openapi.vfs.impl.ZipHandler
import org.jetbrains.kotlin.com.intellij.psi.PsiElementFinder
import org.jetbrains.kotlin.com.intellij.psi.PsiManager
import org.jetbrains.kotlin.com.intellij.psi.impl.PsiElementFinderImpl
import org.jetbrains.kotlin.com.intellij.psi.impl.PsiTreeChangePreprocessor
import org.jetbrains.kotlin.com.intellij.psi.impl.file.impl.JavaFileManager
import org.jetbrains.kotlin.com.intellij.util.lang.UrlClassLoader
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.extensions.CollectAdditionalSourcesExtension
import org.jetbrains.kotlin.extensions.CompilerConfigurationExtension
import org.jetbrains.kotlin.load.java.structure.impl.source.JavaElementSourceFactory
import org.jetbrains.kotlin.load.java.structure.impl.source.JavaFixedElementSourceFactory
import org.jetbrains.kotlin.load.kotlin.MetadataFinderFactory
import org.jetbrains.kotlin.load.kotlin.ModuleVisibilityManager
import org.jetbrains.kotlin.load.kotlin.VirtualFileFinderFactory
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.CodeAnalyzerInitializer
import org.jetbrains.kotlin.resolve.jvm.KotlinJavaPsiFacade
import org.jetbrains.kotlin.resolve.lazy.declarations.CliDeclarationProviderFactoryService
import org.jetbrains.kotlin.resolve.lazy.declarations.DeclarationProviderFactoryService
import org.jetbrains.kotlin.utils.PathUtil
import java.io.File
import java.nio.file.FileSystems
import java.util.zip.ZipFile

class KotlinCoreEnvironment private constructor(
	val projectEnvironment: ProjectEnvironment,
	val configuration: CompilerConfiguration,
	configFiles: EnvironmentConfigFiles
) {
	class ProjectEnvironment(
		disposable: Disposable,
		applicationEnvironment: KotlinCoreApplicationEnvironment,
		configuration: CompilerConfiguration
	) :
		KotlinCoreProjectEnvironment(disposable, applicationEnvironment) {

		val jarFileSystem: VirtualFileSystem

		init {
			val messageCollector = configuration.messageCollector

			setIdeaIoUseFallback()

			val useFastJarFSFlag: Boolean? = configuration.get(JVMConfigurationKeys.USE_FAST_JAR_FILE_SYSTEM)
			val useK2 =
				configuration.getBoolean(CommonConfigurationKeys.USE_FIR) || configuration.languageVersionSettings.languageVersion.usesK2

			when {
				useFastJarFSFlag == true && !useK2 -> {
					messageCollector.report(
						CompilerMessageSeverity.STRONG_WARNING,
						"Using new faster version of JAR FS: it should make your build faster, " +
								"but the new implementation is not thoroughly tested with language versions below 2.0"
					)
				}
				useFastJarFSFlag == false && useK2 -> {
					messageCollector.report(
						CompilerMessageSeverity.INFO,
						"Using outdated version of JAR FS: it might make your build slower"
					)
				}
			}

			// We enable FastJarFS by default since K2
			val useFastJarFS = useFastJarFSFlag ?: useK2

			jarFileSystem = when {
				configuration.getBoolean(JVMConfigurationKeys.USE_PSI_CLASS_FILES_READING) -> {
					applicationEnvironment.jarFileSystem
				}
				useFastJarFS -> {
					val fastJarFs = applicationEnvironment.fastJarFileSystem
					if (fastJarFs == null) {
						messageCollector.report(
							CompilerMessageSeverity.STRONG_WARNING,
							"Your JDK doesn't seem to support mapped buffer unmapping, so the slower (old) version of JAR FS will be used"
						)
						applicationEnvironment.jarFileSystem
					} else {
						val outputJar = configuration.get(JVMConfigurationKeys.OUTPUT_JAR)
						if (outputJar == null) {
							fastJarFs
						} else {
							val contentRoots = configuration.get(CLIConfigurationKeys.CONTENT_ROOTS)
							if (contentRoots?.any { it is ClrDllRoot && it.file.path == outputJar.path } == true) {
								// See KT-61883
								messageCollector.report(
									CompilerMessageSeverity.STRONG_WARNING,
									"JAR from the classpath ${outputJar.path} is reused as output JAR, so the slower (old) version of JAR FS will be used"
								)
								applicationEnvironment.jarFileSystem
							} else {
								fastJarFs
							}
						}
					}
				}

				else -> applicationEnvironment.jarFileSystem
			}
		}

		override fun preregisterServices() {
			registerProjectExtensionPoints(project.extensionArea)
		}

		override fun registerJavaPsiFacade() {
			with(project) {
				registerService(
					CoreJavaFileManager::class.java,
					this.getService(JavaFileManager::class.java) as CoreJavaFileManager
				)

				registerKotlinLightClassSupport(project)

				registerService(ExternalAnnotationsManager::class.java, MockExternalAnnotationsManager())
				registerService(InferredAnnotationsManager::class.java, MockInferredAnnotationsManager())
			}

			super.registerJavaPsiFacade()
		}
	}

	private val sourceFiles = mutableListOf<KtFile>()
	private val rootsIndex: JvmDependenciesDynamicCompoundIndex
	private val packagePartProviders = mutableListOf<JvmPackagePartProvider>()

	private val dllRootsResolver: DllRootsResolver
	private val initialRoots = ArrayList<JavaRoot>()

	init {
		projectEnvironment.configureProjectEnvironment(configuration, configFiles)
		val project = projectEnvironment.project
		project.registerService(
			DeclarationProviderFactoryService::class.java,
			CliDeclarationProviderFactoryService(sourceFiles)
		)

		sourceFiles += createSourceFilesFromSourceRoots(
			configuration, project,
			getSourceRootsCheckingForDuplicates(
				configuration,
				configuration[CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY]
			)
		)

		collectAdditionalSources(project)

		sourceFiles.sortBy { it.virtualFile.path }

		val javaFileManager = project.getService(CoreJavaFileManager::class.java) as KotlinCliJavaFileManagerImpl

		val messageCollector = configuration.get(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY)

		val jdkHome = configuration.get(JVMConfigurationKeys.JDK_HOME)
		val releaseTarget = configuration.get(JVMConfigurationKeys.JDK_RELEASE)
		val javaModuleFinder = CliJavaModuleFinder(
			jdkHome,
			messageCollector,
			javaFileManager,
			project,
			releaseTarget
		)

		val outputDirectory =
			configuration.get(JVMConfigurationKeys.MODULES)?.singleOrNull()?.getOutputDirectory()
				?: configuration.get(JVMConfigurationKeys.OUTPUT_DIRECTORY)?.absolutePath

		val contentRoots = configuration.getList(CLIConfigurationKeys.CONTENT_ROOTS)

		dllRootsResolver = DllRootsResolver(
			PsiManager.getInstance(project),
			messageCollector,
			this::contentRootToVirtualFile,
			outputDirectory?.let(this::findLocalFile),
			hasKotlinSources = contentRoots.any { it is KotlinSourceRoot },
		)

		val (initialRoots, javaModules) = dllRootsResolver.convertAssemblyRoots(contentRoots)
		this.initialRoots.addAll(initialRoots)

		val (roots, singleJavaFileRoots) =
			initialRoots.partition { (file) -> file.isDirectory || file.extension != "java" }

		// REPL and kapt2 update classpath dynamically
		rootsIndex = JvmDependenciesDynamicCompoundIndex(shouldOnlyFindFirstClass = true).apply {
			addIndex(JvmDependenciesIndexImpl(roots, shouldOnlyFindFirstClass = true))
			updateClasspathFromRootsIndex(this)
		}

		javaFileManager.initialize(
			rootsIndex,
			packagePartProviders,
			SingleJavaFileRootsIndex(singleJavaFileRoots),
			configuration.getBoolean(JVMConfigurationKeys.USE_PSI_CLASS_FILES_READING),
			null
		)

		val fileFinderFactory = CliVirtualFileFinderFactory(rootsIndex, releaseTarget != null, null)
		project.registerService(VirtualFileFinderFactory::class.java, fileFinderFactory)
		project.registerService(MetadataFinderFactory::class.java, CliMetadataFinderFactory(fileFinderFactory))

		project.putUserData(APPEND_JAVA_SOURCE_ROOTS_HANDLER_KEY, fun(roots: List<File>) {
			updateClasspath(roots.map { JavaSourceRoot(it, null) })
		})

		project.setupHighestLanguageLevel()
	}

	fun getSourceRootsCheckingForDuplicates(configuration: CompilerConfiguration, messageCollector: MessageCollector?): List<KotlinSourceRoot> {
		val uniqueSourceRoots = hashSetOf<String>()
		val result = mutableListOf<KotlinSourceRoot>()

		for (root in configuration.kotlinSourceRoots) {
			if (!uniqueSourceRoots.add(root.path)) {
				messageCollector?.report(CompilerMessageSeverity.STRONG_WARNING, "Duplicate source root: ${root.path}")
			}
			result.add(root)
		}

		return result
	}

	private fun collectAdditionalSources(project: MockProject) {
		var unprocessedSources: Collection<KtFile> = sourceFiles
		val processedSources = HashSet<KtFile>()
		val processedSourcesByExtension = HashMap<CollectAdditionalSourcesExtension, Collection<KtFile>>()
		// repeat feeding extensions with sources while new sources a being added
		var sourceCollectionIterations = 0
		while (unprocessedSources.isNotEmpty()) {
			if (sourceCollectionIterations++ > 10) { // TODO: consider using some appropriate global constant
				throw IllegalStateException("Unable to collect additional sources in reasonable number of iterations")
			}
			processedSources.addAll(unprocessedSources)
			val allNewSources = ArrayList<KtFile>()
			for (extension in CollectAdditionalSourcesExtension.Companion.getInstances(project)) {
				// do not feed the extension with the sources it returned on the previous iteration
				val sourcesToProcess = unprocessedSources - (processedSourcesByExtension[extension] ?: emptyList())
				val newSources = extension.collectAdditionalSourcesAndUpdateConfiguration(sourcesToProcess, configuration, project)
				if (newSources.isNotEmpty()) {
					allNewSources.addAll(newSources)
					processedSourcesByExtension[extension] = newSources
				}
			}
			unprocessedSources = allNewSources.filterNot { processedSources.contains(it) }.distinct()
			sourceFiles += unprocessedSources
		}
	}

	private val VirtualFile.javaFiles: List<VirtualFile>
		get() = mutableListOf<VirtualFile>().apply {
			VfsUtilCore.processFilesRecursively(this@javaFiles) { file ->
				if (file.extension == "java" || file.fileType == JavaFileType.INSTANCE) {
					add(file)
				}
				true
			}
		}

	private val applicationEnvironment: CoreApplicationEnvironment
		get() = projectEnvironment.environment

	val project: Project
		get() = projectEnvironment.project

	private fun updateClasspathFromRootsIndex(index: JvmDependenciesIndex) {
		index.indexedRoots.forEach {
			projectEnvironment.addSourcesToClasspath(it.file)
		}
	}

	fun updateClasspath(contentRoots: List<ContentRoot>): List<File>? {
		// TODO: add new Java modules to CliJavaModuleResolver
		val newRoots = dllRootsResolver.convertAssemblyRoots(contentRoots).roots - initialRoots

		if (packagePartProviders.isEmpty()) {
			initialRoots.addAll(newRoots)
		} else {
			for (packagePartProvider in packagePartProviders) {
				packagePartProvider.addRoots(newRoots, configuration.getNotNull(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY))
			}
		}

		configuration.addAll(
			CLIConfigurationKeys.CONTENT_ROOTS, contentRoots - configuration.getList(
				CLIConfigurationKeys.CONTENT_ROOTS))

		return rootsIndex.addNewIndexForRoots(newRoots)?.let { newIndex ->
			updateClasspathFromRootsIndex(newIndex)
			newIndex.indexedRoots.map { (file) ->
				VfsUtilCore.virtualToIoFile(VfsUtilCore.getVirtualFileForJar(file) ?: file)
			}.toList()
		}.orEmpty()
	}

	private fun contentRootToVirtualFile(root: ClrContentRootBase): VirtualFile? =
		when (root) {
			is ClrDllRoot ->
				if (root.file.isFile) findJarRoot(root.file) else findExistingRoot(root, "Classpath entry")
			is CSharpSourceRoot ->
				findExistingRoot(root, "Java source root")
			is VirtualClrDllRoot -> root.file
			else ->
				throw IllegalStateException("Unexpected root: $root")
		}

	fun findLocalFile(path: String): VirtualFile? =
		applicationEnvironment.localFileSystem.findFileByPath(path)

	private fun findExistingRoot(root: ClrContentRoot, rootDescription: String): VirtualFile? {
		return findLocalFile(root.file.absolutePath).also {
			if (it == null) {
				report(CompilerMessageSeverity.STRONG_WARNING, "$rootDescription points to a non-existent location: ${root.file}")
			}
		}
	}

	private fun findJarRoot(file: File): VirtualFile? =
		projectEnvironment.jarFileSystem.findFileByPath("$file!/")

	internal fun report(severity: CompilerMessageSeverity, message: String) = configuration.report(severity, message)

	companion object {
		@PublishedApi
		internal val APPLICATION_LOCK = Object()

		private var ourApplicationEnvironment: KotlinCoreApplicationEnvironment? = null
		private var ourProjectCount = 0

		fun getOrCreateApplicationEnvironmentForProduction(
			projectDisposable: Disposable,
			configuration: CompilerConfiguration,
		): KotlinCoreApplicationEnvironment = getOrCreateApplicationEnvironment(
			projectDisposable,
			configuration,
			KotlinCoreApplicationEnvironmentMode.Production,
		)

		fun getOrCreateApplicationEnvironment(
			projectDisposable: Disposable,
			configuration: CompilerConfiguration,
			environmentMode: KotlinCoreApplicationEnvironmentMode,
		): KotlinCoreApplicationEnvironment {
			synchronized(APPLICATION_LOCK) {
				if (ourApplicationEnvironment == null) {
					val disposable = Disposer.newDisposable("Disposable for the KotlinCoreApplicationEnvironment")
					ourApplicationEnvironment =
						createApplicationEnvironment(
							disposable,
							configuration,
							environmentMode,
						)
					ourProjectCount = 0
					Disposer.register(disposable) {
						synchronized(APPLICATION_LOCK) {
							ourApplicationEnvironment = null
						}
					}
				}
				try {
					val disposeAppEnv =
						CompilerSystemProperties.KOTLIN_COMPILER_ENVIRONMENT_KEEPALIVE_PROPERTY.value.toBooleanLenient() != true
					// Disposer uses identity of passed object to deduplicate registered disposables
					// We should everytime pass new instance to avoid un-registering from previous one
					@Suppress("ObjectLiteralToLambda")
					Disposer.register(projectDisposable, object : Disposable {
						override fun dispose() {
							synchronized(APPLICATION_LOCK) {
								// Build-systems may run many instances of the compiler in parallel
								// All projects share the same ApplicationEnvironment, and when the last project is disposed,
								// the ApplicationEnvironment is disposed as well
								if (--ourProjectCount <= 0) {
									// Do not use this property unless you sure need it, causes Application to MEMORY LEAK
									// Only valid use-case is when Application should be cached to avoid
									// initialization costs
									if (disposeAppEnv) {
										disposeApplicationEnvironment()
									} else {
										ourApplicationEnvironment?.idleCleanup()
									}
								}
							}
						}
					})
				} finally {
					ourProjectCount++
				}

				return ourApplicationEnvironment!!
			}
		}

		/**
		 * This method is also used in Gradle after configuration phase finished.
		 */
		@JvmStatic
		fun disposeApplicationEnvironment() {
			synchronized(APPLICATION_LOCK) {
				val environment = ourApplicationEnvironment ?: return
				ourApplicationEnvironment = null
				Disposer.dispose(environment.parentDisposable)
				resetApplicationManager(environment.application)
				ZipHandler.clearFileAccessorCache()
			}
		}

		/**
		 * Resets the application managed by [org.jetbrains.kotlin.com.intellij.openapi.application.ApplicationManager] to `null`. If [applicationToReset] is specified, [resetApplicationManager]
		 * will only reset the application if it's the expected one. Otherwise, the application will already have been changed to another
		 * application. For example, application disposal can trigger one of the disposables registered via
		 * [org.jetbrains.kotlin.com.intellij.openapi.application.ApplicationManager.setApplication], which reset the managed application to the previous application.
		 */
		@JvmStatic
		fun resetApplicationManager(applicationToReset: Application? = null) {
			val currentApplication = ApplicationManager.getApplication() ?: return
			if (applicationToReset != null && applicationToReset != currentApplication) {
				return
			}

			try {
				val ourApplicationField = ApplicationManager::class.java.getDeclaredField("ourApplication")
				ourApplicationField.isAccessible = true
				ourApplicationField.set(null, null)
			} catch (exception: Exception) {
				// Resetting the application manager is not critical in a production context. If the reflective access fails, we shouldn't
				// expose the user to the failure.
				if (currentApplication.isUnitTestMode) {
					throw exception
				}
			}
		}

		@JvmStatic
		fun ProjectEnvironment.configureProjectEnvironment(
			configuration: CompilerConfiguration,
			configFiles: EnvironmentConfigFiles
		) {
			PersistentFSConstants::class.java.getDeclaredField("ourMaxIntellisenseFileSize")
				.apply { isAccessible = true }
				.setInt(null, FileUtilRt.LARGE_FOR_CONTENT_LOADING)

			// otherwise consider that project environment is properly configured before passing to the environment
			// TODO: consider some asserts to check important extension points

			val isJvm = configFiles == EnvironmentConfigFiles.JVM_CONFIG_FILES
			project.registerService(ModuleVisibilityManager::class.java, CliModuleVisibilityManagerImpl(isJvm))

			registerProjectServicesForCLI(this)

			registerProjectServices(project)

			for (extension in CompilerConfigurationExtension.Companion.getInstances(project)) {
				extension.updateConfiguration(configuration)
			}
		}

		private fun createApplicationEnvironment(
			parentDisposable: Disposable,
			configuration: CompilerConfiguration,
			environmentMode: KotlinCoreApplicationEnvironmentMode,
		): KotlinCoreApplicationEnvironment {
			val applicationEnvironment = KotlinCoreApplicationEnvironment.Companion.create(parentDisposable, environmentMode)

			registerApplicationExtensionPointsAndExtensionsFrom(configuration, "extensions/compiler.xml")

			registerApplicationServicesForCLI(applicationEnvironment)
			KotlinCoreEnvironment.Companion.registerApplicationServices(applicationEnvironment)

			return applicationEnvironment
		}

		private fun registerApplicationExtensionPointsAndExtensionsFrom(configuration: CompilerConfiguration, configFilePath: String) {
			fun File.hasConfigFile(configFile: String): Boolean =
				if (isDirectory) File(this, "META-INF" + File.separator + configFile).exists()
				else try {
					ZipFile(this).use {
						it.getEntry("META-INF/$configFile") != null
					}
				} catch (_: Throwable) {
					false
				}

			val pluginRoot: File =
				configuration.get(CLIConfigurationKeys.INTELLIJ_PLUGIN_ROOT)?.let(::File)
					?: PathUtil.getResourcePathForClass(this::class.java).takeIf { it.hasConfigFile(configFilePath) }
					// hack for load extensions when compiler run directly from project directory (e.g. in tests)
					?: File("src/commonMain/resources").takeIf { it.hasConfigFile(configFilePath) }
					?: configuration.get(CLIConfigurationKeys.PATH_TO_KOTLIN_COMPILER_JAR)?.takeIf { it.hasConfigFile(configFilePath) }
					?: throw IllegalStateException(
						"Unable to find extension point configuration $configFilePath " +
								"(cp:\n  ${(Thread.currentThread().contextClassLoader as? UrlClassLoader)?.urls?.joinToString("\n  ") { it.file }})"
					)

			CoreApplicationEnvironment.registerExtensionPointAndExtensions(
				FileSystems.getDefault().getPath(pluginRoot.path),
				configFilePath,
				ApplicationManager.getApplication().extensionArea
			)
		}

		private fun registerApplicationServicesForCLI(applicationEnvironment: KotlinCoreApplicationEnvironment) {
			// ability to get text from annotations xml files
			applicationEnvironment.registerFileType(PlainTextFileType.INSTANCE, "xml")
			applicationEnvironment.registerParserDefinition(JavaParserDefinition())
		}

		@JvmStatic
		fun registerProjectExtensionPoints(area: ExtensionsArea) {
			CoreApplicationEnvironment.registerExtensionPoint(
				area, PsiTreeChangePreprocessor.EP.name, PsiTreeChangePreprocessor::class.java
			)
			CoreApplicationEnvironment.registerExtensionPoint(area, PsiElementFinder.EP.name, PsiElementFinder::class.java)
		}

		// made public for Upsource
		@JvmStatic
		@Deprecated("Use registerProjectServices(project) instead.", ReplaceWith("registerProjectServices(projectEnvironment.project)"))
		fun registerProjectServices(
			projectEnvironment: JavaCoreProjectEnvironment,
			@Suppress("UNUSED_PARAMETER") messageCollector: MessageCollector?
		) {
			registerProjectServices(projectEnvironment.project)
		}

		// made public for Android Lint
		@JvmStatic
		fun registerProjectServices(project: MockProject) {
			with(project) {
				registerService(JavaElementSourceFactory::class.java, JavaFixedElementSourceFactory::class.java)
				registerService(KotlinJavaPsiFacade::class.java, KotlinJavaPsiFacade(this))
			}
		}

		fun registerProjectServicesForCLI(@Suppress("UNUSED_PARAMETER") projectEnvironment: JavaCoreProjectEnvironment) {
			/**
			 * Note that Kapt may restart code analysis process, and CLI services should be aware of that.
			 * Use PsiManager.getModificationTracker() to ensure that all the data you cached is still valid.
			 */
		}

		// made public for Android Lint
		@JvmStatic
		fun registerKotlinLightClassSupport(project: MockProject) {
			with(project) {
				val traceHolder = CliTraceHolder(project)
				val cliLightClassGenerationSupport = CliLightClassGenerationSupport(traceHolder, project)
				val kotlinAsJavaSupport = CliKotlinAsJavaSupport(project, traceHolder)
				registerService(LightClassGenerationSupport::class.java, cliLightClassGenerationSupport)
				registerService(CliLightClassGenerationSupport::class.java, cliLightClassGenerationSupport)
				registerService(KotlinAsJavaSupport::class.java, kotlinAsJavaSupport)
				registerService(CodeAnalyzerInitializer::class.java, traceHolder)

				// We don't pass Disposable because in some tests, we manually unregister these extensions, and that leads to LOG.error
				// exception from `ExtensionPointImpl.doRegisterExtension`, because the registered extension can no longer be found
				// when the project is being disposed.
				// For example, see the `unregisterExtension` call in `GenerationUtils.compileFilesUsingFrontendIR`.
				// TODO: refactor this to avoid registering unneeded extensions in the first place, and avoid using deprecated API. (KT-64296)
				@Suppress("DEPRECATION")
				PsiElementFinder.EP.getPoint(project).registerExtension(JavaElementFinder(this))
				@Suppress("DEPRECATION")
				PsiElementFinder.EP.getPoint(project).registerExtension(PsiElementFinderImpl(this))
			}
		}
	}
}