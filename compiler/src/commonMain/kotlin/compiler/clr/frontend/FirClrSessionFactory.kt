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

import compiler.clr.frontend.symbol.ClrCompilerBuiltinSymbolProvider
import compiler.clr.frontend.symbol.ClrBuiltinsSymbolProvider
import compiler.clr.frontend.symbol.ClrSymbolProvider
import org.jetbrains.kotlin.config.AnalysisFlags
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.fir.*
import org.jetbrains.kotlin.fir.checkers.registerCommonCheckers
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.deserialization.ModuleDataProvider
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.fir.java.FirProjectSessionProvider
import org.jetbrains.kotlin.fir.resolve.ScopeSession
import org.jetbrains.kotlin.fir.resolve.providers.FirSymbolProvider
import org.jetbrains.kotlin.fir.resolve.providers.impl.FirBuiltinSyntheticFunctionInterfaceProvider
import org.jetbrains.kotlin.fir.resolve.providers.impl.FirCloneableSymbolProvider
import org.jetbrains.kotlin.fir.resolve.providers.impl.FirFallbackBuiltinSymbolProvider
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.scopes.FirContainingNamesAwareScope
import org.jetbrains.kotlin.fir.scopes.FirKotlinScopeProvider
import org.jetbrains.kotlin.fir.session.FirAbstractSessionFactory
import org.jetbrains.kotlin.fir.session.FirSessionConfigurator
import org.jetbrains.kotlin.fir.session.environment.AbstractProjectEnvironment
import org.jetbrains.kotlin.fir.session.environment.AbstractProjectFileSearchScope
import org.jetbrains.kotlin.fir.session.registerDefaultComponents
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.utils.addToStdlib.runIf
import org.jetbrains.kotlin.utils.addToStdlib.runUnless

@OptIn(SessionConfiguration::class)
object FirClrSessionFactory :
	FirAbstractSessionFactory<FirClrSessionFactory.LibraryContext, FirClrSessionFactory.SourceContext>() {

	fun createSharedLibrarySession(
		mainModuleName: Name,
		sessionProvider: FirProjectSessionProvider,
		projectEnvironment: AbstractProjectEnvironment,
		extensionRegistrars: List<FirExtensionRegistrar>,
		scope: AbstractProjectFileSearchScope,
		languageVersionSettings: LanguageVersionSettings,
		assemblies: Map<String, NodeAssembly>,
	) = createSharedLibrarySession(
		mainModuleName,
		LibraryContext(assemblies, projectEnvironment),
		sessionProvider,
		languageVersionSettings,
		extensionRegistrars
	) { session, moduleData, scopeProvider, extensionSyntheticFunctionInterfaceProvider ->
		listOfNotNull(
			extensionSyntheticFunctionInterfaceProvider,
			runUnless(languageVersionSettings.getFlag(AnalysisFlags.stdlibCompilation)) {
				initializeBuiltinsProvider(
					session,
					moduleData,
					scopeProvider,
					assemblies.filter { it.key == "kotlin-stdlib" },
				)
			},
			FirBuiltinSyntheticFunctionInterfaceProvider(session, moduleData, scopeProvider),
			FirCloneableSymbolProvider(session, moduleData, scopeProvider),
		)
	}

	fun createLibrarySession(
		sessionProvider: FirProjectSessionProvider,
		sharedLibrarySession: FirSession,
		moduleDataProvider: ModuleDataProvider,
		projectEnvironment: AbstractProjectEnvironment,
		extensionRegistrars: List<FirExtensionRegistrar>,
		scope: AbstractProjectFileSearchScope,
		languageVersionSettings: LanguageVersionSettings,
		assemblies: Map<String, NodeAssembly>,
	) = createLibrarySession(
		LibraryContext(assemblies, projectEnvironment),
		sharedLibrarySession,
		sessionProvider,
		moduleDataProvider,
		languageVersionSettings,
		extensionRegistrars,
		createProviders = { session, kotlinScopeProvider ->
			listOf(
				ClrSymbolProvider(
					session,
					assemblies.filterNot { it.key == "kotlin-stdlib" },
					moduleDataProvider.allModuleData.last()
				),
			)
		},
	)

	override fun createKotlinScopeProviderForLibrarySession(): FirKotlinScopeProvider {
		return FirKotlinScopeProvider(::wrapScopeWithClrMapped)
	}

	override fun FirSession.registerLibrarySessionComponents(c: LibraryContext) {
		registerDefaultComponents()
		registerClrComponents(c.assemblies)
	}

	fun createSourceSession(
		moduleData: FirModuleData,
		sessionProvider: FirProjectSessionProvider,
		projectEnvironment: AbstractProjectEnvironment,
		extensionRegistrars: List<FirExtensionRegistrar>,
		configuration: CompilerConfiguration,
		assemblies: Map<String, NodeAssembly>,
		init: FirSessionConfigurator.() -> Unit,
	): FirSession {
		val context = SourceContext(assemblies, projectEnvironment)
		return createSourceSession(
			moduleData,
			context = context,
			sessionProvider,
			extensionRegistrars,
			configuration,
			init,
			createProviders = { session, kotlinScopeProvider, symbolProvider, generatedSymbolsProvider ->
				val providers = listOfNotNull(
					symbolProvider,
					generatedSymbolsProvider,
					ClrSymbolProvider(session, assemblies, session.moduleData),
					initializeForStdlibIfNeeded(projectEnvironment, session, kotlinScopeProvider, assemblies),
				)

				SourceProviders(providers, null)
			}
		)
	}

	override fun createKotlinScopeProviderForSourceSession(
		moduleData: FirModuleData,
		languageVersionSettings: LanguageVersionSettings,
	): FirKotlinScopeProvider {
		if (languageVersionSettings.getFlag(AnalysisFlags.stdlibCompilation) && moduleData.isCommon) {
			return FirKotlinScopeProvider()
		}
		return FirKotlinScopeProvider { klass, declaredScope, useSiteSession, scopeSession, memberRequiredPhase ->
			wrapScopeWithClrMapped(
				klass,
				declaredScope,
				useSiteSession,
				scopeSession,
				memberRequiredPhase,
				filterOutClrPlatformDeclarations = !languageVersionSettings.getFlag(AnalysisFlags.stdlibCompilation)
			)
		}
	}

	override fun FirSessionConfigurator.registerPlatformCheckers(c: SourceContext) {
		registerCommonCheckers() // CLR特定的检查器可以添加在这里
	}

	override fun FirSessionConfigurator.registerExtraPlatformCheckers(c: SourceContext) {
	}

	override fun FirSession.registerSourceSessionComponents(c: SourceContext) {
		registerDefaultComponents()
		registerClrComponents(c.assemblies)
		register(FirClrTargetProvider::class, FirClrTargetProvider())
	}

	private fun FirSession.registerClrComponents(assemblies: Map<String, NodeAssembly>) {
		register(FirClrAssemblyProvider::class, FirClrAssemblyProvider(assemblies))
	}

	private fun wrapScopeWithClrMapped(
		klass: FirClass,
		declaredMemberScope: FirContainingNamesAwareScope,
		useSiteSession: FirSession,
		scopeSession: ScopeSession,
		memberRequiredPhase: FirResolvePhase?,
		filterOutClrPlatformDeclarations: Boolean = false,
	): FirContainingNamesAwareScope {
		if (klass !is FirRegularClass) return declaredMemberScope
		val classId = klass.symbol.classId
		val kotlinUnsafeFqName = classId.asSingleFqName().toUnsafe()
		val symbolProvider = useSiteSession.symbolProvider

		// 优先处理我们关心的 CLR 库类 (或者任何非 BuiltInsFallback 和非 Source 的 Library origin 类)
		if (klass.origin == FirDeclarationOrigin.Library) {
			return ClrClassMemberScope(klass, useSiteSession, scopeSession, declaredMemberScope)
		}

		// 对于 Kotlin 的内建回退类
		if (klass.origin == FirDeclarationOrigin.BuiltInsFallback) {
			return declaredMemberScope
		}

		// 对于源码中定义的类
		if (klass.origin == FirDeclarationOrigin.Source) {
			return declaredMemberScope
		}

		// 其他情况（例如 Java 类、Enhancement 等，如果未来支持的话）
		// 或者如果一个 Library 类因为某些原因没有被上面的 if (klass.origin == FirDeclarationOrigin.Library) 捕获
		return declaredMemberScope
	}

	class LibraryContext(
		val assemblies: Map<String, NodeAssembly>,
		val projectEnvironment: AbstractProjectEnvironment,
	)

	class SourceContext(
		val assemblies: Map<String, NodeAssembly>,
		val projectEnvironment: AbstractProjectEnvironment,
	)

	private fun initializeForStdlibIfNeeded(
		projectEnvironment: AbstractProjectEnvironment,
		session: FirSession,
		kotlinScopeProvider: FirKotlinScopeProvider,
		assemblies: Map<String, NodeAssembly>,
	): FirSymbolProvider? {
		return runIf(
			session.languageVersionSettings.getFlag(AnalysisFlags.stdlibCompilation) &&
					!session.moduleData.isCommon
					&& session.moduleData.dependsOnDependencies.isEmpty()
		) {
			ClrCompilerBuiltinSymbolProvider(
				session,
				session.moduleData,
				kotlinScopeProvider,
				assemblies.filter { it.key == "kotlin-stdlib" },
			)
		}
	}

	private fun initializeBuiltinsProvider(
		session: FirSession,
		builtinsModuleData: FirModuleData,
		kotlinScopeProvider: FirKotlinScopeProvider,
		assemblies: Map<String, NodeAssembly>,
	): ClrBuiltinsSymbolProvider = ClrBuiltinsSymbolProvider(
		session,
		FirFallbackBuiltinSymbolProvider(session, builtinsModuleData, kotlinScopeProvider),
		assemblies,
	)
}