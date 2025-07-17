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

import compiler.clr.frontend.symbol.builder.*
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.EffectiveVisibility
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.*
import org.jetbrains.kotlin.fir.builder.buildPackageDirective
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.builder.*
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.resolve.providers.FirCompositeSymbolNamesProvider
import org.jetbrains.kotlin.fir.resolve.providers.FirSymbolNamesProvider
import org.jetbrains.kotlin.fir.resolve.providers.FirSymbolProvider
import org.jetbrains.kotlin.fir.resolve.providers.FirSymbolProviderInternals
import org.jetbrains.kotlin.fir.resolve.providers.impl.FirFallbackBuiltinSymbolProvider
import org.jetbrains.kotlin.fir.scopes.FirKotlinScopeProvider
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.fir.types.impl.ConeTypeParameterTypeImpl
import org.jetbrains.kotlin.fir.types.toLookupTag
import org.jetbrains.kotlin.javac.resolve.classId
import org.jetbrains.kotlin.name.*
import org.jetbrains.kotlin.types.Variance

@OptIn(SymbolInternals::class)
class ClrBuiltinsSymbolProvider(
	session: FirSession,
	private val fallbackBuiltinSymbolProvider: FirFallbackBuiltinSymbolProvider,
) : FirSymbolProvider(session) {
	private val assemblyBuiltinSymbolProvider = ClrCompilerBuiltinSymbolProvider(
		session,
		fallbackBuiltinSymbolProvider.moduleData,
		fallbackBuiltinSymbolProvider.kotlinScopeProvider
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

@OptIn(SymbolInternals::class)
@ThreadSafeMutableState
class ClrCompilerBuiltinSymbolProvider(
	session: FirSession,
	moduleData: FirModuleData,
	kotlinScopeProvider: FirKotlinScopeProvider,
) : FirSymbolProvider(session) {
	private val context = FirBuilderContext(moduleData, kotlinScopeProvider, FirDeclarationOrigin.BuiltIns)

	private val annotationSymbol = context(context) {
		buildSymbol(
			classId = StandardClassIds.Annotation,
			status = status(modality = Modality.OPEN),
			classKind = ClassKind.INTERFACE
		)
	}

	private val anySymbol = context(context) {
		buildSymbol(
			classId = StandardClassIds.Any,
			status = status(modality = Modality.OPEN),
			declarations = {
				val classId = implicit<FirRegularClassBuilder>().symbol.classId

				+primaryConstructor().apply {
					containingClassForStaticMemberAttr = classId.toLookupTag()
				}

				+simpleFunction(
					name = Name.identifier("equals"),
					status = status(modality = Modality.OPEN),
					returnTypeRef = resolvedTypeRef(
						coneType = coneClassLikeType(StandardClassIds.Boolean)
					),
					dispatchReceiverType = coneClassLikeType(classId),
					valueParameters = {
						+valueParameter(
							name = Name.identifier("other"),
							returnTypeRef = resolvedTypeRef(
								coneType = coneClassLikeType(
									classId = StandardClassIds.Any,
									isMarkedNullable = true
								)
							)
						)
					}
				).apply {
					containingClassForStaticMemberAttr = classId.toLookupTag()
				}

				+simpleFunction(
					name = Name.identifier("hashCode"),
					status = status(modality = Modality.OPEN),
					returnTypeRef = resolvedTypeRef(
						coneType = coneClassLikeType(StandardClassIds.Int)
					),
					dispatchReceiverType = coneClassLikeType(classId)
				).apply {
					containingClassForStaticMemberAttr = classId.toLookupTag()
				}

				+simpleFunction(
					name = Name.identifier("toString"),
					status = status(modality = Modality.OPEN),
					returnTypeRef = resolvedTypeRef(
						coneType = coneClassLikeType(StandardClassIds.String)
					),
					dispatchReceiverType = coneClassLikeType(classId)
				).apply {
					containingClassForStaticMemberAttr = classId.toLookupTag()
				}
			},
			superTypeRefs = {}
		)
	}

	private val arraySymbol = context(context) {
		buildSymbol(
			classId = StandardClassIds.Array
		)
	}

	private val byteArraySymbol = context(context) {
		buildSymbol(
			classId = classId("kotlin", "ByteArray")
		)
	}

	private val charArraySymbol = context(context) {
		buildSymbol(
			classId = classId("kotlin", "CharArray")
		)
	}

	private val shortArraySymbol = context(context) {
		buildSymbol(
			classId = classId("kotlin", "ShortArray")
		)
	}

	private val intArraySymbol = context(context) {
		buildSymbol(
			classId = classId("kotlin", "IntArray")
		)
	}

	private val longArraySymbol = context(context) {
		buildSymbol(
			classId = classId("kotlin", "LongArray")
		)
	}

	private val floatArraySymbol = context(context) {
		buildSymbol(
			classId = classId("kotlin", "FloatArray")
		)
	}

	private val doubleArraySymbol = context(context) {
		buildSymbol(
			classId = classId("kotlin", "DoubleArray")
		)
	}

	private val booleanArraySymbol = context(context) {
		buildSymbol(
			classId = classId("kotlin", "BooleanArray")
		)
	}

	private val booleanSymbol = context(context) {
		buildSymbol(
			classId = StandardClassIds.Boolean,
			declarations = {
				val classId = implicit<FirRegularClassBuilder>().symbol.classId

				+simpleFunction(
					name = Name.identifier("not"),
					status = status(isOperator = true),
					returnTypeRef = resolvedTypeRef(
						coneType = coneClassLikeType(classId)
					),
					dispatchReceiverType = coneClassLikeType(classId)
				)
			}
		)
	}

	private val charSymbol = context(context) {
		buildSymbol(
			classId = StandardClassIds.Char
		)
	}

	private val charSequenceSymbol = context(context) {
		buildSymbol(
			classId = StandardClassIds.CharSequence,
			status = status(modality = Modality.OPEN),
			classKind = ClassKind.INTERFACE
		)
	}

	private val comparableSymbol = context(context) {
		buildSymbol(
			classId = StandardClassIds.Comparable,
			status = status(modality = Modality.OPEN),
			classKind = ClassKind.INTERFACE
		)
	}

	private val enumSymbol = context(context) {
		buildSymbol(
			classId = StandardClassIds.Enum,
			status = status(modality = Modality.ABSTRACT)
		)
	}

	private val nothingSymbol = context(context) {
		buildSymbol(
			classId = StandardClassIds.Nothing,
			declarations = {
				val classId = implicit<FirRegularClassBuilder>().symbol.classId

				+primaryConstructor(
					status = status(
						visibility = Visibilities.Private,
						effectiveVisibility = EffectiveVisibility.PrivateInClass
					)
				).apply {
					containingClassForStaticMemberAttr = classId.toLookupTag()
				}
			}
		)
	}

	private val numberSymbol = context(context) {
		buildSymbol(
			classId = StandardClassIds.Number,
			status = status(modality = Modality.ABSTRACT)
		)
	}

	private val byteSymbol = context(context) {
		buildSymbol(
			classId = StandardClassIds.Byte
		)
	}

	private val shortSymbol = context(context) {
		buildSymbol(
			classId = StandardClassIds.Short
		)
	}

	private val intSymbol = context(context) {
		buildSymbol(
			classId = StandardClassIds.Int,
			declarations = {
				val classId = implicit<FirRegularClassBuilder>().symbol.classId

				+simpleFunction(
					name = Name.identifier("plus"),
					status = status(isOperator = true),
					returnTypeRef = resolvedTypeRef(
						coneType = coneClassLikeType(classId)
					),
					dispatchReceiverType = coneClassLikeType(classId),
					valueParameters = {
						+valueParameter(
							name = Name.identifier("other"),
							returnTypeRef = resolvedTypeRef(
								coneType = coneClassLikeType(classId)
							)
						)
					}
				)

				+simpleFunction(
					name = Name.identifier("compareTo"),
					status = status(isOperator = true),
					returnTypeRef = resolvedTypeRef(
						coneType = coneClassLikeType(classId)
					),
					dispatchReceiverType = coneClassLikeType(classId),
					valueParameters = {
						+valueParameter(
							name = Name.identifier("other"),
							returnTypeRef = resolvedTypeRef(
								coneType = coneClassLikeType(classId)
							)
						)
					}
				)

				+simpleFunction(
					name = Name.identifier("inc"),
					status = status(modality = Modality.OPEN, isOperator = true),
					returnTypeRef = resolvedTypeRef(
						coneType = coneClassLikeType(classId)
					),
					dispatchReceiverType = coneClassLikeType(classId),
				)

				+simpleFunction(
					name = Name.identifier("dec"),
					status = status(modality = Modality.OPEN, isOperator = true),
					returnTypeRef = resolvedTypeRef(
						coneType = coneClassLikeType(classId)
					),
					dispatchReceiverType = coneClassLikeType(classId),
				)

				+simpleFunction(
					name = Name.identifier("rangeTo"),
					status = status(isOperator = true),
					returnTypeRef = resolvedTypeRef(
						coneType = coneClassLikeType(StandardClassIds.IntRange)
					),
					dispatchReceiverType = coneClassLikeType(classId),
					valueParameters = {
						+valueParameter(
							name = Name.identifier("other"),
							returnTypeRef = resolvedTypeRef(
								coneType = coneClassLikeType(classId)
							)
						)
					}
				)
			}
		)
	}

	private val longSymbol = context(context) {
		buildSymbol(
			classId = StandardClassIds.Long
		)
	}

	private val floatSymbol = context(context) {
		buildSymbol(
			classId = StandardClassIds.Float
		)
	}

	private val doubleSymbol = context(context) {
		buildSymbol(
			classId = StandardClassIds.Double,
			declarations = {
				val classId = implicit<FirRegularClassBuilder>().symbol.classId

				+simpleFunction(
					name = Name.identifier("plus"),
					status = status(isOperator = true),
					returnTypeRef = resolvedTypeRef(
						coneType = coneClassLikeType(classId)
					),
					dispatchReceiverType = coneClassLikeType(classId),
					valueParameters = {
						+valueParameter(
							name = Name.identifier("other"),
							returnTypeRef = resolvedTypeRef(
								coneType = coneClassLikeType(classId)
							)
						)
					}
				)

				+simpleFunction(
					name = Name.identifier("times"),
					status = status(isOperator = true),
					returnTypeRef = resolvedTypeRef(
						coneType = coneClassLikeType(classId)
					),
					dispatchReceiverType = coneClassLikeType(classId),
					valueParameters = {
						+valueParameter(
							name = Name.identifier("other"),
							returnTypeRef = resolvedTypeRef(
								coneType = coneClassLikeType(StandardClassIds.Int)
							)
						)
					}
				)
			}
		)
	}

	private val stringSymbol = context(context) {
		buildSymbol(
			classId = StandardClassIds.String,
			declarations = {
				val classId = implicit<FirRegularClassBuilder>().symbol.classId

				+simpleFunction(
					name = Name.identifier("plus"),
					status = status(modality = Modality.OPEN, isOperator = true),
					returnTypeRef = resolvedTypeRef(
						coneType = coneClassLikeType(classId)
					),
					dispatchReceiverType = coneClassLikeType(classId),
					valueParameters = {
						+valueParameter(
							name = Name.identifier("other"),
							returnTypeRef = resolvedTypeRef(
								coneType = coneClassLikeType(
									classId = classId,
									isMarkedNullable = true
								)
							)
						)
					}
				)

				+property(
					name = Name.identifier("length"),
					status = status(modality = Modality.OPEN),
					returnTypeRef = resolvedTypeRef(
						coneType = coneClassLikeType(StandardClassIds.Int)
					),
					dispatchReceiverType = coneClassLikeType(classId)
				)

				+simpleFunction(
					name = Name.identifier("get"),
					status = status(modality = Modality.OPEN, isOperator = true),
					returnTypeRef = resolvedTypeRef(
						coneType = coneClassLikeType(StandardClassIds.Char)
					),
					dispatchReceiverType = coneClassLikeType(classId),
					valueParameters = {
						+valueParameter(
							name = Name.identifier("index"),
							returnTypeRef = resolvedTypeRef(
								coneType = coneClassLikeType(StandardClassIds.Int)
							)
						)
					}
				)
			}
		)
	}

	private val throwableSymbol = context(context) {
		buildSymbol(
			classId = StandardClassIds.Throwable,
			status = status(modality = Modality.OPEN)
		)
	}

	private val iterableSymbol = context(context) {
		buildSymbol(
			classId = StandardClassIds.Iterable,
			status = status(modality = Modality.OPEN),
			classKind = ClassKind.INTERFACE
		)
	}

	private val mutableIterableSymbol = context(context) {
		buildSymbol(
			classId = StandardClassIds.MutableIterable,
			status = status(modality = Modality.OPEN),
			classKind = ClassKind.INTERFACE
		)
	}

	private val collectionSymbol = context(context) {
		buildSymbol(
			classId = StandardClassIds.Collection,
			status = status(modality = Modality.OPEN),
			classKind = ClassKind.INTERFACE
		)
	}

	private val mutableCollectionSymbol = context(context) {
		buildSymbol(
			classId = StandardClassIds.MutableCollection,
			status = status(modality = Modality.OPEN),
			classKind = ClassKind.INTERFACE
		)
	}

	private val listSymbol = context(context) {
		buildSymbol(
			classId = StandardClassIds.List,
			status = status(modality = Modality.OPEN),
			classKind = ClassKind.INTERFACE,
			typeParameters = {
				+typeParameter(
					name = Name.identifier("E"),
					containingDeclarationSymbol = implicit<FirRegularClassBuilder>().symbol,
					variance = Variance.OUT_VARIANCE,
					isReified = true
				)
			},
			declarations = {
				val classId = implicit<FirRegularClassBuilder>().symbol.classId

				+simpleFunction(
					name = Name.identifier("iterator"),
					status = status(modality = Modality.OPEN, isOperator = true),
					returnTypeRef = resolvedTypeRef(
						coneType = coneClassLikeType(
							classId = StandardClassIds.Iterator,
							typeArguments = arrayOf(
								coneTypeParameterType(
									symbol = implicit<FirRegularClassBuilder>().typeParameters.single().symbol
								)
							)
						)
					),
					dispatchReceiverType = coneClassLikeType(classId)
				)
			}
		)
	}

	private val mutableListSymbol = context(context) {
		buildSymbol(
			classId = StandardClassIds.MutableList,
			status = status(modality = Modality.OPEN),
			classKind = ClassKind.INTERFACE
		)
	}

	private val setSymbol = context(context) {
		buildSymbol(
			classId = StandardClassIds.Set,
			status = status(modality = Modality.OPEN),
			classKind = ClassKind.INTERFACE
		)
	}

	private val mutableSetSymbol = context(context) {
		buildSymbol(
			classId = StandardClassIds.MutableSet,
			status = status(modality = Modality.OPEN),
			classKind = ClassKind.INTERFACE
		)
	}

	private val mapSymbol = context(context) {
		buildSymbol(
			classId = StandardClassIds.Map,
			status = status(modality = Modality.OPEN),
			classKind = ClassKind.INTERFACE
		)
	}

	private val mutableMapSymbol = context(context) {
		buildSymbol(
			classId = StandardClassIds.MutableMap,
			status = status(modality = Modality.OPEN),
			classKind = ClassKind.INTERFACE
		)
	}

	private val iteratorSymbol = context(context) {
		buildSymbol(
			classId = StandardClassIds.Iterator,
			status = status(modality = Modality.OPEN),
			classKind = ClassKind.INTERFACE,
			typeParameters = {
				+typeParameter(
					name = Name.identifier("T"),
					containingDeclarationSymbol = implicit<FirRegularClassBuilder>().symbol,
					variance = Variance.OUT_VARIANCE,
					isReified = true
				)
			},
			declarations = {
				val classId = implicit<FirRegularClassBuilder>().symbol.classId

				+simpleFunction(
					name = Name.identifier("next"),
					status = status(isOperator = true),
					returnTypeRef = resolvedTypeRef(
						coneType = coneTypeParameterType(
							symbol = implicit<FirRegularClassBuilder>().typeParameters.single().symbol
						)
					),
					dispatchReceiverType = coneClassLikeType(classId)
				)

				+simpleFunction(
					name = Name.identifier("hasNext"),
					status = status(isOperator = true),
					returnTypeRef = resolvedTypeRef(
						coneType = coneClassLikeType(StandardClassIds.Boolean)
					),
					dispatchReceiverType = coneClassLikeType(classId)
				)
			}
		)
	}

	private val mutableIteratorSymbol = context(context) {
		buildSymbol(
			classId = StandardClassIds.MutableIterator,
			status = status(modality = Modality.OPEN),
			classKind = ClassKind.INTERFACE
		)
	}

	private val listIteratorSymbol = context(context) {
		buildSymbol(
			classId = StandardClassIds.ListIterator,
			status = status(modality = Modality.OPEN),
			classKind = ClassKind.INTERFACE
		)
	}

	private val mutableListIteratorSymbol = context(context) {
		buildSymbol(
			classId = StandardClassIds.MutableListIterator,
			status = status(modality = Modality.OPEN),
			classKind = ClassKind.INTERFACE
		)
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

	@OptIn(DirectDeclarationsAccess::class)
	context(builder: FirBuilderContext)
	private fun buildSymbol(
		classId: ClassId,
		source: KtSourceElement? = null,
		resolvePhase: FirResolvePhase = FirResolvePhase.RAW_FIR,
		attributes: FirDeclarationAttributes = FirDeclarationAttributes(),
		typeParameters: context(FirRegularClassBuilder, TypeParametersBuilder) () -> Unit = {},
		status: FirDeclarationStatus = status(),
		deprecationsProvider: DeprecationsProvider = UnresolvedDeprecationProvider,
		classKind: ClassKind = ClassKind.CLASS,
		declarations: context(FirRegularClassBuilder, DeclarationsBuilder) () -> Unit = {},
		annotations: List<FirAnnotation> = emptyList(),
		name: Name = classId.shortClassName,
		companionObjectSymbol: FirRegularClassSymbol? = null,
		superTypeRefs: context(FirRegularClassBuilder, SuperTypeRefsBuilder) () -> Unit = {
			+resolvedTypeRef(
				coneType = coneClassLikeType(StandardClassIds.Any)
			)
		},
		contextParameters: List<FirValueParameter> = emptyList(),
		block: FirRegularClassBuilder.() -> Unit = {},
	): FirRegularClassSymbol = file(
		packageDirective = buildPackageDirective {
			packageFqName = classId.packageFqName
		},
		declarations = {
			+regularClass(
				classId = classId,
				source = source,
				resolvePhase = resolvePhase,
				attributes = attributes,
				typeParameters = typeParameters,
				status = status,
				deprecationsProvider = deprecationsProvider,
				classKind = classKind,
				declarations = declarations,
				annotations = annotations,
				name = name,
				companionObjectSymbol = companionObjectSymbol,
				superTypeRefs = superTypeRefs,
				contextParameters = contextParameters,
				block = block,
			)
		},
		name = classId.shortClassName.asString()
	).declarations.single().symbol as FirRegularClassSymbol

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