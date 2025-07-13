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

import org.jetbrains.kotlin.backend.jvm.JvmIrSpecialAnnotationSymbolProvider
import org.jetbrains.kotlin.backend.jvm.classNameOverride
import org.jetbrains.kotlin.backend.jvm.createJvmFileFacadeClass
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.FilteredAnnotations
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.declarations.IrFactory
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrDelegatingConstructorCall
import org.jetbrains.kotlin.ir.expressions.impl.IrDelegatingConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.fromSymbolDescriptor
import org.jetbrains.kotlin.ir.util.DeclarationStubGenerator
import org.jetbrains.kotlin.ir.util.createThisReceiverParameter
import org.jetbrains.kotlin.load.java.JvmAnnotationNames
import org.jetbrains.kotlin.load.java.descriptors.JavaCallableMemberDescriptor
import org.jetbrains.kotlin.load.java.descriptors.JavaClassDescriptor
import org.jetbrains.kotlin.load.java.descriptors.getParentJavaStaticClassScope
import org.jetbrains.kotlin.load.java.sam.JavaSingleAbstractMethodUtils
import org.jetbrains.kotlin.load.java.typeEnhancement.hasEnhancedNullability
import org.jetbrains.kotlin.load.kotlin.FacadeClassSource
import org.jetbrains.kotlin.psi.KtPureClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.pureEndOffset
import org.jetbrains.kotlin.psi.psiUtil.pureStartOffset
import org.jetbrains.kotlin.psi2ir.generators.GeneratorContext
import org.jetbrains.kotlin.psi2ir.generators.GeneratorExtensions
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.resolve.descriptorUtil.resolveTopLevelClass
import org.jetbrains.kotlin.resolve.jvm.JAVA_LANG_RECORD_FQ_NAME
import org.jetbrains.kotlin.resolve.jvm.annotations.hasJvmFieldAnnotation
import org.jetbrains.kotlin.resolve.jvm.annotations.isJvmRecord
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DescriptorWithContainerSource
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DeserializedContainerSource
import org.jetbrains.kotlin.synthetic.SyntheticJavaPropertyDescriptor
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.replaceAnnotations

open class ClrGeneratorExtensionsImpl(
	private val configuration: CompilerConfiguration,
	private val generateFacades: Boolean = true,
) : GeneratorExtensions(), ClrGeneratorExtensions {
	override val irDeserializationEnabled: Boolean = configuration.get(JVMConfigurationKeys.SERIALIZE_IR) != JvmSerializeIrMode.NONE

	override val samConversion: SamConversion = JvmSamConversion()

	inner class JvmSamConversion : SamConversion() {
		override fun isPlatformSamType(type: KotlinType): Boolean =
			JavaSingleAbstractMethodUtils.isSamType(type)

		override fun isCarefulApproximationOfContravariantProjection(): Boolean =
			configuration.get(JVMConfigurationKeys.SAM_CONVERSIONS) != JvmClosureGenerationScheme.CLASS
	}

	override fun getContainerSource(descriptor: DeclarationDescriptor): DeserializedContainerSource? {
		return (descriptor as? DescriptorWithContainerSource)?.containerSource
	}

	override fun computeFieldVisibility(descriptor: PropertyDescriptor): DescriptorVisibility? =
		if (descriptor.hasJvmFieldAnnotation() || descriptor is JavaCallableMemberDescriptor)
			descriptor.visibility
		else
			null

	override fun computeExternalDeclarationOrigin(descriptor: DeclarationDescriptor): IrDeclarationOrigin? =
		if (descriptor is JavaCallableMemberDescriptor || descriptor is JavaClassDescriptor)
			IrDeclarationOrigin.IR_EXTERNAL_JAVA_DECLARATION_STUB
		else
			IrDeclarationOrigin.IR_EXTERNAL_DECLARATION_STUB

	override fun generateFacadeClass(
		irFactory: IrFactory,
		deserializedSource: DeserializedContainerSource,
		stubGenerator: DeclarationStubGenerator
	): IrClass? {
		if (!generateFacades || deserializedSource !is FacadeClassSource) return null
		val facadeName = deserializedSource.facadeClassName ?: deserializedSource.className
		return createJvmFileFacadeClass(
			if (deserializedSource.facadeClassName != null) IrDeclarationOrigin.JVM_MULTIFILE_CLASS else IrDeclarationOrigin.FILE_CLASS,
			facadeName.fqNameForTopLevelClassMaybeWithDollars.shortName(),
			deserializedSource,
			deserializeIr = { facade -> deserializeClass(facade, stubGenerator, facade.parent) }
		).also {
			it.createThisReceiverParameter()
			it.classNameOverride = facadeName
		}
	}

	override fun deserializeClass(
		irClass: IrClass,
		stubGenerator: DeclarationStubGenerator,
		parent: IrDeclarationParent,
	): Boolean = /*JvmIrDeserializerImpl().deserializeTopLevelClass(
		irClass, stubGenerator.irBuiltIns, stubGenerator.symbolTable, listOf(stubGenerator), this
	)*/
		TODO()

	override fun isPropertyWithPlatformField(descriptor: PropertyDescriptor): Boolean =
		descriptor.hasJvmFieldAnnotation()

	override val enhancedNullability: EnhancedNullability
		get() = JvmEnhancedNullability

	open class JvmEnhancedNullability : EnhancedNullability() {
		override fun hasEnhancedNullability(kotlinType: KotlinType): Boolean =
			kotlinType.hasEnhancedNullability()

		override fun stripEnhancedNullability(kotlinType: KotlinType): KotlinType =
			if (kotlinType.hasEnhancedNullability())
				kotlinType.replaceAnnotations(
					FilteredAnnotations(kotlinType.annotations, true) {
						it != JvmAnnotationNames.ENHANCED_NULLABILITY_ANNOTATION
					}
				)
			else
				kotlinType

		companion object Instance : JvmEnhancedNullability()
	}

	override fun getParentClassStaticScope(descriptor: ClassDescriptor): MemberScope? =
		descriptor.getParentJavaStaticClassScope()

	override fun createCustomSuperConstructorCall(
		ktPureClassOrObject: KtPureClassOrObject,
		descriptor: ClassDescriptor,
		context: GeneratorContext
	): IrDelegatingConstructorCall? {
		if (!descriptor.isJvmRecord()) return null

		val recordClass =
			// We assume j.l.Record is in the classpath because otherwise it should be a compile time error
			descriptor.module.resolveTopLevelClass(JAVA_LANG_RECORD_FQ_NAME, NoLookupLocation.FROM_BACKEND)
				?: error("Class not found: $JAVA_LANG_RECORD_FQ_NAME")

		val recordConstructor = recordClass.constructors.single()
		// OptIn is needed for the same as for Any constructor at BodyGenerator::generateAnySuperConstructorCall
		@OptIn(ObsoleteDescriptorBasedAPI::class)
		return IrDelegatingConstructorCallImpl.fromSymbolDescriptor(
			ktPureClassOrObject.pureStartOffset, ktPureClassOrObject.pureEndOffset,
			context.irBuiltIns.unitType,
			context.symbolTable.descriptorExtension.referenceConstructor(recordConstructor)
		)
	}

	override val shouldPreventDeprecatedIntegerValueTypeLiteralConversion: Boolean
		get() = true

	override fun generateFlexibleNullabilityAnnotationCall(): IrConstructorCall =
		JvmIrSpecialAnnotationSymbolProvider.generateFlexibleNullabilityAnnotationCall()

	override fun generateFlexibleMutabilityAnnotationCall(): IrConstructorCall =
		JvmIrSpecialAnnotationSymbolProvider.generateFlexibleMutabilityAnnotationCall()

	override fun generateEnhancedNullabilityAnnotationCall(): IrConstructorCall =
		JvmIrSpecialAnnotationSymbolProvider.generateEnhancedNullabilityAnnotationCall()

	override fun generateRawTypeAnnotationCall(): IrConstructorCall =
		JvmIrSpecialAnnotationSymbolProvider.generateRawTypeAnnotationCall()

	override fun unwrapSyntheticJavaProperty(descriptor: PropertyDescriptor): Pair<FunctionDescriptor, FunctionDescriptor?>? {
		if (descriptor is SyntheticJavaPropertyDescriptor) {
			return descriptor.getMethod to descriptor.setMethod
		}
		return null
	}

	override val parametersAreAssignable: Boolean
		get() = true

	override val debugInfoOnlyOnVariablesInDestructuringDeclarations: Boolean
		get() = true
}