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

package compiler.clr.frontend.symbol.builder

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.KtSourceFile
import org.jetbrains.kotlin.KtSourceFileLinesMapping
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.fir.FirModuleData
import org.jetbrains.kotlin.fir.FirPackageDirective
import org.jetbrains.kotlin.fir.contracts.FirContractDescription
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.builder.*
import org.jetbrains.kotlin.fir.declarations.impl.FirDeclarationStatusImpl
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirBlock
import org.jetbrains.kotlin.fir.expressions.FirDelegatedConstructorCall
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.scopes.FirScopeProvider
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.fir.types.impl.ConeClassLikeTypeImpl
import org.jetbrains.kotlin.fir.types.impl.ConeTypeParameterTypeImpl
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DeserializedContainerSource
import org.jetbrains.kotlin.types.Variance

context(context: T)
fun <T> implicit(): T = context

class FirBuilderContext(
	val moduleData: FirModuleData,
	val scopeProvider: FirScopeProvider,
	val origin: FirDeclarationOrigin,
)

context(context: FirBuilderContext)
fun file(
	source: KtSourceElement? = null,
	resolvePhase: FirResolvePhase = FirResolvePhase.RAW_FIR,
	annotations: List<FirAnnotation> = emptyList(),
	attributes: FirDeclarationAttributes = FirDeclarationAttributes(),
	packageDirective: FirPackageDirective,
	imports: List<FirImport> = emptyList(),
	declarations: context(FirFileBuilder, DeclarationsBuilder) () -> Unit = {},
	name: String,
	sourceFile: KtSourceFile? = null,
	sourceFileLinesMapping: KtSourceFileLinesMapping? = null,
	symbol: FirFileSymbol = FirFileSymbol(),
	block: FirFileBuilder.() -> Unit = {},
) = buildFile {
	this.symbol = symbol
	this.source = source
	this.resolvePhase = resolvePhase
	this.annotations += annotations
	this.moduleData = context.moduleData
	this.origin = context.origin
	this.attributes = attributes
	this.packageDirective = packageDirective
	this.imports += imports
	this.declarations += DeclarationsBuilder().apply { declarations() }.list.toList()
	this.name = name
	this.sourceFile = sourceFile
	this.sourceFileLinesMapping = sourceFileLinesMapping
	block()
}

context(context: FirBuilderContext)
fun regularClass(
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
	symbol: FirRegularClassSymbol = FirRegularClassSymbol(classId),
	companionObjectSymbol: FirRegularClassSymbol? = null,
	superTypeRefs: context(FirRegularClassBuilder, SuperTypeRefsBuilder) () -> Unit = {
		+resolvedTypeRef(
			coneType = coneClassLikeType(StandardClassIds.Any)
		)
	},
	contextParameters: List<FirValueParameter> = emptyList(),
	block: FirRegularClassBuilder.() -> Unit = {},
) = buildRegularClass {
	this.symbol = symbol
	this.source = source
	this.resolvePhase = resolvePhase
	this.moduleData = context.moduleData
	this.origin = context.origin
	this.attributes = attributes
	this.typeParameters += TypeParametersBuilder().apply { typeParameters() }.list.toList()
	this.status = status
	this.deprecationsProvider = deprecationsProvider
	this.scopeProvider = context.scopeProvider
	this.classKind = classKind
	this.declarations += DeclarationsBuilder().apply { declarations() }.list.toList()
	this.annotations += annotations
	this.name = name
	this.companionObjectSymbol = companionObjectSymbol
	this.superTypeRefs += SuperTypeRefsBuilder().apply { superTypeRefs() }.list.toList()
	this.contextParameters += contextParameters
	block()
}

context(context: FirBuilderContext, builder: FirSimpleFunctionBuilder)
fun valueParameter(
	name: Name,
	source: KtSourceElement? = null,
	resolvePhase: FirResolvePhase = FirResolvePhase.RAW_FIR,
	attributes: FirDeclarationAttributes = FirDeclarationAttributes(),
	returnTypeRef: FirTypeRef,
	deprecationsProvider: DeprecationsProvider = UnresolvedDeprecationProvider,
	annotations: List<FirAnnotation> = emptyList(),
	symbol: FirValueParameterSymbol = FirValueParameterSymbol(name),
	defaultValue: FirExpression? = null,
	containingDeclarationSymbol: FirBasedSymbol<*> = builder.symbol,
	isCrossinline: Boolean = false,
	isNoinline: Boolean = false,
	isVararg: Boolean = false,
	valueParameterKind: FirValueParameterKind = FirValueParameterKind.Regular,
	block: FirValueParameterBuilder.() -> Unit = {},
) = buildValueParameter {
	this.symbol = symbol
	this.source = source
	this.resolvePhase = resolvePhase
	this.moduleData = context.moduleData
	this.origin = context.origin
	this.attributes = attributes
	this.returnTypeRef = returnTypeRef
	this.deprecationsProvider = deprecationsProvider
	this.name = name
	this.annotations += annotations
	this.defaultValue = defaultValue
	this.containingDeclarationSymbol = containingDeclarationSymbol
	this.isCrossinline = isCrossinline
	this.isNoinline = isNoinline
	this.isVararg = isVararg
	this.valueParameterKind = valueParameterKind
	block()
}

context(context: FirBuilderContext, builder: FirRegularClassBuilder)
fun constructor(
	classId: ClassId = builder.symbol.classId,
	source: KtSourceElement? = null,
	resolvePhase: FirResolvePhase = FirResolvePhase.RAW_FIR,
	attributes: FirDeclarationAttributes = FirDeclarationAttributes(),
	typeParameters: context(FirConstructorBuilder, TypeParametersBuilder) () -> Unit = {},
	status: FirDeclarationStatus = status(),
	returnTypeRef: FirTypeRef = resolvedTypeRef(
		coneType = coneClassLikeType(classId)
	),
	receiverParameter: FirReceiverParameter? = null,
	deprecationsProvider: DeprecationsProvider = UnresolvedDeprecationProvider,
	containerSource: DeserializedContainerSource? = null,
	dispatchReceiverType: ConeSimpleKotlinType? = null,
	contextParameters: List<FirValueParameter> = emptyList(),
	valueParameters: List<FirValueParameter> = emptyList(),
	contractDescription: FirContractDescription? = null,
	annotations: List<FirAnnotation> = emptyList(),
	symbol: FirConstructorSymbol = FirConstructorSymbol(classId),
	delegatedConstructor: FirDelegatedConstructorCall? = null,
	body: FirBlock? = null,
	block: FirConstructorBuilder.() -> Unit = {},
) = buildConstructor {
	this.symbol = symbol
	this.source = source
	this.resolvePhase = resolvePhase
	this.moduleData = context.moduleData
	this.origin = context.origin
	this.attributes = attributes
	this.typeParameters += TypeParametersBuilder().apply { typeParameters() }.list.toList()
	this.status = status
	this.returnTypeRef = returnTypeRef
	this.receiverParameter = receiverParameter
	this.deprecationsProvider = deprecationsProvider
	this.containerSource = containerSource
	this.dispatchReceiverType = dispatchReceiverType
	this.contextParameters += contextParameters
	this.valueParameters += valueParameters
	this.contractDescription = contractDescription
	this.annotations += annotations
	this.delegatedConstructor = delegatedConstructor
	this.body = body
	block()
}

context(context: FirBuilderContext, builder: FirRegularClassBuilder)
fun primaryConstructor(
	classId: ClassId = builder.symbol.classId,
	source: KtSourceElement? = null,
	resolvePhase: FirResolvePhase = FirResolvePhase.RAW_FIR,
	attributes: FirDeclarationAttributes = FirDeclarationAttributes(),
	typeParameters: context(FirPrimaryConstructorBuilder, TypeParametersBuilder) () -> Unit = {},
	status: FirDeclarationStatus = status(),
	returnTypeRef: FirTypeRef = resolvedTypeRef(
		coneType = coneClassLikeType(classId)
	),
	receiverParameter: FirReceiverParameter? = null,
	deprecationsProvider: DeprecationsProvider = UnresolvedDeprecationProvider,
	containerSource: DeserializedContainerSource? = null,
	dispatchReceiverType: ConeSimpleKotlinType? = null,
	contextParameters: List<FirValueParameter> = emptyList(),
	valueParameters: List<FirValueParameter> = emptyList(),
	contractDescription: FirContractDescription? = null,
	annotations: List<FirAnnotation> = emptyList(),
	symbol: FirConstructorSymbol = FirConstructorSymbol(classId),
	delegatedConstructor: FirDelegatedConstructorCall? = null,
	body: FirBlock? = null,
	block: FirPrimaryConstructorBuilder.() -> Unit = {},
) = buildPrimaryConstructor {
	this.symbol = symbol
	this.source = source
	this.resolvePhase = resolvePhase
	this.moduleData = context.moduleData
	this.origin = context.origin
	this.attributes = attributes
	this.typeParameters += TypeParametersBuilder().apply { typeParameters() }.list.toList()
	this.status = status
	this.returnTypeRef = returnTypeRef
	this.receiverParameter = receiverParameter
	this.deprecationsProvider = deprecationsProvider
	this.containerSource = containerSource
	this.dispatchReceiverType = dispatchReceiverType
	this.contextParameters += contextParameters
	this.valueParameters += valueParameters
	this.contractDescription = contractDescription
	this.annotations += annotations
	this.delegatedConstructor = delegatedConstructor
	this.body = body
	block()
}

context(context: FirBuilderContext, builder: FirRegularClassBuilder)
fun simpleFunction(
	name: Name,
	callableId: CallableId = CallableId(builder.symbol.classId, name),
	source: KtSourceElement? = null,
	resolvePhase: FirResolvePhase = FirResolvePhase.RAW_FIR,
	attributes: FirDeclarationAttributes = FirDeclarationAttributes(),
	status: FirDeclarationStatus = status(),
	returnTypeRef: FirTypeRef,
	receiverParameter: FirReceiverParameter? = null,
	deprecationsProvider: DeprecationsProvider = UnresolvedDeprecationProvider,
	containerSource: DeserializedContainerSource? = null,
	dispatchReceiverType: ConeSimpleKotlinType? = null,
	contextParameters: List<FirValueParameter> = emptyList(),
	valueParameters: context(FirSimpleFunctionBuilder, ValueParametersBuilder) () -> Unit = {},
	body: FirBlock? = null,
	contractDescription: FirContractDescription? = null,
	symbol: FirNamedFunctionSymbol = FirNamedFunctionSymbol(callableId),
	annotations: List<FirAnnotation> = emptyList(),
	typeParameters: context(FirSimpleFunctionBuilder, TypeParametersBuilder) () -> Unit = {},
	block: FirSimpleFunctionBuilder.() -> Unit = {},
) = buildSimpleFunction {
	this.symbol = symbol
	this.source = source
	this.resolvePhase = resolvePhase
	this.moduleData = context.moduleData
	this.origin = context.origin
	this.attributes = attributes
	this.status = status
	this.returnTypeRef = returnTypeRef
	this.receiverParameter = receiverParameter
	this.deprecationsProvider = deprecationsProvider
	this.containerSource = containerSource
	this.dispatchReceiverType = dispatchReceiverType
	this.contextParameters += contextParameters
	this.valueParameters += ValueParametersBuilder().apply { valueParameters() }.list.toList()
	this.body = body
	this.contractDescription = contractDescription
	this.name = name
	this.annotations += annotations
	this.typeParameters += TypeParametersBuilder().apply { typeParameters() }.list.toList()
	block()
}

context(context: FirBuilderContext)
fun property(
	name: Name,
	source: KtSourceElement? = null,
	resolvePhase: FirResolvePhase = FirResolvePhase.RAW_FIR,
	attributes: FirDeclarationAttributes = FirDeclarationAttributes(),
	status: FirDeclarationStatus = status(),
	returnTypeRef: FirTypeRef,
	receiverParameter: FirReceiverParameter? = null,
	deprecationsProvider: DeprecationsProvider = UnresolvedDeprecationProvider,
	containerSource: DeserializedContainerSource? = null,
	dispatchReceiverType: ConeSimpleKotlinType? = null,
	contextParameters: List<FirValueParameter> = emptyList(),
	initializer: FirExpression? = null,
	delegate: FirExpression? = null,
	isVar: Boolean = false,
	getter: FirPropertyAccessor? = null,
	setter: FirPropertyAccessor? = null,
	backingField: FirBackingField? = null,
	annotations: List<FirAnnotation> = emptyList(),
	symbol: FirPropertySymbol = FirPropertySymbol(name),
	delegateFieldSymbol: FirDelegateFieldSymbol? = null,
	isLocal: Boolean = false,
	bodyResolveState: FirPropertyBodyResolveState = FirPropertyBodyResolveState.NOTHING_RESOLVED,
	typeParameters: context(FirPropertyBuilder, TypeParametersBuilder) () -> Unit = {},
	block: FirPropertyBuilder.() -> Unit = {},
) = buildProperty {
	this.symbol = symbol
	this.source = source
	this.resolvePhase = resolvePhase
	this.moduleData = context.moduleData
	this.origin = context.origin
	this.attributes = attributes
	this.status = status
	this.returnTypeRef = returnTypeRef
	this.receiverParameter = receiverParameter
	this.deprecationsProvider = deprecationsProvider
	this.containerSource = containerSource
	this.dispatchReceiverType = dispatchReceiverType
	this.contextParameters += contextParameters
	this.name = name
	this.initializer = initializer
	this.delegate = delegate
	this.isVar = isVar
	this.getter = getter
	this.setter = setter
	this.backingField = backingField
	this.annotations += annotations
	this.delegateFieldSymbol = delegateFieldSymbol
	this.isLocal = isLocal
	this.bodyResolveState = bodyResolveState
	this.typeParameters += TypeParametersBuilder().apply { typeParameters() }.list.toList()
	block()
}

context(context: FirBuilderContext)
fun typeParameter(
	source: KtSourceElement? = null,
	resolvePhase: FirResolvePhase = FirResolvePhase.RAW_FIR,
	attributes: FirDeclarationAttributes = FirDeclarationAttributes(),
	name: Name,
	symbol: FirTypeParameterSymbol = FirTypeParameterSymbol(),
	containingDeclarationSymbol: FirBasedSymbol<*>,
	variance: Variance = Variance.INVARIANT,
	isReified: Boolean = false,
	bounds: List<FirTypeRef> = listOf(
		resolvedTypeRef(
			coneType = coneClassLikeType(
				classId = StandardClassIds.Any,
				isMarkedNullable = true
			)
		)
	),
	annotations: List<FirAnnotation> = emptyList(),
	block: FirTypeParameterBuilder.() -> Unit = {},
) = buildTypeParameter {
	this.symbol = symbol
	this.source = source
	this.resolvePhase = resolvePhase
	this.moduleData = context.moduleData
	this.origin = context.origin
	this.attributes = attributes
	this.name = name
	this.containingDeclarationSymbol = containingDeclarationSymbol
	this.variance = variance
	this.isReified = isReified
	this.bounds += bounds
	this.annotations += annotations
	block()
}

context(context: FirBuilderContext)
fun receiverParameter(
	source: KtSourceElement? = null,
	resolvePhase: FirResolvePhase = FirResolvePhase.RAW_FIR,
	attributes: FirDeclarationAttributes = FirDeclarationAttributes(),
	symbol: FirReceiverParameterSymbol = FirReceiverParameterSymbol(),
	typeRef: FirTypeRef,
	containingDeclarationSymbol: FirBasedSymbol<*>,
	annotations: List<FirAnnotation> = emptyList(),
	block: FirReceiverParameterBuilder.() -> Unit = {}
) = buildReceiverParameter {
	this.symbol = symbol
	this.source = source
	this.resolvePhase = resolvePhase
	this.moduleData = context.moduleData
	this.origin = context.origin
	this.attributes = attributes
	this.typeRef = typeRef
	this.containingDeclarationSymbol = containingDeclarationSymbol
	this.annotations += annotations
	block()
}

context(context: FirBuilderContext)
fun status(
	visibility: Visibility = Visibilities.DEFAULT_VISIBILITY,
	modality: Modality = Modality.FINAL,
	isExpect: Boolean = false,
	isActual: Boolean = false,
	isOverride: Boolean = false,
	isOperator: Boolean = false,
	isInfix: Boolean = false,
	isInline: Boolean = false,
	isValue: Boolean = false,
	isTailRec: Boolean = false,
	isExternal: Boolean = false,
	isConst: Boolean = false,
	isLateInit: Boolean = false,
	isInner: Boolean = false,
	isCompanion: Boolean = false,
	isData: Boolean = false,
	isSuspend: Boolean = false,
	isStatic: Boolean = false,
	isFromSealedClass: Boolean = false,
	isFromEnumClass: Boolean = false,
	isFun: Boolean = false,
	hasStableParameterNames: Boolean = false,
	effectiveVisibility: EffectiveVisibility = EffectiveVisibility.Public,
) = FirDeclarationStatusImpl(visibility, modality).apply {
	this.isExpect = isExpect
	this.isActual = isActual
	this.isOverride = isOverride
	this.isOperator = isOperator
	this.isInfix = isInfix
	this.isInline = isInline
	this.isValue = isValue
	this.isTailRec = isTailRec
	this.isExternal = isExternal
	this.isConst = isConst
	this.isLateInit = isLateInit
	this.isInner = isInner
	this.isCompanion = isCompanion
	this.isData = isData
	this.isSuspend = isSuspend
	this.isStatic = isStatic
	this.isFromSealedClass = isFromSealedClass
	this.isFromEnumClass = isFromEnumClass
	this.isFun = isFun
	this.hasStableParameterNames = hasStableParameterNames
}.resolved(visibility, modality, effectiveVisibility)

context(context: FirBuilderContext)
fun resolvedTypeRef(
	source: KtSourceElement? = null,
	annotations: List<FirAnnotation> = emptyList(),
	coneType: ConeKotlinType,
	delegatedTypeRef: FirTypeRef? = null,
) = buildResolvedTypeRef {
	this.source = source
	this.annotations += annotations
	this.coneType = coneType
	this.delegatedTypeRef = delegatedTypeRef
}

context(context: FirBuilderContext)
fun coneClassLikeType(
	classId: ClassId,
	typeArguments: Array<out ConeTypeProjection> = emptyArray(),
	isMarkedNullable: Boolean = false,
	attributes: ConeAttributes = ConeAttributes.Empty,
) = ConeClassLikeTypeImpl(classId.toLookupTag(), typeArguments, isMarkedNullable, attributes)

context(context: FirBuilderContext)
fun coneTypeParameterType(
	symbol: FirTypeParameterSymbol,
	isMarkedNullable: Boolean = false,
	attributes: ConeAttributes = ConeAttributes.Empty,
) = ConeTypeParameterTypeImpl(symbol.toLookupTag(), isMarkedNullable, attributes)