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

package compiler.clr.frontend.symbol

import compiler.clr.frontend.*
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.EffectiveVisibility
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.*
import org.jetbrains.kotlin.fir.analysis.checkers.hasAnnotationOrInsideAnnotatedClass
import org.jetbrains.kotlin.fir.builder.buildPackageDirective
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.builder.*
import org.jetbrains.kotlin.fir.declarations.impl.FirDeclarationStatusImpl
import org.jetbrains.kotlin.fir.declarations.impl.FirResolvedDeclarationStatusImpl
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotation
import org.jetbrains.kotlin.fir.expressions.impl.FirEmptyAnnotationArgumentMapping
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.FirStub
import org.jetbrains.kotlin.fir.resolve.providers.FirSymbolNamesProvider
import org.jetbrains.kotlin.fir.resolve.providers.FirSymbolProvider
import org.jetbrains.kotlin.fir.resolve.providers.FirSymbolProviderInternals
import org.jetbrains.kotlin.fir.scopes.kotlinScopeProvider
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.fir.types.ConeTypeProjection
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.impl.ConeClassLikeTypeImpl
import org.jetbrains.kotlin.fir.types.impl.ConeTypeParameterTypeImpl
import org.jetbrains.kotlin.fir.types.toLookupTag
import org.jetbrains.kotlin.javac.resolve.classId
import org.jetbrains.kotlin.name.*
import org.jetbrains.kotlin.types.Variance

class ClrSymbolNamesProvider : FirSymbolNamesProvider() {
	private val packageNames = mutableSetOf<FqName>()
	private val classNames = mutableMapOf<FqName, MutableSet<Name>>()
	private val callableNames = mutableMapOf<FqName, MutableSet<Name>>()

	override val hasSpecificClassifierPackageNamesComputation: Boolean = true
	override val hasSpecificCallablePackageNamesComputation: Boolean = true

	override fun getPackageNames(): Set<String> = packageNames.map { it.asString() }.toSet()

	override fun getTopLevelClassifierNamesInPackage(packageFqName: FqName): Set<Name> =
		classNames[packageFqName] ?: emptySet()

	override fun getTopLevelCallableNamesInPackage(packageFqName: FqName): Set<Name> =
		callableNames[packageFqName] ?: emptySet()

	fun registerPackageName(packageFqName: FqName) {
		packageNames.add(packageFqName)
	}

	fun registerClassName(packageFqName: FqName, name: Name) {
		classNames.getOrPut(
			key = packageFqName,
			defaultValue = { mutableSetOf() }
		) += name
	}

	fun registerCallableName(packageFqName: FqName, name: Name) {
		callableNames.getOrPut(
			key = packageFqName,
			defaultValue = { mutableSetOf() }
		) += name
	}
}

@OptIn(FirImplementationDetail::class, SymbolInternals::class)
class ClrSymbolProvider(
	session: FirSession,
	assemblies: Map<String, NodeAssembly>,
	rewriteModuleData: FirModuleData?,
) : FirSymbolProvider(session) {
	private val clrSymbolNamesProvider = ClrSymbolNamesProvider()
	private val classPackages = mutableMapOf<String, MutableList<ClassId>>()
	private val classSymbols = mutableMapOf<ClassId, FirRegularClassSymbol>()
	private val functionPackages = mutableMapOf<String, MutableList<CallableId>>()
	private val functionSymbols = mutableMapOf<CallableId, MutableList<FirNamedFunctionSymbol>>()

	private val firModuleData: FirModuleData = rewriteModuleData
		?: session.nullableModuleData
		?: error("Module data is not registered in $session")

	private val firFileSymbols = assemblies.mapValues { (_, node) ->
		node.types
			.filterNot { it.match("System", "Void") || it.isNotPublic }
			.map { buildFile(it) }
	}.apply {
		/*File("fir").mkdir()
		this.forEach { (assembly, fileSymbols) ->
			File("fir/$assembly").printWriter().use { writer ->
				writer.println(fileSymbols.map { it.fir.render() })
			}
		}*/
	}

	private fun buildFile(node: NodeType) = FirFileSymbol().also { fileSymbol ->
		buildFile {
			moduleData = firModuleData
			origin = FirDeclarationOrigin.Library
			packageDirective = buildPackageDirective {
				packageFqName = FqName(node.namespace)
			}

			declarations += when {
				node.attributes
					.mapNotNull { it.type }
					.any { it.match("kotlin.clr", "KotlinObject") } -> buildObject(node).fir

				node.attributes
					.mapNotNull { it.type }
					.any { it.match("kotlin.clr", "KotlinFileClass") } -> buildFileClass(node).fir

				else -> buildClass(node).fir
			}
			name = node.name
			symbol = fileSymbol
		}
		clrSymbolNamesProvider.registerPackageName(fileSymbol.fir.packageFqName)
	}

	private fun buildObject(node: NodeType) = FirRegularClassSymbol(
		classId = classId(node.namespace, node.name)
	).also { classSymbol ->
		buildRegularClass {
			moduleData = firModuleData
			origin = FirDeclarationOrigin.Library
			typeParameters
			status = FirResolvedDeclarationStatusImpl(
				Visibilities.Public,
				Modality.FINAL,
				EffectiveVisibility.Public
			)
			classKind = ClassKind.OBJECT

			declarations += node.methods.map { buildTopLevelFunction(classSymbol.classId, it).fir }

			name = classSymbol.name
			scopeProvider = session.kotlinScopeProvider
			symbol = classSymbol
		}
		clrSymbolNamesProvider.registerClassName(classSymbol.packageFqName(), classSymbol.name)
		classPackages.getOrPut(
			key = node.namespace,
			defaultValue = { mutableListOf() }
		) += classSymbol.classId
		classSymbols[classSymbol.classId] = classSymbol
	}

	private fun buildFileClass(node: NodeType) = FirRegularClassSymbol(
		classId = classId(node.namespace, node.name)
	).also { classSymbol ->
		buildRegularClass {
			moduleData = firModuleData
			origin = FirDeclarationOrigin.Library
			typeParameters
			status = FirResolvedDeclarationStatusImpl(
				Visibilities.Public,
				Modality.FINAL,
				EffectiveVisibility.Public
			)
			classKind = ClassKind.CLASS

			declarations += node.methods.map { buildTopLevelFunction(classSymbol.classId, it).fir }

			name = classSymbol.name
			scopeProvider = session.kotlinScopeProvider
			symbol = classSymbol
			companionObjectSymbol = buildCompanionClass(node, classSymbol.classId)
		}
		clrSymbolNamesProvider.registerClassName(classSymbol.packageFqName(), classSymbol.name)
		classPackages.getOrPut(
			key = node.namespace,
			defaultValue = { mutableListOf() }
		) += classSymbol.classId
		classSymbols[classSymbol.classId] = classSymbol
	}

	private fun buildClass(node: NodeType) = FirRegularClassSymbol(
		classId = classId(node.namespace, node.name)
	).also { classSymbol ->
		buildRegularClass {
			moduleData = firModuleData
			origin = FirDeclarationOrigin.Library
			status = FirResolvedDeclarationStatusImpl(
				Visibilities.Public,
				Modality.FINAL,
				EffectiveVisibility.Public
			)
			classKind = ClassKind.CLASS

			declarations += node.constructors
				.filter { !it.isStatic }
				.map { buildConstructor(classSymbol.classId, it).fir }
			declarations += node.methods
				.filter { !it.isStatic }
				.map { buildFunction(classSymbol.classId, it).fir }

			name = classSymbol.name
			scopeProvider = session.kotlinScopeProvider
			symbol = classSymbol
			companionObjectSymbol = buildCompanionClass(node, classSymbol.classId)
		}
		clrSymbolNamesProvider.registerClassName(classSymbol.packageFqName(), classSymbol.name)
		classPackages.getOrPut(
			key = node.namespace,
			defaultValue = { mutableListOf() }
		) += classSymbol.classId
		classSymbols[classSymbol.classId] = classSymbol
	}

	private fun buildCompanionClass(node: NodeType, parent: ClassId) = FirRegularClassSymbol(
		classId = parent.createNestedClassId(SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT)
	).also { classSymbol ->
		buildRegularClass {
			moduleData = firModuleData
			origin = FirDeclarationOrigin.Library
			typeParameters
			status = FirDeclarationStatusImpl(
				Visibilities.Public,
				Modality.FINAL
			).apply {
				isCompanion = true
			}.resolved(
				Visibilities.Public,
				Modality.FINAL,
				EffectiveVisibility.Public
			)
			classKind = ClassKind.OBJECT

			declarations += node.constructors
				.filter { it.isStatic }
				.map { buildConstructor(classSymbol.classId, it).fir }
			declarations += node.methods
				.filter { it.isStatic }
				.map { buildFunction(classSymbol.classId, it).fir }

			name = classSymbol.name
			scopeProvider = session.kotlinScopeProvider
			symbol = classSymbol
		}
		classPackages.getOrPut(
			key = node.namespace,
			defaultValue = { mutableListOf() }
		) += classSymbol.classId
		classSymbols[classSymbol.classId] = classSymbol
	}

	private fun buildConstructor(classId: ClassId, node: NodeConstructor) = FirConstructorSymbol(
		callableId = classId.callableIdForConstructor()
	).also { constructorSymbol ->
		buildConstructor {
			moduleData = firModuleData
			origin = FirDeclarationOrigin.Library
			status = FirResolvedDeclarationStatusImpl(
				Visibilities.Public,
				Modality.FINAL,
				EffectiveVisibility.Public
			)
			returnTypeRef = TypeResolver.resolveType(
				namespace = classId.packageFqName.asString(),
				name = classId.shortClassName.asString(),
				isReturnPosition = true,
				nullable = false,
				typeParameters = emptyList()
			)
			if (!node.isStatic) {
				dispatchReceiverType = ConeClassLikeTypeImpl(
					classId.toLookupTag(),
					emptyArray<ConeTypeProjection>(),
					false
				)
			}
			symbol = constructorSymbol
		}
	}

	private fun buildTopLevelFunction(classId: ClassId, node: NodeMethod) = FirNamedFunctionSymbol(
		callableId = CallableId(classId.packageFqName, Name.identifier(node.name))
	).also { functionSymbol ->
		buildSimpleFunction {
			moduleData = firModuleData
			origin = FirDeclarationOrigin.Library
			status = FirDeclarationStatusImpl(
				Visibilities.Public,
				Modality.FINAL
			).apply {
				isStatic = true
			}.resolved(
				Visibilities.Public,
				Modality.FINAL,
				EffectiveVisibility.Public
			)

			val typeParameterSymbols = node.typeParameters.map { buildTypeParameter(it, functionSymbol) }

			returnTypeRef = when {
				node.attributes.mapNotNull { it.type }.any {
					it.match("System.Diagnostics.CodeAnalysis", "DoesNotReturnAttribute ")
				} -> buildResolvedTypeRef {
					coneType = ConeClassLikeTypeImpl(
						classId("kotlin", "Nothing").toLookupTag(),
						emptyArray(),
						false
					)
				}

				node.returnType.typeKind in listOf(2, 7, 10) -> TypeResolver.resolveType(
					namespace = node.returnType.namespace ?: "",
					name = node.returnType.name,
					isReturnPosition = true,
					nullable = !node.attributes.mapNotNull { it.type }.any {
						it.match("kotlin.clr", "KotlinNotNull")
					},
					typeParameters = node.typeParameters.map {
						typeParameterSymbols.find { symbol ->
							symbol.name.asString() == it.name
						}!!
					}
				)

				else -> buildResolvedTypeRef {
					coneType = ConeClassLikeTypeImpl(
						classId("kotlin", "Any").toLookupTag(),
						emptyArray(),
						true
					)
				}
			}

			val isExtension = node.attributes
				.mapNotNull { it.type }
				.any { it.match("kotlin.clr", "KotlinExtension") }
			valueParameters += node.parameters
				.drop(
					when (isExtension) {
						true -> 1
						false -> 0
					}
				)
				.map { buildValueParameter(it, functionSymbol, typeParameterSymbols).fir }
			if (isExtension) {
				receiverParameter = FirReceiverParameterSymbol().also { parameterSymbol ->
					buildReceiverParameter {
						moduleData = firModuleData
						origin = FirDeclarationOrigin.Library
						symbol = parameterSymbol
						typeRef = buildResolvedTypeRef {
							coneType = this@buildSimpleFunction.valueParameters.first().returnTypeRef.coneType
						}
						containingDeclarationSymbol = functionSymbol
					}
				}.fir
			}
			containerSource = ClrPackagePartSource(classId)
			name = Name.identifier(node.name)
			symbol = functionSymbol

			typeParameters += typeParameterSymbols.map { it.fir }
		}
		clrSymbolNamesProvider.registerCallableName(functionSymbol.packageFqName(), functionSymbol.name)
		functionPackages.getOrPut(
			key = functionSymbol.packageFqName().asString(),
			defaultValue = { mutableListOf() }
		) += functionSymbol.callableId
		functionSymbols.getOrPut(
			key = functionSymbol.callableId,
			defaultValue = { mutableListOf() }
		) += functionSymbol
	}

	private fun buildFunction(classId: ClassId, node: NodeMethod) = FirNamedFunctionSymbol(
		callableId = CallableId(classId, Name.identifier(node.name))
	).also { functionSymbol ->
		buildSimpleFunction {
			moduleData = firModuleData
			origin = FirDeclarationOrigin.Library
			status = FirResolvedDeclarationStatusImpl(
				Visibilities.Public,
				Modality.FINAL,
				EffectiveVisibility.Public
			)

			val typeParameterSymbols = node.typeParameters.map { buildTypeParameter(it, functionSymbol) }

			returnTypeRef = when {
				node.attributes.mapNotNull { it.type }.any {
					it.match("System.Diagnostics.CodeAnalysis", "DoesNotReturnAttribute ")
				} -> buildResolvedTypeRef {
					coneType = ConeClassLikeTypeImpl(
						classId("kotlin", "Nothing").toLookupTag(),
						emptyArray(),
						false
					)
				}

				node.returnType.typeKind in listOf(2, 7, 10) -> TypeResolver.resolveType(
					namespace = node.returnType.namespace ?: "",
					name = node.returnType.name,
					isReturnPosition = true,
					nullable = !node.attributes.mapNotNull { it.type }.any {
						it.match("kotlin.clr", "KotlinNotNull")
					},
					typeParameters = node.typeParameters.map {
						typeParameterSymbols.find { typeParameter ->
							typeParameter.name.asString() == it.name
						}!!
					}
				)

				else -> buildResolvedTypeRef {
					coneType = ConeClassLikeTypeImpl(
						classId("kotlin", "Any").toLookupTag(),
						emptyArray(),
						true
					)
				}
			}

			valueParameters += node.parameters.map {
				buildValueParameter(it, functionSymbol, typeParameterSymbols).fir
			}
			name = Name.identifier(node.name)
			symbol = functionSymbol
			if (node.isStatic) {
				annotations += buildAnnotation {
					annotationTypeRef = buildResolvedTypeRef {
						coneType = ConeClassLikeTypeImpl(
							lookupTag = classId("kotlin.clr", "ClrStatic").toLookupTag(),
							typeArguments = emptyArray(),
							isMarkedNullable = false
						)
					}
					argumentMapping = FirEmptyAnnotationArgumentMapping
				}
			}

			typeParameters += node.typeParameters
				.map { buildTypeParameter(it, functionSymbol).fir }
		}
	}

	private fun buildValueParameter(
		node: NodeParameter,
		containingSymbol: FirFunctionSymbol<*>,
		typeParameters: List<FirTypeParameterSymbol>,
	) = FirValueParameterSymbol(
		name = Name.identifier(node.name!!)
	).also { parameterSymbol ->
		buildValueParameter {
			moduleData = firModuleData
			origin = FirDeclarationOrigin.Library
			returnTypeRef = when {
				node.type.typeKind in listOf(2, 7, 10) -> TypeResolver.resolveType(
					namespace = node.type.namespace ?: "",
					name = node.type.name,
					isReturnPosition = true,
					nullable = !node.attributes.mapNotNull { it.type }.any {
						it.match("kotlin.clr", "KotlinNotNull")
					},
					typeParameters = emptyList()
				)

				node.type.typeKind == 1 -> buildResolvedTypeRef {
					coneType = ConeClassLikeTypeImpl(
						classId("kotlin", "Array").toLookupTag(),
						typeParameters.map {
							ConeTypeParameterTypeImpl(
								it.toLookupTag(),
								false
							)
						}.toTypedArray(),
						!node.attributes.mapNotNull { it.type }.any {
							it.match("kotlin.clr", "KotlinNotNull")
						}
					)
				}

				else -> buildResolvedTypeRef {
					coneType = ConeClassLikeTypeImpl(
						classId("kotlin", "Any").toLookupTag(),
						emptyArray(),
						true
					)
				}
			}
			name = parameterSymbol.callableId.callableName
			symbol = parameterSymbol
			if (node.hasDefaultValue) {
				defaultValue = FirStub
			}
			containingDeclarationSymbol = containingSymbol
			isVararg = node.isParams
		}
	}

	private fun buildTypeParameter(node: NodeTypeParameter, containingSymbol: FirFunctionSymbol<*>) =
		FirTypeParameterSymbol().also { parameterSymbol ->
			buildTypeParameter {
				moduleData = firModuleData
				origin = FirDeclarationOrigin.Library
				name = Name.identifier(node.name)
				symbol = parameterSymbol
				containingDeclarationSymbol = containingSymbol
				variance = Variance.INVARIANT
				isReified = true
				bounds += buildResolvedTypeRef {
					coneType = ConeClassLikeTypeImpl(
						classId("kotlin", "Any").toLookupTag(),
						emptyArray(),
						true
					)
				}
			}
		}

	fun hasClassSymbol(classId: ClassId): Boolean = classId in classSymbols

	override fun getClassLikeSymbolByClassId(classId: ClassId): FirRegularClassSymbol? {
		return classSymbols[classId]
	}

	@FirSymbolProviderInternals
	override fun getTopLevelCallableSymbolsTo(
		destination: MutableList<FirCallableSymbol<*>>,
		packageFqName: FqName,
		name: Name,
	) {
		destination += functionSymbols
			.filter { it.key.packageName == packageFqName && it.key.callableName == name }
			.flatMap { it.value }
	}

	@FirSymbolProviderInternals
	override fun getTopLevelFunctionSymbolsTo(
		destination: MutableList<FirNamedFunctionSymbol>,
		packageFqName: FqName,
		name: Name,
	) {
		destination += functionSymbols
			.filter { it.key.packageName == packageFqName && it.key.callableName == name }
			.flatMap { it.value }
	}

	@FirSymbolProviderInternals
	override fun getTopLevelPropertySymbolsTo(
		destination: MutableList<FirPropertySymbol>,
		packageFqName: FqName,
		name: Name,
	) {
	}

	override fun hasPackage(fqName: FqName): Boolean {
		val packageName = fqName.asString()
		return classPackages.containsKey(packageName) || functionPackages.containsKey(packageName)
	}

	override val symbolNamesProvider: FirSymbolNamesProvider = clrSymbolNamesProvider
}

/**
 * CLR实际化内置符号提供器
 */
class ClrActualizingBuiltinSymbolProvider(
	val builtinsSymbolProvider: ClrBuiltinsSymbolProvider,
	private val refinedSourceSymbolProviders: List<FirSymbolProvider>,
) : FirSymbolProvider(builtinsSymbolProvider.session) {
	override fun getClassLikeSymbolByClassId(classId: ClassId): FirRegularClassSymbol? {
		for (symbolProvider in refinedSourceSymbolProviders) {
			val classSymbol = symbolProvider.getClassLikeSymbolByClassId(classId) ?: continue
			if (!classSymbol.hasAnnotationOrInsideAnnotatedClass(
					classId("kotlin.internal", "ActualizeByClrBuiltinProvider"),
					symbolProvider.session
				)
			) {
				continue
			}

			// If there are multiple declarations with the same name, they will be reported as redeclarations by a checker
			return builtinsSymbolProvider.getClassLikeSymbolByClassId(classId) as FirRegularClassSymbol
		}

		return null
	}

	override val symbolNamesProvider: FirSymbolNamesProvider = builtinsSymbolProvider.symbolNamesProvider

	@FirSymbolProviderInternals
	override fun getTopLevelCallableSymbolsTo(
		destination: MutableList<FirCallableSymbol<*>>,
		packageFqName: FqName,
		name: Name,
	) {
	}

	@FirSymbolProviderInternals
	override fun getTopLevelFunctionSymbolsTo(
		destination: MutableList<FirNamedFunctionSymbol>,
		packageFqName: FqName,
		name: Name,
	) {
	}

	@FirSymbolProviderInternals
	override fun getTopLevelPropertySymbolsTo(
		destination: MutableList<FirPropertySymbol>,
		packageFqName: FqName,
		name: Name,
	) {
	}

	override fun hasPackage(fqName: FqName): Boolean = builtinsSymbolProvider.hasPackage(fqName)
}