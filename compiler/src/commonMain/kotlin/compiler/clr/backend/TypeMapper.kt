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

import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.isFunction
import org.jetbrains.kotlin.ir.util.isNullable
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty

enum class TypeStyle {
	ReturnType,
	TypeArgument,
	Property,
	NoModifier,
	Normal
}

@OptIn(UnsafeDuringIrConstructionAPI::class)
fun IrType.map(typeStyle: TypeStyle): String {
	if (typeStyle == TypeStyle.ReturnType) {
		if (isUnit() || isNothing()) {
			return "void"
		}
	}

	val classifier = classOrNull ?: return "global::System.Object?"
	val fqName = classifier.owner.fqNameWhenAvailable ?: return "global::System.Object?"

	return mapKotlinToCLRType(this, fqName, typeStyle)
}


@OptIn(UnsafeDuringIrConstructionAPI::class)
private fun mapKotlinToCLRType(type: IrType, fqName: FqName, typeStyle: TypeStyle): String {
	val result = when (fqName.asString()) {
		"kotlin.Annotation" -> "global::System.Attribute"
		"kotlin.Any" -> "global::System.Object"
		"kotlin.Array" -> when (val typeArgument = (type as IrSimpleType).arguments.single()) {
			is IrStarProjection -> "dynamic"
			is IrTypeProjection -> typeArgument.type.map(TypeStyle.ReturnType) + "[]"
		}

		"kotlin.ByteArray" -> "sbyte[]"
		"kotlin.CharArray" -> "char[]"
		"kotlin.ShortArray" -> "short[]"
		"kotlin.IntArray" -> "int[]"
		"kotlin.LongArray" -> "long[]"
		"kotlin.FloatArray" -> "float[]"
		"kotlin.DoubleArray" -> "double[]"
		"kotlin.BooleanArray" -> "bool[]"
		"kotlin.Boolean" -> "bool"
		"kotlin.Char" -> "char"
		"kotlin.CharSequence" -> "global::System.Collections.Generic.IEnumerable<char>"

		"kotlin.Comparable" -> "global::System.IComparable"
		"kotlin.Enum" -> "global::System.Enum"
		"kotlin.Number" -> "global::System.Numerics.INumber"
		"kotlin.Byte" -> "sbyte"
		"kotlin.Short" -> "short"
		"kotlin.Int" -> "int"
		"kotlin.Long" -> "long"
		"kotlin.Float" -> "float"
		"kotlin.Double" -> "double"
		"kotlin.String" -> "string"
		"kotlin.Throwable" -> "global::System.Exception"

		else if (type.isFunction() && type is IrSimpleType) -> when {
			type.arguments.last().typeOrFail.isUnit() -> buildString {
				append("global::System.Action")
				type.arguments.dropLast(1).ifNotEmpty {
					append("<")
					append(joinToString { it.typeOrFail.map(TypeStyle.TypeArgument) })
					append(">")
				}
			}

			else -> buildString {
				append("global::System.Func")
				append("<")
				append(type.arguments.joinToString { it.typeOrFail.map(TypeStyle.TypeArgument) })
				append(">")
			}
		}

		else -> buildString {
			append(
				when (fqName.asString()) {
					"kotlin.collections.Iterable" -> "global::System.Collections.Generic.IEnumerable"
					"kotlin.collections.MutableIterable" -> "/* TODO: MutableIterable */"
					"kotlin.collections.Collection" -> "global::System.Collections.Generic.IReadOnlyCollection"
					"kotlin.collections.MutableCollection" -> "global::System.Collections.Generic.ICollection"
					"kotlin.collections.List" -> "global::System.Collections.Generic.IReadOnlyList"
					"kotlin.collections.MutableList" -> "global::System.Collections.Generic.IList"
					"kotlin.collections.Set" -> "global::System.Collections.Generic.IReadOnlySet"
					"kotlin.collections.MutableSet" -> "global::System.Collections.Generic.ISet"
					"kotlin.collections.Map" -> "global::System.Collections.Generic.IReadOnlyDictionary"
					"kotlin.collections.MutableMap" -> "global::System.Collections.Generic.IDictionary"
					"kotlin.collections.Iterator" -> "global::kotlin.collections.KotlinIterator"
					"kotlin.collections.MutableIterator" -> "/* TODO: MutableIterator */"
					"kotlin.collections.ListIterator" -> "/* TODO: ListIterator */"
					"kotlin.collections.MutableListIterator" -> "/* TODO: MutableListIterator */"
					else -> "global::" + fqName.asString()
				}
			)
			if (type is IrSimpleType) {
				type.arguments.ifNotEmpty {
					append("<")
					append(joinToString { it.typeOrFail.map(TypeStyle.TypeArgument) })
					append(">")
				}
			}
		}
	}
	return when {
		typeStyle == TypeStyle.NoModifier -> result
		type.isNullable() -> "$result?"
		else -> when (typeStyle) {
			TypeStyle.ReturnType,
			TypeStyle.TypeArgument,
			TypeStyle.Property,
				-> result

			else -> "[global::kotlin.clr.KotlinNotNull] $result"
		}
	}
}