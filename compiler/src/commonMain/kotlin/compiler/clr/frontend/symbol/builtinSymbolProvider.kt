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

import compiler.clr.frontend.NodeAssembly
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.EffectiveVisibility
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.*
import org.jetbrains.kotlin.fir.builder.buildPackageDirective
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.builder.*
import org.jetbrains.kotlin.fir.declarations.impl.FirDeclarationStatusImpl
import org.jetbrains.kotlin.fir.declarations.impl.FirResolvedDeclarationStatusImpl
import org.jetbrains.kotlin.fir.resolve.providers.FirCompositeSymbolNamesProvider
import org.jetbrains.kotlin.fir.resolve.providers.FirSymbolNamesProvider
import org.jetbrains.kotlin.fir.resolve.providers.FirSymbolProvider
import org.jetbrains.kotlin.fir.resolve.providers.FirSymbolProviderInternals
import org.jetbrains.kotlin.fir.resolve.providers.impl.FirFallbackBuiltinSymbolProvider
import org.jetbrains.kotlin.fir.scopes.FirKotlinScopeProvider
import org.jetbrains.kotlin.fir.scopes.kotlinScopeProvider
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.fir.types.impl.ConeClassLikeTypeImpl
import org.jetbrains.kotlin.fir.types.impl.ConeTypeParameterTypeImpl
import org.jetbrains.kotlin.fir.types.toLookupTag
import org.jetbrains.kotlin.javac.resolve.classId
import org.jetbrains.kotlin.name.*
import org.jetbrains.kotlin.types.Variance

@OptIn(SymbolInternals::class)
class ClrBuiltinsSymbolProvider(
	session: FirSession,
	private val fallbackBuiltinSymbolProvider: FirFallbackBuiltinSymbolProvider,
	assemblies: Map<String, NodeAssembly>,
) : FirSymbolProvider(session) {
	private val assemblyBuiltinSymbolProvider = ClrCompilerBuiltinSymbolProvider(
		session,
		fallbackBuiltinSymbolProvider.moduleData,
		fallbackBuiltinSymbolProvider.kotlinScopeProvider,
		assemblies
	)

	override val symbolNamesProvider: FirSymbolNamesProvider
		get() = FirCompositeSymbolNamesProvider.fromSymbolProviders(
			listOf(
				assemblyBuiltinSymbolProvider,
				fallbackBuiltinSymbolProvider
			)
		)

	override fun getClassLikeSymbolByClassId(classId: ClassId): FirRegularClassSymbol? {
		return assemblyBuiltinSymbolProvider.getClassLikeSymbolByClassId(classId)
			?: fallbackBuiltinSymbolProvider.getClassLikeSymbolByClassId(classId)
	}

	@FirSymbolProviderInternals
	override fun getTopLevelCallableSymbolsTo(
		destination: MutableList<FirCallableSymbol<*>>,
		packageFqName: FqName,
		name: Name,
	) {
		val initSize = destination.size
		assemblyBuiltinSymbolProvider.getTopLevelCallableSymbolsTo(destination, packageFqName, name)
		if (initSize == destination.size) {
			fallbackBuiltinSymbolProvider.getTopLevelCallableSymbolsTo(destination, packageFqName, name)
		}
	}

	@FirSymbolProviderInternals
	override fun getTopLevelFunctionSymbolsTo(
		destination: MutableList<FirNamedFunctionSymbol>,
		packageFqName: FqName,
		name: Name,
	) {
		val initSize = destination.size
		assemblyBuiltinSymbolProvider.getTopLevelFunctionSymbolsTo(destination, packageFqName, name)
		if (initSize == destination.size) {
			fallbackBuiltinSymbolProvider.getTopLevelFunctionSymbolsTo(destination, packageFqName, name)
		}
	}

	@FirSymbolProviderInternals
	override fun getTopLevelPropertySymbolsTo(
		destination: MutableList<FirPropertySymbol>,
		packageFqName: FqName,
		name: Name,
	) {
		val initSize = destination.size
		assemblyBuiltinSymbolProvider.getTopLevelPropertySymbolsTo(destination, packageFqName, name)
		if (initSize == destination.size) {
			fallbackBuiltinSymbolProvider.getTopLevelPropertySymbolsTo(destination, packageFqName, name)
		}
	}

	override fun hasPackage(fqName: FqName): Boolean {
		return assemblyBuiltinSymbolProvider.hasPackage(fqName) || fallbackBuiltinSymbolProvider.hasPackage(fqName)
	}
}

@ThreadSafeMutableState
class ClrCompilerBuiltinSymbolProvider(
	session: FirSession,
	moduleData: FirModuleData,
	kotlinScopeProvider: FirKotlinScopeProvider,
	assemblies: Map<String, NodeAssembly>,
) : FirSymbolProvider(session) {
	private val annotationSymbol = StandardClassIds.Annotation.buildSymbol(moduleData) { classId, _, _ ->
		status = FirResolvedDeclarationStatusImpl(
			Visibilities.Public,
			Modality.OPEN,
			EffectiveVisibility.Public
		)
		classKind = ClassKind.INTERFACE
	}
	@OptIn(SymbolInternals::class)
	private val anySymbol = StandardClassIds.Any.buildSymbol(moduleData) { classId, _, _ ->
		status = FirResolvedDeclarationStatusImpl(
			Visibilities.Public,
			Modality.OPEN,
			EffectiveVisibility.Public
		)
		classKind = ClassKind.CLASS
		declarations += FirConstructorSymbol(
			callableId = classId.callableIdForConstructor()
		).also { constructorSymbol ->
			buildPrimaryConstructor {
				this.moduleData = moduleData
				origin = FirDeclarationOrigin.BuiltIns
				status = FirResolvedDeclarationStatusImpl(
					visibility = Visibilities.Public,
					modality = Modality.FINAL,
					effectiveVisibility = EffectiveVisibility.Public
				)
				returnTypeRef = buildResolvedTypeRef {
					coneType = ConeClassLikeTypeImpl(
						lookupTag = classId.toLookupTag(),
						typeArguments = emptyArray(),
						isMarkedNullable = false
					)
				}
				symbol = constructorSymbol
			}.apply {
				containingClassForStaticMemberAttr = classId.toLookupTag()
			}
		}.fir
		declarations += FirNamedFunctionSymbol(
			callableId = CallableId(classId, Name.identifier("equals"))
		).also { functionSymbol ->
			buildSimpleFunction {
				this.moduleData = moduleData
				origin = FirDeclarationOrigin.BuiltIns
				status = FirResolvedDeclarationStatusImpl(
					visibility = Visibilities.Public,
					modality = Modality.OPEN,
					effectiveVisibility = EffectiveVisibility.Public
				)
				returnTypeRef = buildResolvedTypeRef {
					coneType = ConeClassLikeTypeImpl(
						lookupTag = StandardClassIds.Boolean.toLookupTag(),
						typeArguments = emptyArray(),
						isMarkedNullable = false
					)
				}
				dispatchReceiverType = ConeClassLikeTypeImpl(
					lookupTag = classId.toLookupTag(),
					typeArguments = emptyArray(),
					isMarkedNullable = false
				)
				valueParameters += FirValueParameterSymbol(
					name = Name.identifier("other")
				).also { valueParameterSymbol ->
					buildValueParameter {
						this.moduleData = moduleData
						origin = FirDeclarationOrigin.BuiltIns
						returnTypeRef = buildResolvedTypeRef {
							coneType = ConeClassLikeTypeImpl(
								lookupTag = StandardClassIds.Any.toLookupTag(),
								typeArguments = emptyArray(),
								isMarkedNullable = true
							)
						}
						name = valueParameterSymbol.name
						symbol = valueParameterSymbol
						containingDeclarationSymbol = functionSymbol
					}
				}.fir
				name = functionSymbol.name
				symbol = functionSymbol
			}.apply {
				containingClassForStaticMemberAttr = classId.toLookupTag()
			}
		}.fir
		declarations += FirNamedFunctionSymbol(
			callableId = CallableId(classId, Name.identifier("hashCode"))
		).also { functionSymbol ->
			buildSimpleFunction {
				this.moduleData = moduleData
				origin = FirDeclarationOrigin.BuiltIns
				status = FirResolvedDeclarationStatusImpl(
					visibility = Visibilities.Public,
					modality = Modality.OPEN,
					effectiveVisibility = EffectiveVisibility.Public
				)
				returnTypeRef = buildResolvedTypeRef {
					coneType = ConeClassLikeTypeImpl(
						lookupTag = StandardClassIds.Int.toLookupTag(),
						typeArguments = emptyArray(),
						isMarkedNullable = false
					)
				}
				dispatchReceiverType = ConeClassLikeTypeImpl(
					lookupTag = classId.toLookupTag(),
					typeArguments = emptyArray(),
					isMarkedNullable = false
				)
				name = functionSymbol.name
				symbol = functionSymbol
			}.apply {
				containingClassForStaticMemberAttr = classId.toLookupTag()
			}
		}.fir
		declarations += FirNamedFunctionSymbol(
			callableId = CallableId(classId, Name.identifier("toString"))
		).also { functionSymbol ->
			buildSimpleFunction {
				this.moduleData = moduleData
				origin = FirDeclarationOrigin.BuiltIns
				status = FirResolvedDeclarationStatusImpl(
					visibility = Visibilities.Public,
					modality = Modality.OPEN,
					effectiveVisibility = EffectiveVisibility.Public
				)
				returnTypeRef = buildResolvedTypeRef {
					coneType = ConeClassLikeTypeImpl(
						lookupTag = StandardClassIds.String.toLookupTag(),
						typeArguments = emptyArray(),
						isMarkedNullable = false
					)
				}
				dispatchReceiverType = ConeClassLikeTypeImpl(
					lookupTag = classId.toLookupTag(),
					typeArguments = emptyArray(),
					isMarkedNullable = false
				)
				name = functionSymbol.name
				symbol = functionSymbol
			}.apply {
				containingClassForStaticMemberAttr = classId.toLookupTag()
			}
		}.fir
	}
	private val arraySymbol = StandardClassIds.Array.buildSymbol(moduleData) { classId, _, _ ->
		status = FirResolvedDeclarationStatusImpl(
			Visibilities.Public,
			Modality.FINAL,
			EffectiveVisibility.Public
		)
		classKind = ClassKind.CLASS
	}
	private val byteArraySymbol = classId("kotlin", "ByteArray").buildSymbol(moduleData) { classId, _, _ ->
		status = FirResolvedDeclarationStatusImpl(
			Visibilities.Public,
			Modality.FINAL,
			EffectiveVisibility.Public
		)
		classKind = ClassKind.CLASS
	}
	private val charArraySymbol = classId("kotlin", "CharArray").buildSymbol(moduleData) { classId, _, _ ->
		status = FirResolvedDeclarationStatusImpl(
			Visibilities.Public,
			Modality.FINAL,
			EffectiveVisibility.Public
		)
		classKind = ClassKind.CLASS
	}
	private val shortArraySymbol = classId("kotlin", "ShortArray").buildSymbol(moduleData) { classId, _, _ ->
		status = FirResolvedDeclarationStatusImpl(
			Visibilities.Public,
			Modality.FINAL,
			EffectiveVisibility.Public
		)
		classKind = ClassKind.CLASS
	}
	private val intArraySymbol = classId("kotlin", "IntArray").buildSymbol(moduleData) { classId, _, _ ->
		status = FirResolvedDeclarationStatusImpl(
			Visibilities.Public,
			Modality.FINAL,
			EffectiveVisibility.Public
		)
		classKind = ClassKind.CLASS
	}
	private val longArraySymbol = classId("kotlin", "LongArray").buildSymbol(moduleData) { classId, _, _ ->
		status = FirResolvedDeclarationStatusImpl(
			Visibilities.Public,
			Modality.FINAL,
			EffectiveVisibility.Public
		)
		classKind = ClassKind.CLASS
	}
	private val floatArraySymbol = classId("kotlin", "FloatArray").buildSymbol(moduleData) { classId, _, _ ->
		status = FirResolvedDeclarationStatusImpl(
			Visibilities.Public,
			Modality.FINAL,
			EffectiveVisibility.Public
		)
		classKind = ClassKind.CLASS
	}
	private val doubleArraySymbol = classId("kotlin", "DoubleArray").buildSymbol(moduleData) { classId, _, _ ->
		status = FirResolvedDeclarationStatusImpl(
			Visibilities.Public,
			Modality.FINAL,
			EffectiveVisibility.Public
		)
		classKind = ClassKind.CLASS
	}
	private val booleanArraySymbol = classId("kotlin", "BooleanArray").buildSymbol(moduleData) { classId, _, _ ->
		status = FirResolvedDeclarationStatusImpl(
			Visibilities.Public,
			Modality.FINAL,
			EffectiveVisibility.Public
		)
		classKind = ClassKind.CLASS
	}
	private val booleanSymbol = StandardClassIds.Boolean.buildSymbol(moduleData) { classId, _, _ ->
		status = FirResolvedDeclarationStatusImpl(
			Visibilities.Public,
			Modality.FINAL,
			EffectiveVisibility.Public
		)
		classKind = ClassKind.CLASS
	}
	private val charSymbol = StandardClassIds.Char.buildSymbol(moduleData) { classId, _, _ ->
		status = FirResolvedDeclarationStatusImpl(
			Visibilities.Public,
			Modality.FINAL,
			EffectiveVisibility.Public
		)
		classKind = ClassKind.CLASS
	}
	private val charSequenceSymbol = StandardClassIds.CharSequence.buildSymbol(moduleData) { classId, _, _ ->
		status = FirResolvedDeclarationStatusImpl(
			Visibilities.Public,
			Modality.OPEN,
			EffectiveVisibility.Public
		)
		classKind = ClassKind.INTERFACE
	}
	private val comparableSymbol = StandardClassIds.Comparable.buildSymbol(moduleData) { classId, _, _ ->
		status = FirResolvedDeclarationStatusImpl(
			Visibilities.Public,
			Modality.OPEN,
			EffectiveVisibility.Public
		)
		classKind = ClassKind.INTERFACE
	}
	private val enumSymbol = StandardClassIds.Enum.buildSymbol(moduleData) { classId, _, _ ->
		status = FirResolvedDeclarationStatusImpl(
			Visibilities.Public,
			Modality.ABSTRACT,
			EffectiveVisibility.Public
		)
		classKind = ClassKind.CLASS
	}
	@OptIn(SymbolInternals::class)
	private val nothingSymbol = StandardClassIds.Nothing.buildSymbol(moduleData) { classId, _, _ ->
		status = FirResolvedDeclarationStatusImpl(
			Visibilities.Public,
			Modality.FINAL,
			EffectiveVisibility.Public
		)
		classKind = ClassKind.CLASS
		declarations += FirConstructorSymbol(
			callableId = classId.callableIdForConstructor()
		).also { constructorSymbol ->
			buildPrimaryConstructor {
				this.moduleData = moduleData
				origin = FirDeclarationOrigin.BuiltIns
				status = FirResolvedDeclarationStatusImpl(
					visibility = Visibilities.Private,
					modality = Modality.FINAL,
					effectiveVisibility = EffectiveVisibility.PrivateInClass
				)
				returnTypeRef = buildResolvedTypeRef {
					coneType = ConeClassLikeTypeImpl(
						lookupTag = classId.toLookupTag(),
						typeArguments = emptyArray(),
						isMarkedNullable = false
					)
				}
				symbol = constructorSymbol
			}.apply {
				containingClassForStaticMemberAttr = classId.toLookupTag()
			}
		}.fir
	}
	private val numberSymbol = StandardClassIds.Number.buildSymbol(moduleData) { classId, _, _ ->
		status = FirResolvedDeclarationStatusImpl(
			Visibilities.Public,
			Modality.ABSTRACT,
			EffectiveVisibility.Public
		)
		classKind = ClassKind.CLASS
	}
	private val byteSymbol = StandardClassIds.Byte.buildSymbol(moduleData) { classId, _, _ ->
		status = FirResolvedDeclarationStatusImpl(
			Visibilities.Public,
			Modality.FINAL,
			EffectiveVisibility.Public
		)
		classKind = ClassKind.CLASS
	}
	private val shortSymbol = StandardClassIds.Short.buildSymbol(moduleData) { classId, _, _ ->
		status = FirResolvedDeclarationStatusImpl(
			Visibilities.Public,
			Modality.FINAL,
			EffectiveVisibility.Public
		)
		classKind = ClassKind.CLASS
	}
	@OptIn(SymbolInternals::class)
	private val intSymbol = StandardClassIds.Int.buildSymbol(moduleData) { classId, _, _ ->
		status = FirResolvedDeclarationStatusImpl(
			Visibilities.Public,
			Modality.FINAL,
			EffectiveVisibility.Public
		)
		classKind = ClassKind.CLASS
		declarations += FirNamedFunctionSymbol(
			callableId = CallableId(classId, Name.identifier("plus"))
		).also { functionSymbol ->
			buildSimpleFunction {
				this.moduleData = moduleData
				origin = FirDeclarationOrigin.BuiltIns
				status = FirDeclarationStatusImpl(
					Visibilities.Public,
					Modality.FINAL
				).apply {
					isOperator = true
				}.resolved(
					Visibilities.Public,
					Modality.FINAL,
					EffectiveVisibility.Public
				)
				returnTypeRef = buildResolvedTypeRef {
					coneType = ConeClassLikeTypeImpl(
						classId.toLookupTag(),
						emptyArray(),
						false
					)
				}
				dispatchReceiverType = ConeClassLikeTypeImpl(
					classId.toLookupTag(),
					emptyArray(),
					false
				)
				valueParameters += FirValueParameterSymbol(Name.identifier("other")).also { valueParameterSymbol ->
					buildValueParameter {
						this.moduleData = moduleData
						origin = FirDeclarationOrigin.BuiltIns
						returnTypeRef = buildResolvedTypeRef {
							coneType = ConeClassLikeTypeImpl(
								classId.toLookupTag(),
								emptyArray(),
								false
							)
						}
						name = valueParameterSymbol.name
						symbol = valueParameterSymbol
						containingDeclarationSymbol = functionSymbol
					}
				}.fir
				name = functionSymbol.callableId.callableName
				symbol = functionSymbol
			}
		}.fir
		declarations += FirNamedFunctionSymbol(
			callableId = CallableId(classId, Name.identifier("compareTo"))
		).also { functionSymbol ->
			buildSimpleFunction {
				this.moduleData = moduleData
				origin = FirDeclarationOrigin.BuiltIns
				status = FirDeclarationStatusImpl(
					Visibilities.Public,
					Modality.FINAL
				).apply {
					isOperator = true
				}.resolved(
					Visibilities.Public,
					Modality.FINAL,
					EffectiveVisibility.Public
				)
				returnTypeRef = buildResolvedTypeRef {
					coneType = ConeClassLikeTypeImpl(
						classId.toLookupTag(),
						emptyArray(),
						false
					)
				}
				dispatchReceiverType = ConeClassLikeTypeImpl(
					classId.toLookupTag(),
					emptyArray(),
					false
				)
				valueParameters += FirValueParameterSymbol(Name.identifier("other")).also { valueParameterSymbol ->
					buildValueParameter {
						this.moduleData = moduleData
						origin = FirDeclarationOrigin.BuiltIns
						returnTypeRef = buildResolvedTypeRef {
							coneType = ConeClassLikeTypeImpl(
								classId.toLookupTag(),
								emptyArray(),
								false
							)
						}
						name = valueParameterSymbol.name
						symbol = valueParameterSymbol
						containingDeclarationSymbol = functionSymbol
					}
				}.fir
				name = functionSymbol.callableId.callableName
				symbol = functionSymbol
			}
		}.fir
	}
	private val longSymbol = StandardClassIds.Long.buildSymbol(moduleData) { classId, _, _ ->
		status = FirResolvedDeclarationStatusImpl(
			Visibilities.Public,
			Modality.FINAL,
			EffectiveVisibility.Public
		)
		classKind = ClassKind.CLASS
	}
	private val floatSymbol = StandardClassIds.Float.buildSymbol(moduleData) { classId, _, _ ->
		status = FirResolvedDeclarationStatusImpl(
			Visibilities.Public,
			Modality.FINAL,
			EffectiveVisibility.Public
		)
		classKind = ClassKind.CLASS
	}
	@OptIn(SymbolInternals::class)
	private val doubleSymbol = StandardClassIds.Double.buildSymbol(moduleData) { classId, _, _ ->
		status = FirResolvedDeclarationStatusImpl(
			Visibilities.Public,
			Modality.FINAL,
			EffectiveVisibility.Public
		)
		classKind = ClassKind.CLASS
		declarations += FirNamedFunctionSymbol(
			callableId = CallableId(classId, Name.identifier("plus"))
		).also { functionSymbol ->
			buildSimpleFunction {
				this.moduleData = moduleData
				origin = FirDeclarationOrigin.BuiltIns
				status = FirDeclarationStatusImpl(
					Visibilities.Public,
					Modality.FINAL
				).apply {
					isOperator = true
				}.resolved(
					Visibilities.Public,
					Modality.FINAL,
					EffectiveVisibility.Public
				)
				returnTypeRef = buildResolvedTypeRef {
					coneType = ConeClassLikeTypeImpl(
						classId.toLookupTag(),
						emptyArray(),
						false
					)
				}
				dispatchReceiverType = ConeClassLikeTypeImpl(
					classId.toLookupTag(),
					emptyArray(),
					false
				)
				valueParameters += FirValueParameterSymbol(Name.identifier("other")).also { valueParameterSymbol ->
					buildValueParameter {
						this.moduleData = moduleData
						origin = FirDeclarationOrigin.BuiltIns
						returnTypeRef = buildResolvedTypeRef {
							coneType = ConeClassLikeTypeImpl(
								classId.toLookupTag(),
								emptyArray(),
								false
							)
						}
						name = valueParameterSymbol.name
						symbol = valueParameterSymbol
						containingDeclarationSymbol = functionSymbol
					}
				}.fir
				name = functionSymbol.callableId.callableName
				symbol = functionSymbol
			}
		}.fir
		declarations += FirNamedFunctionSymbol(
			callableId = CallableId(classId, Name.identifier("times"))
		).also { functionSymbol ->
			buildSimpleFunction {
				this.moduleData = moduleData
				origin = FirDeclarationOrigin.BuiltIns
				status = FirDeclarationStatusImpl(
					Visibilities.Public,
					Modality.FINAL
				).apply {
					isOperator = true
				}.resolved(
					Visibilities.Public,
					Modality.FINAL,
					EffectiveVisibility.Public
				)
				returnTypeRef = buildResolvedTypeRef {
					coneType = ConeClassLikeTypeImpl(
						classId.toLookupTag(),
						emptyArray(),
						false
					)
				}
				dispatchReceiverType = ConeClassLikeTypeImpl(
					classId.toLookupTag(),
					emptyArray(),
					false
				)
				valueParameters += FirValueParameterSymbol(Name.identifier("other")).also { valueParameterSymbol ->
					buildValueParameter {
						this.moduleData = moduleData
						origin = FirDeclarationOrigin.BuiltIns
						returnTypeRef = buildResolvedTypeRef {
							coneType = ConeClassLikeTypeImpl(
								StandardClassIds.Int.toLookupTag(),
								emptyArray(),
								false
							)
						}
						name = valueParameterSymbol.name
						symbol = valueParameterSymbol
						containingDeclarationSymbol = functionSymbol
					}
				}.fir
				name = functionSymbol.callableId.callableName
				symbol = functionSymbol
			}
		}.fir
	}
	private val stringSymbol = StandardClassIds.String.buildSymbol(moduleData) { classId, _, _ ->
		status = FirResolvedDeclarationStatusImpl(
			Visibilities.Public,
			Modality.FINAL,
			EffectiveVisibility.Public
		)
		classKind = ClassKind.CLASS
	}
	private val throwableSymbol = StandardClassIds.Throwable.buildSymbol(moduleData) { classId, _, _ ->
		status = FirResolvedDeclarationStatusImpl(
			Visibilities.Public,
			Modality.OPEN,
			EffectiveVisibility.Public
		)
		classKind = ClassKind.CLASS
	}

	private val iterableSymbol = StandardClassIds.Iterable.buildSymbol(moduleData) { classId, _, _ ->
		status = FirResolvedDeclarationStatusImpl(
			Visibilities.Public,
			Modality.OPEN,
			EffectiveVisibility.Public
		)
		classKind = ClassKind.INTERFACE
	}
	private val mutableIterableSymbol = StandardClassIds.MutableIterable.buildSymbol(moduleData) { classId, _, _ ->
		status = FirResolvedDeclarationStatusImpl(
			Visibilities.Public,
			Modality.OPEN,
			EffectiveVisibility.Public
		)
		classKind = ClassKind.INTERFACE
	}
	private val collectionSymbol = StandardClassIds.Collection.buildSymbol(moduleData) { classId, _, _ ->
		status = FirResolvedDeclarationStatusImpl(
			Visibilities.Public,
			Modality.OPEN,
			EffectiveVisibility.Public
		)
		classKind = ClassKind.INTERFACE
	}
	private val mutableCollectionSymbol = StandardClassIds.MutableCollection.buildSymbol(moduleData) { classId, _, _ ->
		status = FirResolvedDeclarationStatusImpl(
			Visibilities.Public,
			Modality.OPEN,
			EffectiveVisibility.Public
		)
		classKind = ClassKind.INTERFACE
	}
	@OptIn(SymbolInternals::class)
	private val listSymbol = StandardClassIds.List.buildSymbol(moduleData) { classId, _, classSymbol ->
		status = FirResolvedDeclarationStatusImpl(
			Visibilities.Public,
			Modality.OPEN,
			EffectiveVisibility.Public
		)
		classKind = ClassKind.INTERFACE
		val typeParameter = FirTypeParameterSymbol().also { typeParameterSymbol ->
			buildTypeParameter {
				this.moduleData = moduleData
				origin = FirDeclarationOrigin.BuiltIns
				name = Name.identifier("E")
				symbol = typeParameterSymbol
				containingDeclarationSymbol = classSymbol
				variance = Variance.OUT_VARIANCE
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
		typeParameters += typeParameter.fir
		declarations += FirNamedFunctionSymbol(
			callableId = CallableId(classId, Name.identifier("iterator"))
		).also { functionSymbol ->
			buildSimpleFunction {
				this.moduleData = moduleData
				origin = FirDeclarationOrigin.BuiltIns
				status = FirDeclarationStatusImpl(
					Visibilities.Public,
					Modality.OPEN
				).apply {
					isOperator = true
//					isOverride = true
				}.resolved(
					Visibilities.Public,
					Modality.FINAL,
					EffectiveVisibility.Public
				)
				returnTypeRef = buildResolvedTypeRef {
					coneType = ConeClassLikeTypeImpl(
						StandardClassIds.Iterator.toLookupTag(),
						arrayOf(
							ConeTypeParameterTypeImpl(
								typeParameter.toLookupTag(),
								false
							)
						),
						false
					)
				}
				dispatchReceiverType = ConeClassLikeTypeImpl(
					classId.toLookupTag(),
					emptyArray(),
					false
				)
				name = functionSymbol.callableId.callableName
				symbol = functionSymbol
			}
		}.fir
	}
	private val mutableListSymbol = StandardClassIds.MutableList.buildSymbol(moduleData) { classId, _, _ ->
		status = FirResolvedDeclarationStatusImpl(
			Visibilities.Public,
			Modality.OPEN,
			EffectiveVisibility.Public
		)
		classKind = ClassKind.INTERFACE
	}
	private val setSymbol = StandardClassIds.Set.buildSymbol(moduleData) { classId, _, _ ->
		status = FirResolvedDeclarationStatusImpl(
			Visibilities.Public,
			Modality.OPEN,
			EffectiveVisibility.Public
		)
		classKind = ClassKind.INTERFACE
	}
	private val mutableSetSymbol = StandardClassIds.MutableSet.buildSymbol(moduleData) { classId, _, _ ->
		status = FirResolvedDeclarationStatusImpl(
			Visibilities.Public,
			Modality.OPEN,
			EffectiveVisibility.Public
		)
		classKind = ClassKind.INTERFACE
	}
	private val mapSymbol = StandardClassIds.Map.buildSymbol(moduleData) { classId, _, _ ->
		status = FirResolvedDeclarationStatusImpl(
			Visibilities.Public,
			Modality.OPEN,
			EffectiveVisibility.Public
		)
		classKind = ClassKind.INTERFACE
	}
	private val mutableMapSymbol = StandardClassIds.MutableMap.buildSymbol(moduleData) { classId, _, _ ->
		status = FirResolvedDeclarationStatusImpl(
			Visibilities.Public,
			Modality.OPEN,
			EffectiveVisibility.Public
		)
		classKind = ClassKind.INTERFACE
	}
	@OptIn(SymbolInternals::class)
	private val iteratorSymbol = StandardClassIds.Iterator.buildSymbol(moduleData) { classId, _, classSymbol ->
		status = FirResolvedDeclarationStatusImpl(
			Visibilities.Public,
			Modality.OPEN,
			EffectiveVisibility.Public
		)
		classKind = ClassKind.INTERFACE
		val typeParameter = FirTypeParameterSymbol().also { typeParameterSymbol ->
			buildTypeParameter {
				this.moduleData = moduleData
				origin = FirDeclarationOrigin.BuiltIns
				name = Name.identifier("T")
				symbol = typeParameterSymbol
				containingDeclarationSymbol = classSymbol
				variance = Variance.OUT_VARIANCE
				isReified = true
			}
		}
		typeParameters += typeParameter.fir
		declarations += FirNamedFunctionSymbol(
			CallableId(classId, Name.identifier("next"))
		).also { functionSymbol ->
			buildSimpleFunction {
				this.moduleData = moduleData
				origin = FirDeclarationOrigin.BuiltIns
				status = FirDeclarationStatusImpl(
					Visibilities.Public,
					Modality.FINAL
				).apply {
					isOperator = true
				}.resolved(
					Visibilities.Public,
					Modality.FINAL,
					EffectiveVisibility.Public
				)
				returnTypeRef = buildResolvedTypeRef {
					coneType = ConeTypeParameterTypeImpl(
						typeParameter.toLookupTag(),
						false
					)
				}
				dispatchReceiverType = ConeClassLikeTypeImpl(
					classId.toLookupTag(),
					emptyArray(),
					false
				)
				name = functionSymbol.callableId.callableName
				symbol = functionSymbol
			}
		}.fir
		declarations += FirNamedFunctionSymbol(
			CallableId(classId, Name.identifier("hasNext"))
		).also { functionSymbol ->
			buildSimpleFunction {
				this.moduleData = moduleData
				origin = FirDeclarationOrigin.BuiltIns
				status = FirDeclarationStatusImpl(
					Visibilities.Public,
					Modality.FINAL
				).apply {
					isOperator = true
				}.resolved(
					Visibilities.Public,
					Modality.FINAL,
					EffectiveVisibility.Public
				)
				returnTypeRef = buildResolvedTypeRef {
					coneType = ConeClassLikeTypeImpl(
						StandardClassIds.Boolean.toLookupTag(),
						emptyArray(),
						false
					)
				}
				dispatchReceiverType = ConeClassLikeTypeImpl(
					classId.toLookupTag(),
					emptyArray(),
					false
				)
				name = functionSymbol.callableId.callableName
				symbol = functionSymbol
			}
		}.fir
	}
	private val mutableIteratorSymbol = StandardClassIds.MutableIterator.buildSymbol(moduleData) { classId, _, _ ->
		status = FirResolvedDeclarationStatusImpl(
			Visibilities.Public,
			Modality.OPEN,
			EffectiveVisibility.Public
		)
		classKind = ClassKind.INTERFACE
	}
	private val listIteratorSymbol = StandardClassIds.ListIterator.buildSymbol(moduleData) { classId, _, _ ->
		status = FirResolvedDeclarationStatusImpl(
			Visibilities.Public,
			Modality.OPEN,
			EffectiveVisibility.Public
		)
		classKind = ClassKind.INTERFACE
	}
	private val mutableListIteratorSymbol =
		StandardClassIds.MutableListIterator.buildSymbol(moduleData) { classId, _, _ ->
			status = FirResolvedDeclarationStatusImpl(
				Visibilities.Public,
				Modality.OPEN,
				EffectiveVisibility.Public
			)
			classKind = ClassKind.INTERFACE
		}

	@OptIn(FirImplementationDetail::class)
	private val builtinsClassSymbols = listOf(
		annotationSymbol,
		anySymbol,
		arraySymbol,
		byteArraySymbol,
		charArraySymbol,
		shortArraySymbol,
		intArraySymbol,
		longArraySymbol,
		floatArraySymbol,
		doubleArraySymbol,
		booleanArraySymbol,
		booleanSymbol,
		charSymbol,
		charSequenceSymbol,
		comparableSymbol,
		enumSymbol,
		nothingSymbol,
		numberSymbol,
		byteSymbol,
		shortSymbol,
		intSymbol,
		longSymbol,
		floatSymbol,
		doubleSymbol,
		stringSymbol,
		throwableSymbol,

		iterableSymbol,
		mutableIterableSymbol,
		collectionSymbol,
		mutableCollectionSymbol,
		listSymbol,
		mutableListSymbol,
		setSymbol,
		mutableSetSymbol,
		mapSymbol,
		mutableMapSymbol,
		iteratorSymbol,
		mutableIteratorSymbol,
		listIteratorSymbol,
		mutableListIteratorSymbol
	).associate { it.unpack() }

	@OptIn(SymbolInternals::class)
	private fun ClassId.buildSymbol(
		moduleData: FirModuleData,
		build: FirRegularClassBuilder.(ClassId, FirFileSymbol, FirRegularClassSymbol) -> Unit,
	): FirRegularClassSymbol {
		val returnSymbol: FirRegularClassSymbol
		FirFileSymbol().let { fileSymbol ->
			val classId = this
			buildFile {
				this.moduleData = moduleData
				origin = FirDeclarationOrigin.BuiltIns
				packageDirective = buildPackageDirective {
					packageFqName = classId.packageFqName
				}
				declarations += FirRegularClassSymbol(classId).also { classSymbol ->
					returnSymbol = classSymbol
					buildRegularClass {
						this.moduleData = moduleData
						origin = FirDeclarationOrigin.BuiltIns
						name = classId.shortClassName
						scopeProvider = session.kotlinScopeProvider
						build(this@buildSymbol, fileSymbol, classSymbol)
						symbol = classSymbol
					}
				}.fir
				name = classId.shortClassName.asString()
				symbol = fileSymbol
			}
		}
		return returnSymbol
	}

	private fun FirRegularClassSymbol.unpack(): Pair<ClassId, FirRegularClassSymbol> = classId to this

	override fun getClassLikeSymbolByClassId(classId: ClassId) = tryGetClassLikeSymbol(classId)

	override fun hasPackage(fqName: FqName) = builtinsClassSymbols.any { it.key.packageFqName == fqName }

	override val symbolNamesProvider = object : FirSymbolNamesProvider() {
		private val packageFqNamesSet: Set<FqName> by lazy {
			builtinsClassSymbols.keys.map { it.packageFqName }.toSet()
		}

		private val classifierNamesByPackage: Map<FqName, Set<Name>> by lazy {
			builtinsClassSymbols.keys.groupBy { it.packageFqName }
				.mapValues { entry -> entry.value.map { classId -> classId.shortClassName }.toSet() }
		}

		override val hasSpecificClassifierPackageNamesComputation: Boolean = true
		override val hasSpecificCallablePackageNamesComputation: Boolean = true

		override fun getPackageNames(): Set<String>? = buildSet {
			addAll(packageFqNamesSet.map { it.asString() })
		}.ifEmpty { null }

		override fun getTopLevelClassifierNamesInPackage(packageFqName: FqName): Set<Name>? {
			val explicitNames = classifierNamesByPackage[packageFqName]
			if (explicitNames != null) {
				return explicitNames
			}

			return emptySet()
		}

		override fun getTopLevelCallableNamesInPackage(packageFqName: FqName): Set<Name>? {
			return if (packageFqName in classifierNamesByPackage) emptySet() else emptySet()
		}
	}

	@OptIn(FirImplementationDetail::class)
	private fun tryGetClassLikeSymbol(classId: ClassId): FirRegularClassSymbol? =
		builtinsClassSymbols[classId]

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
}