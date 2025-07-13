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

package compiler.clr.backend.lower

import compiler.clr.backend.ClrBackendContext
import compiler.clr.backend.mapping.TypeStyle
import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.phaser.PhaseDescription
import org.jetbrains.kotlin.builtins.PrimitiveType
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.builtins.UnsignedType
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrPackageFragment
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.fromSymbolOwner
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.util.capitalizeDecapitalize.toLowerCaseAsciiOnly
import org.jetbrains.kotlin.utils.addToStdlib.assignFrom

@PhaseDescription(name = "JvmBuiltInsLowering")
internal class ClrBuiltInsLowering(val context: ClrBackendContext) : FileLoweringPass {
	@OptIn(UnsafeDuringIrConstructionAPI::class)
	override fun lower(irFile: IrFile) {
		val transformer = object : IrElementTransformerVoidWithContext() {
			override fun visitCall(expression: IrCall): IrExpression {
				expression.transformChildren(this, null)

				val callee = expression.symbol.owner
				val parentClassName = callee.parent.fqNameForIrSerialization.asString()
				val functionName = callee.name.asString()
				if (parentClassName == "kotlin.CompareToKt" && functionName == "compareTo") {
					val operandType = expression.arguments[0]!!.type
					when {
						operandType.isUInt() -> return expression.replaceWithCallTo(context.ir.symbols.compareUnsignedInt)
						operandType.isULong() -> return expression.replaceWithCallTo(context.ir.symbols.compareUnsignedLong)
					}
				}
				val jvm8Replacement = jvm8builtInReplacements[parentClassName to functionName]
				if (jvm8Replacement != null) {
					return expression.replaceWithCallTo(jvm8Replacement)
				}

				return when {
					callee.isArrayOf() ->
						expression.arguments[0]
							?: throw AssertionError("Argument #0 expected: ${expression.dump()}")

					/*callee.isEmptyArray() ->
						context.createJvmIrBuilder(currentScope!!, expression).irArrayOf(expression.type)*/

					else ->
						expression
				}
			}
		}

		irFile.transformChildren(transformer, null)
	}

	private val jvm8builtInReplacements = mapOf(
		("kotlin.UInt" to "compareTo") to context.ir.symbols.compareUnsignedInt,
		("kotlin.UInt" to "div") to context.ir.symbols.divideUnsignedInt,
		("kotlin.UInt" to "rem") to context.ir.symbols.remainderUnsignedInt,
		("kotlin.UInt" to "toString") to context.ir.symbols.toUnsignedStringInt,
		("kotlin.ULong" to "compareTo") to context.ir.symbols.compareUnsignedLong,
		("kotlin.ULong" to "div") to context.ir.symbols.divideUnsignedLong,
		("kotlin.ULong" to "rem") to context.ir.symbols.remainderUnsignedLong,
		("kotlin.ULong" to "toString") to context.ir.symbols.toUnsignedStringLong
	)

	// Originals are so far only instance methods and extensions, while the replacements are
	// statics, so we copy dispatch and extension receivers to a value argument if needed.
	// If we can't coerce arguments to required types, keep original expression (see below).
	@OptIn(UnsafeDuringIrConstructionAPI::class)
	private fun IrCall.replaceWithCallTo(replacement: IrSimpleFunctionSymbol): IrExpression {
		val expectedType = this.type
		val intrinsicCallType = replacement.owner.returnType

		val intrinsicCall = IrCallImpl.fromSymbolOwner(
			startOffset,
			endOffset,
			intrinsicCallType,
			replacement
		).also { newCall ->
			newCall.arguments.assignFrom(replacement.owner.parameters zip arguments) { (parameter, argument) ->
				argument!!.coerceIfPossible(parameter.type) ?: return this@replaceWithCallTo
			}
		}

		// Coerce intrinsic call result from JVM 'int' or 'long' to corresponding unsigned type if required.
		return if (intrinsicCallType.isInt() || intrinsicCallType.isLong()) {
			intrinsicCall.coerceIfPossible(expectedType)
				?: throw AssertionError("Can't coerce '${intrinsicCallType.render()}' to '${expectedType.render()}'")
		} else {
			intrinsicCall
		}
	}

	private fun IrExpression.coerceIfPossible(toType: IrType): IrExpression? {
		// TODO maybe UnsafeCoerce could handle types with different, but coercible underlying representations.
		// See KT-43286 and related tests for details.
		val fromJvmType = context.defaultTypeMapper.mapType(type, TypeStyle.Normal)
		val toJvmType = context.defaultTypeMapper.mapType(toType, TypeStyle.Normal)
		return if (fromJvmType != toJvmType)
			null
		else
			IrCallImpl.fromSymbolOwner(startOffset, endOffset, toType, context.ir.symbols.unsafeCoerceIntrinsic)
				.also { call ->
					call.typeArguments[0] = type
					call.typeArguments[1] = toType
					call.arguments[0] = this
				}
	}
}

internal val PRIMITIVE_ARRAY_OF_NAMES: Set<String> =
	(PrimitiveType.entries.map { type -> type.name } + UnsignedType.entries.map { type -> type.typeName.asString() })
		.map { name -> name.toLowerCaseAsciiOnly() + "ArrayOf" }.toSet()

internal const val ARRAY_OF_NAME = "arrayOf"

internal fun IrFunction.isArrayOf(): Boolean {
	val parent = when (val directParent = parent) {
		is IrClass -> directParent.getPackageFragment()
		is IrPackageFragment -> directParent
		else -> return false
	}
	return parent.packageFqName == StandardNames.BUILT_INS_PACKAGE_FQ_NAME &&
			name.asString().let { it in PRIMITIVE_ARRAY_OF_NAMES || it == ARRAY_OF_NAME } &&
			hasShape(regularParameters = 1) &&
			parameters[0].isVararg
}