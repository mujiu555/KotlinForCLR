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

package compiler.clr.backend.codegen

import compiler.clr.backend.ClrBackendContext
import compiler.clr.backend.TypeStyle
import compiler.clr.backend.map
import org.jetbrains.kotlin.DeprecatedForRemovalCompilerApi
import org.jetbrains.kotlin.descriptors.ClassKind.*
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Modality.*
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.fir.lazy.AbstractFir2IrLazyFunction
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.symbols.*
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.isByte
import org.jetbrains.kotlin.ir.types.isChar
import org.jetbrains.kotlin.ir.types.isDouble
import org.jetbrains.kotlin.ir.types.isFloat
import org.jetbrains.kotlin.ir.types.isInt
import org.jetbrains.kotlin.ir.types.isLong
import org.jetbrains.kotlin.ir.types.isNothing
import org.jetbrains.kotlin.ir.types.isNumber
import org.jetbrains.kotlin.ir.types.isShort
import org.jetbrains.kotlin.ir.types.isUnit
import org.jetbrains.kotlin.ir.types.typeOrFail
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.javac.resolve.classId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

fun <T> List<T>.join(separator: T): List<T> = when {
	isEmpty() -> this
	else -> zipWithNext { node, _ -> listOf(node, separator) }.flatten() + last()
}

@OptIn(UnsafeDuringIrConstructionAPI::class)
class ClassCodegen(val context: ClrBackendContext) {
	fun IrFile.visit(): BackendIR {
		val `package` = packageFqName

		val content = multiLineIR(
			*declarations
				.map { declaration ->
					when (declaration) {
						is IrClass -> declaration.visit()
						else -> multiLinePlain(
							"/*",
							"Unsupported declaration: ${declaration::class.java.simpleName}",
							"at IrFile.visit: $this",
							"*/",
						)
					}
				}
				.join(noneIR)
				.toTypedArray()
		).let {
			when (it.nodes.size == 1) {
				true -> it.nodes.single()
				else -> it
			}
		}

		return when (!`package`.isRoot) {
			true -> multiLineIR(
				singleLinePlain("namespace $`package`"),
				blockPadding(content),
			)

			else -> content
		}
	}

	fun IrClass.visit(): BackendIR = when {
		isFileClass -> visitFileClass()
		kind == CLASS -> visitClass()
		kind == INTERFACE -> visitInterface()
		kind == ENUM_CLASS -> visitEnumClass()
		kind == ENUM_ENTRY -> multiLinePlain(
			"/*",
			"TODO enum entry",
			"at IrClass.visit: $this",
			"*/",
		)

		kind == ANNOTATION_CLASS -> visitAnnotationClass()
		kind == OBJECT -> visitObject()
		else -> multiLinePlain(
			"/*",
			"Unknown class type",
			"at IrClass.visit: $this",
			"*/",
		)
	}

	fun IrClass.visitFileClass() = multiLineIR(
		singleLinePlain(
			"[global::kotlin.clr.KotlinFileClass]"
		),
		singleLineIR(
			visibility.delegate.visit(),
			plainIR("static "),
			plainIR("class "),
			plainIR(name.visit())
		),
		blockPadding(
			buildList {
				declarations
					.filterIsInstance<IrConstructor>()
					.map { it.visit() }
					.forEach { add(it) }
				declarations
					.filterIsInstance<IrProperty>()
					.mapNotNull { it.backingField }
					.map { it.visit() }
					.forEach { add(it) }
				declarations
					.filterNot { it is IrConstructor }
					.mapNotNull { it.visit() }
					.forEach { add(it) }
			}.join(noneIR)
		),
	)

	fun IrClass.visitClass() = multiLineIR(
		singleLineIR(
			visibility.delegate.visit(),
			modality.visit(),
			plainIR("class "),
			plainIR(name.visit()),
			plainIR(" : "),
			plainIR(superTypes.joinToString(", ") { it.map(TypeStyle.NoModifier) }),
		),
		blockPadding(
			buildList {
				declarations
					.filterIsInstance<IrConstructor>()
					.map { it.visit() }
					.forEach { add(it) }
				declarations
					.filterIsInstance<IrProperty>()
					.mapNotNull { it.backingField }
					.map { it.visit() }
					.forEach { add(it) }
				declarations
					.filterNot { it is IrConstructor }
					.mapNotNull { it.visit() }
					.forEach { add(it) }
			}.join(noneIR)
		),
	)

	fun IrClass.visitInterface() = multiLineIR(
		singleLineIR(
			buildList {
				add(visibility.delegate.visit())
				add(modality.visit())
				add(plainIR("interface "))
				add(plainIR(name.visit()))
				if (superTypes.isNotEmpty()) {
					add(plainIR(" : "))
					add(plainIR(superTypes.joinToString(", ") { it.map(TypeStyle.NoModifier) }))
				}
			}
		),
		blockPadding(
			buildList {
				declarations
					.filterIsInstance<IrConstructor>()
					.map { it.visit() }
					.forEach { add(it) }
				declarations
					.filterIsInstance<IrProperty>()
					.mapNotNull { it.backingField }
					.map { it.visit() }
					.forEach { add(it) }
				declarations
					.filterNot { it is IrConstructor }
					.mapNotNull { it.visit() }
					.forEach { add(it) }
			}.join(noneIR)
		),
	)

	fun IrClass.visitEnumClass() = multiLineIR(
		singleLineIR(
			visibility.delegate.visit(),
			modality.visit(),
			plainIR("enum "),
			plainIR(name.visit()),
		),
		blockPadding(
			buildList {
				declarations
					.filterIsInstance<IrConstructor>()
					.map { it.visit() }
					.forEach { add(it) }
				declarations
					.filterIsInstance<IrProperty>()
					.mapNotNull { it.backingField }
					.map { it.visit() }
					.forEach { add(it) }
				declarations
					.filterNot { it is IrConstructor }
					.mapNotNull { it.visit() }
					.forEach { add(it) }
			}.join(noneIR)
		),
	)

	fun IrClass.visitAnnotationClass() = multiLineIR(
		singleLineIR(
			visibility.delegate.visit(),
			modality.visit(),
			plainIR("class "),
			plainIR(name.visit()),
			plainIR(" : global::System.Attribute"),
		),
		blockPadding(
			buildList {
				declarations
					.filterIsInstance<IrConstructor>()
					.map { it.visit() }
					.forEach { add(it) }
				declarations
					.filterIsInstance<IrProperty>()
					.mapNotNull { it.backingField }
					.map { it.visit() }
					.forEach { add(it) }
				declarations
					.filterNot { it is IrConstructor }
					.mapNotNull { it.visit() }
					.forEach { add(it) }
			}.join(noneIR)
		),
	)

	fun IrClass.visitObject() = multiLineIR(
		singleLineIR(
			visibility.delegate.visit(),
			modality.visit(),
			plainIR("class "),
			plainIR(name.visit()),
			plainIR(" : "),
			plainIR(superTypes.joinToString(", ") { it.map(TypeStyle.NoModifier) }),
		),
		blockPadding(
			buildList {
				add(
					multiLineListIR(
						buildList {
							if (!defaultType.isNullable()) {
								add(singleLinePlain("[global::kotlin.clr.KotlinNotNull]"))
							}
							add(
								singleLinePlain(
									"public static ",
									defaultType.map(TypeStyle.Property),
									" INSTANCE { get; } = new ",
									defaultType.map(TypeStyle.NoModifier),
									"();",
								)
							)
						}
					)
				)
				declarations
					.filterIsInstance<IrConstructor>()
					.map { it.visit() }
					.forEach { add(it) }
				declarations
					.filterIsInstance<IrProperty>()
					.mapNotNull { it.backingField }
					.map { it.visit() }
					.forEach { add(it) }
				declarations
					.filterNot { it is IrConstructor }
					.mapNotNull { it.visit() }
					.forEach { add(it) }
			}.join(noneIR)
		),
	)

	fun IrDeclaration.visit(): BackendIR? {
		if (isFakeOverride) return null
		return when (this) {
			is IrClass -> visit()
			is IrConstructor -> visit()
			is IrFunction -> visit()
			is IrProperty -> visit()
			is IrVariable -> visit()
			is IrTypeParameter -> visit()
			else -> multiLinePlain(
				"/*",
				"Unsupported declaration: ${this::class.java.simpleName}",
				"at IrDeclaration.visit",
				"*/"
			)
		}
	}

	fun IrFunction.visit() = multiLineIR(
		buildList {
			when (returnType.isNothing()) {
				true -> add(
					singleLineIR(plainIR("[global::System.Diagnostics.CodeAnalysis.DoesNotReturnAttribute]"))
				)

				else -> when {
					!returnType.isNullable() && !returnType.isUnit() -> add(
						singleLineIR(plainIR("[global::kotlin.clr.KotlinNotNull]"))
					)
				}
			}
			if (parameters.firstOrNull { it.kind == IrParameterKind.ExtensionReceiver } != null) {
				add(singleLineIR(plainIR("[global::kotlin.clr.KotlinExtension]")))
			}
			add(
				singleLineIR(
					buildList {
						val isStatic = when {
							parent is IrFile -> true
							isStatic -> true
							else -> false
						}

						val returnType = returnType.map(TypeStyle.ReturnType)

						val parameters = parameters.filter {
							it.kind == IrParameterKind.Regular || it.kind == IrParameterKind.Context
						}.map {
							it.type.map(TypeStyle.Normal) to it.name.visit()
						}

						add(visibility.delegate.visit())
						if (isStatic) {
							add(plainIR("static "))
						}
						if (this@visit is IrSimpleFunction && this@visit.overriddenSymbols.isNotEmpty()) {
							add(plainIR("override "))
						}
						add(plainIR("$returnType "))
						add(plainIR("${name.visit()}("))
						this@visit.parameters.firstOrNull { it.kind == IrParameterKind.ExtensionReceiver }?.let {
							add(plainIR("${it.type.map(TypeStyle.Normal)} receiver, "))
						}
						add(plainIR(parameters.joinToString(", ") { "${it.first} ${it.second}" }))
						add(plainIR(")"))
					}
				)
			)
			add(body?.visit() ?: blockPadding())
		}
	)

	fun IrConstructor.visit() = multiLineIR(
		singleLineIR(
			buildList {
				val className = (parent as? IrClass)?.name?.visit()!!

				val parameters = parameters.filter {
					it.kind == IrParameterKind.Regular || it.kind == IrParameterKind.Context
				}.map {
					it.type.map(TypeStyle.Normal) to it.name.visit()
				}

				add(visibility.delegate.visit())
				add(plainIR("$className("))
				add(plainIR(parameters.joinToString(", ") { "${it.first} ${it.second}" }))
				add(plainIR(")"))
				if (body?.statements?.get(0) is IrDelegatingConstructorCall) {
					add(plainIR(" : "))
					add((body?.statements?.get(0) as IrDelegatingConstructorCall).visit())
				}
			}
		),
		blockPadding(
			buildList {
				body?.visit()?.let {
					add(it)
				}
				addAll(
					parentAsClass.declarations
						.filterIsInstance<IrProperty>()
						.map { it to it.backingField?.initializer?.expression }
						.filter { it.second != null }
						.map { it.first to it.second!! }
						.map { (property, initializer) ->
							singleLineIR(
								plainIR("this.${property.name.visit()}_backingField = "),
								initializer.visitNeedReturn(),
								plainIR(";")
							)
						}
				)
			}
		),
	)

	fun IrField.visit() = multiLineIR(
		buildList {
			if (!type.isNullable()) {
				add(singleLineIR(plainIR("[global::kotlin.clr.KotlinNotNull]")))
			}
			add(
				singleLineListIR(
					buildList {
						add(plainIR("private "))
						if (isStatic) {
							add(plainIR("static "))
						}

						add(plainIR(type.map(TypeStyle.Property)))
						add(plainIR(" "))

						add(plainIR(name.visit() + "_backingField"))
						add(plainIR(";"))
					}
				)
			)
		}
	)

	fun IrProperty.visit() = multiLineIR(
		buildList {
			val type = getter?.returnType
				?: setter?.returnType
				?: error("Property must have either getter or setter: $this")

			if (!type.isNullable()) {
				add(singleLineIR(plainIR("[global::kotlin.clr.KotlinNotNull]")))
			}
			add(
				singleLineIR(
					buildList {
						val isStatic = when {
							parent is IrFile -> true
							(backingField?.isStatic ?: getter?.isStatic ?: setter?.isStatic) == true -> true
							else -> false
						}
						add(visibility.delegate.visit())
						if (isStatic) {
							add(plainIR("static "))
						}
						when (modality) {
							FINAL -> {}
							SEALED -> add(
								multiLinePlain(
									"/*",
									"TODO sealed",
									"at IrProperty.visit: ${this@visit}",
									"*/"
								)
							)

							OPEN -> add(plainIR("virtual "))
							ABSTRACT -> add(plainIR("abstract "))
						}

						add(plainIR(type.map(TypeStyle.ReturnType)))
						add(plainIR(" "))

						add(plainIR(name.visit()))
					}
				)
			)
			add(
				blockPadding(
					multiLineIR(
						buildList {
							getter?.let { getter ->
								if (getter.visibility.delegate != visibility.delegate) {
									add(getter.visibility.delegate.visit())
								}
								add(plainIR("get"))
								add(getter.body!!.visit()!!)
							}
							setter?.let { setter ->
								if (setter.visibility.delegate != visibility.delegate) {
									add(setter.visibility.delegate.visit())
								}
								add(plainIR("set"))
								add(setter.body!!.visit()!!)
							}
						}
					)
				)
			)
		}
	)

	fun IrBody.visit() = when (this) {
		is IrBlockBody -> statements
			.filterNot { it is IrDelegatingConstructorCall }
			.filterNot { it is IrInstanceInitializerCall }
			.map {
				when (it) {
					is IrWhen -> it.visit()
					is IrExpression -> singleLineIR(it.visit(), plainIR(";"))
					is IrVariable -> it.visit().appendSingleLine(plainIR(";"))
					else -> multiLinePlain(
						"/*",
						"Unsupported statement: ${it::class.java.simpleName}",
						"at IrBody.visit: $this",
						"is IrBlockBody",
						"*/",
					)
				}
			}
			.let {
				when (it.isEmpty()) {
					true -> null
					else -> blockPadding(it)
				}
			}

		else -> statements
			.filterNot { it is IrDelegatingConstructorCall }
			.filterNot { it is IrInstanceInitializerCall }
			.map {
				when (it) {
					is IrWhen -> it.visit()
					is IrExpression -> singleLineIR(it.visit(), plainIR(";"))
					is IrVariable -> it.visit().appendSingleLine(plainIR(";"))
					else -> multiLinePlain(
						"/*",
						"Unsupported statement: ${it::class.java.simpleName}",
						"at IrBody.visit: $this",
						"*/",
					)
				}
			}
			.let {
				when (it.isEmpty()) {
					true -> null
					else -> multiLineIR(it)
				}
			}
	}

	fun IrDelegatingConstructorCall.visit() = singleLineListIR(
		plainIR("base("),
		*valueArguments
			.filterNotNull()
			.map { it.visitNeedReturn() }
			.join(plainIR(", "))
			.toTypedArray(),
		plainIR(")"),
	)

	fun IrGetObjectValue.visit() = singleLineListIR(
		plainIR(symbol.owner.defaultType.map(TypeStyle.NoModifier)),
		plainIR(".INSTANCE"),
	)

	fun IrConstructorCall.visit(): BackendIR = singleLineListIR(
		buildList {
			val constructedClass = symbol.owner.parent as IrClass
			add(plainIR("new "))
			add(plainIR(constructedClass.defaultType.map(TypeStyle.NoModifier)))
			add(plainIR("("))
			valueArguments
				.filterNotNull()
				.map { it.visitNeedReturn() }
				.join(plainIR(", "))
				.forEach { add(it) }
			add(plainIR(")"))
		}
	)

	fun IrCall.visitClassParent(): BackendIR {
		val function = symbol.owner
		val parent = function.parent as IrClass

		val isStatic = function.isStatic
		val isCompanionStatic = (function as? AbstractFir2IrLazyFunction<*>)?.fir?.annotations?.any {
			it.annotationTypeRef.coneType.classId == classId("kotlin.clr", "ClrStatic")
		} == true

		return when {
			isStatic -> singleLineListIR(
				buildList {
					add(plainIR(parent.defaultType.map(TypeStyle.NoModifier)))
					add(plainIR("."))
					when (function.name.isSpecial) {
						true -> {
							val name = function.name.visit()

							when {
								name.startsWith("<set-") -> {
									add(plainIR(name.substring("<set-".length, name.length - 1)))
									add(plainIR(" = "))
									add(valueArguments[0]!!.visitNeedReturn())
								}

								name.startsWith("<get-") -> {
									add(plainIR(name.substring("<get-".length, name.length - 1)))
								}

								else -> add(
									multiLinePlain(
										"/*",
										"Unsupported name: $name",
										"at IrCall.visitClassParent: ${this@visitClassParent}",
										"is static",
										"is special",
										"*/",
									)
								)
							}
						}

						else -> {
							add(plainIR(function.name.visit()))
							if (typeArguments.isNotEmpty()) {
								add(plainIR("<"))
								add(
									singleLineListIR(
										typeArguments.map {
											it?.map(TypeStyle.TypeArgument) ?: "global::System.Object"
										}.map {
											plainIR(it)
										}
									)
								)
								add(plainIR(">"))
							}
							add(plainIR("("))
							listOfNotNull(
								arguments.getOrNull(symbol.owner.parameters.indexOfFirst {
									it.kind == IrParameterKind.ExtensionReceiver
								}),
								*valueArguments.toTypedArray()
							)
								.map { it.visitNeedReturn() }
								.join(plainIR(", "))
								.forEach { add(it) }
							add(plainIR(")"))
						}
					}
				}
			)

			isCompanionStatic -> singleLineListIR(
				buildList {
					val outer = parent.parent as? IrClass ?: return multiLinePlain(
						"/*",
						"Expected IrClass but got ${parent::class.java.simpleName}",
						"at IrCall.visitClassParent: ${this@visitClassParent}",
						"is companion static",
						"*/",
					)
					add(plainIR(outer.defaultType.map(TypeStyle.NoModifier)))
					add(plainIR("."))
					when (function.name.isSpecial) {
						true -> {
							val name = function.name.visit()
							when {
								name.startsWith("<set-") -> {
									add(plainIR(name.substring("<set-".length, name.length - 1)))
									add(plainIR(" = "))
									add(valueArguments[0]!!.visitNeedReturn())
								}

								name.startsWith("<get-") -> {
									add(plainIR(name.substring("<get-".length, name.length - 1)))
								}

								else -> add(
									multiLinePlain(
										"/*",
										"Unsupported name: $name",
										"at IrCall.visitClassParent: ${this@visitClassParent}",
										"is companion static",
										"is special",
										"*/",
									)
								)
							}
						}

						else -> {
							add(plainIR(function.name.visit()))
							if (typeArguments.isNotEmpty()) {
								add(plainIR("<"))
								add(
									singleLineListIR(
										typeArguments.map {
											it?.map(TypeStyle.TypeArgument) ?: "global::System.Object"
										}.map {
											plainIR(it)
										}
									)
								)
								add(plainIR(">"))
							}
							add(plainIR("("))
							listOfNotNull(
								arguments.getOrNull(symbol.owner.parameters.indexOfFirst {
									it.kind == IrParameterKind.ExtensionReceiver
								}),
								*valueArguments.toTypedArray()
							)
								.map { it.visitNeedReturn() }
								.join(plainIR(", "))
								.forEach { add(it) }
							add(plainIR(")"))
						}
					}
				}
			)

			else -> when {
				function.isOperator -> when (function.name.visit()) {
					"plus" -> singleLineListIR(
						plainIR("("),
						arguments[0]!!.visitNeedReturn(),
						plainIR(")"),
						plainIR(" + "),
						plainIR("("),
						arguments[1]!!.visitNeedReturn(),
						plainIR(")"),
					)

					"minus" -> singleLineListIR(
						plainIR("("),
						arguments[0]!!.visitNeedReturn(),
						plainIR(")"),
						plainIR(" - "),
						plainIR("("),
						arguments[1]!!.visitNeedReturn(),
						plainIR(")"),
					)

					"times" -> singleLineListIR(
						plainIR("("),
						arguments[0]!!.visitNeedReturn(),
						plainIR(")"),
						plainIR(" * "),
						plainIR("("),
						arguments[1]!!.visitNeedReturn(),
						plainIR(")"),
					)

					"div" -> singleLineListIR(
						plainIR("("),
						arguments[0]!!.visitNeedReturn(),
						plainIR(")"),
						plainIR(" / "),
						plainIR("("),
						arguments[1]!!.visitNeedReturn(),
						plainIR(")"),
					)

					"rem" -> singleLineListIR(
						plainIR("("),
						arguments[0]!!.visitNeedReturn(),
						plainIR(")"),
						plainIR(" % "),
						plainIR("("),
						arguments[1]!!.visitNeedReturn(),
						plainIR(")"),
					)

					"plusAssign" -> singleLineListIR(
						plainIR("("),
						dispatchReceiver!!.visitNeedReturn(),
						plainIR(")"),
						plainIR(" += "),
						plainIR("("),
						valueArguments.single()!!.visitNeedReturn(),
						plainIR(")"),
					)

					"minusAssign" -> singleLineListIR(
						plainIR("("),
						dispatchReceiver!!.visitNeedReturn(),
						plainIR(")"),
						plainIR(" -= "),
						plainIR("("),
						valueArguments.single()!!.visitNeedReturn(),
						plainIR(")"),
					)

					"inc" -> singleLineListIR(
						plainIR("("),
						dispatchReceiver!!.visitNeedReturn(),
						plainIR(")"),
						plainIR("++"),
					)

					"dec" -> singleLineListIR(
						plainIR("("),
						dispatchReceiver!!.visitNeedReturn(),
						plainIR(")"),
						plainIR("--"),
					)

					"invoke" -> singleLineListIR(
						plainIR("("),
						dispatchReceiver!!.visitNeedReturn(),
						plainIR(")"),
						plainIR(".Invoke"),
						plainIR("("),
						*valueArguments
							.filterNotNull()
							.map { it.visit() }
							.join(plainIR(", "))
							.toTypedArray(),
						plainIR(")"),
					)

					"iterator" -> singleLineListIR(
						plainIR("new global::kotlin.collections.KotlinIteratorWrapper"),
						plainIR("<"),
						dispatchReceiver!!.type.let {
							it as IrSimpleType
							plainIR(it.arguments.single().typeOrFail.map(TypeStyle.TypeArgument))
						},
						plainIR(">"),
						plainIR("("),
						singleLineListIR(
							plainIR("("),
							dispatchReceiver!!.visit(),
							plainIR(")"),
							plainIR(".GetEnumerator()")
						),
						plainIR(")"),
					)

					"hasNext" -> singleLineListIR(
						plainIR("("),
						dispatchReceiver!!.visit(),
						plainIR(")"),
						plainIR("."),
						plainIR(function.name.visit()),
						plainIR("("),
						*valueArguments
							.filterNotNull()
							.map { it.visitNeedReturn() }
							.join(plainIR(", "))
							.toTypedArray(),
						plainIR(")"),
					)

					"next" -> singleLineListIR(
						plainIR("("),
						dispatchReceiver!!.visit(),
						plainIR(")"),
						plainIR("."),
						plainIR(function.name.visit()),
						plainIR("("),
						*valueArguments
							.filterNotNull()
							.map { it.visitNeedReturn() }
							.join(plainIR(", "))
							.toTypedArray(),
						plainIR(")"),
					)

					"not" -> singleLineListIR(
						plainIR("!"),
						plainIR("("),
						dispatchReceiver!!.visit(),
						plainIR(")"),
					)

					else -> {
						val name = function.name.visit()
						when {
							name.startsWith("<set-") -> {
								multiLineIR(
									plainIR(name.substring("<set-".length, name.length - 1)),
									plainIR(" = "),
									valueArguments[0]!!.visitNeedReturn()
								)
							}

							name.startsWith("<get-") -> {
								plainIR(name.substring("<get-".length, name.length - 1))
							}

							else -> multiLinePlain(
								"/*",
								"Unsupported name: ${function.name.visit()}",
								"at IrCall.visitClassParent: $this",
								"is operator",
								"*/",
							)
						}
					}
				}

				else -> when {
					dispatchReceiver?.type?.run {
						isByte() || isChar() || isShort() || isInt() || isLong() || isFloat() || isDouble()
					} == true && function.name.asString() in listOf(
						"toByte", "toChar", "toShort", "toInt", "toLong", "toFloat", "toDouble"
					) -> singleLineListIR(
						plainIR("("),
						plainIR(
							when (function.name.asString()) {
								"toByte" -> "sbyte"
								"toChar" -> "char"
								"toShort" -> "short"
								"toInt" -> "int"
								"toLong" -> "long"
								"toFloat" -> "float"
								"toDouble" -> "double"
								else -> error(function.name.asString())
							}
						),
						plainIR(")"),
						plainIR("("),
						dispatchReceiver!!.visit(),
						plainIR(")"),
					)

					dispatchReceiver != null -> singleLineListIR(
						buildList {
							add(plainIR("("))
							add(dispatchReceiver!!.visit())
							add(plainIR(")"))
							add(plainIR("."))
							when (function.name.isSpecial) {
								true -> {
									val name = function.name.visit()
									when {
										name.startsWith("<set-") -> {
											add(plainIR(name.substring("<set-".length, name.length - 1)))
											add(plainIR(" = "))
											add(valueArguments[0]!!.visitNeedReturn())
										}

										name.startsWith("<get-") -> {
											add(plainIR(name.substring("<get-".length, name.length - 1)))
										}

										else -> add(
											multiLinePlain(
												"/*",
												"Unsupported name: $name",
												"at IrCall.visitClassParent: ${this@visitClassParent}",
												"is special",
												"*/",
											)
										)
									}
								}

								else -> {
									add(plainIR(function.name.visit().let {
										when (it) {
											"equals" -> "Equals"
											else -> it
										}
									}))
									if (typeArguments.isNotEmpty()) {
										add(plainIR("<"))
										add(
											singleLineListIR(
												typeArguments.map {
													it?.map(TypeStyle.TypeArgument) ?: "global::System.Object"
												}.map {
													plainIR(it)
												}
											)
										)
										add(plainIR(">"))
									}
									add(plainIR("("))
									listOfNotNull(
										arguments.getOrNull(symbol.owner.parameters.indexOfFirst {
											it.kind == IrParameterKind.ExtensionReceiver
										}),
										*valueArguments.toTypedArray()
									)
										.map { it.visitNeedReturn() }
										.join(plainIR(", "))
										.forEach { add(it) }
									add(plainIR(")"))
								}
							}
						}
					)

					else -> multiLinePlain(
						"/*",
						"Unsupported dispatchReceiver: null",
						"at IrCall.visitClassParent: $this",
						"*/",
					)
				}
			}
		}
	}

	fun IrCall.visitExternalPackageFragmentParent(): BackendIR {
		val function = symbol.owner
		val parent = function.parent as IrExternalPackageFragment

		return when (parent.packageFqName.visit()) {
			"kotlin.internal.ir" -> when (function.name.visit()) {
				"greater" -> singleLineListIR(
					plainIR("("),
					valueArguments[0]!!.visitNeedReturn(),
					plainIR(")"),
					plainIR(" > "),
					plainIR("("),
					valueArguments[1]!!.visitNeedReturn(),
					plainIR(")"),
				)

				"less" -> singleLineListIR(
					plainIR("("),
					valueArguments[0]!!.visitNeedReturn(),
					plainIR(")"),
					plainIR(" < "),
					plainIR("("),
					valueArguments[1]!!.visitNeedReturn(),
					plainIR(")"),
				)

				"EQEQ" -> singleLineListIR(
					plainIR("("),
					valueArguments[0]!!.visitNeedReturn(),
					plainIR(")"),
					plainIR(" == "),
					plainIR("("),
					valueArguments[1]!!.visitNeedReturn(),
					plainIR(")"),
				)

				"CHECK_NOT_NULL" -> singleLineListIR(
					plainIR("global::kotlin.clr.NotNullAssertion.notNull"),
					plainIR("<"),
					plainIR(type.map(TypeStyle.TypeArgument)),
					plainIR(">"),
					plainIR("("),
					valueArguments.single()!!.visitNeedReturn(),
					plainIR(")"),
				)

				else -> multiLinePlain(
					"/*",
					"Unsupported function in kotlin.internal.ir: ${function.name}",
					"at IrCall.visitExternalPackageFragmentParent: $this",
					"*/",
				)
			}

			else -> multiLinePlain(
				"/*",
				"Unexpected external package fragment: ${parent.packageFqName}",
				"at IrCall.visitExternalPackageFragmentParent: $this",
				"*/",
			)
		}
	}

	fun IrCall.visit(): BackendIR {
		val function = symbol.owner
		val parent = function.parent

		return when (parent) {
			is IrClass -> visitClassParent()
			is IrExternalPackageFragment -> visitExternalPackageFragmentParent()
			else -> multiLinePlain(
				"/*",
				"Unexpected parent declaration: ${parent::class.java}: ${parent.render()}",
				"at IrCall.visit: $this",
				"*/",
			)
		}
	}

	fun IrTypeOperatorCall.visit(): BackendIR = argument.visit()

	fun IrFunctionExpression.visit(): BackendIR = multiLineListIR(
		singleLineListIR(
			plainIR("("),
			*function.parameters
				.filter { it.kind == IrParameterKind.Regular || it.kind == IrParameterKind.Context }
				.map { plainIR(it.name.map()) }
				.join(plainIR(", "))
				.toTypedArray(),
			plainIR(") =>")
		),
		function.body!!.visit()!!
	)

	fun IrReturn.visit(): BackendIR = singleLineListIR(
		buildList {
			add(plainIR("return"))
			(returnTargetSymbol.owner as? IrFunction).let { function ->
				when (function?.returnType?.isUnit()) {
					true -> {}
					else -> {
						add(plainIR(" "))
						add(value.visitNeedReturn())
					}
				}
			}
		}
	)

	fun IrVariable.visit(): BackendIR = singleLineIR(
		buildList {
			add(plainIR(type.map(TypeStyle.ReturnType)))
			add(plainIR(" "))
			add(plainIR(name.visit()))
			initializer?.let {
				add(plainIR(" = "))
				add(it.visitNeedReturn())
			}
		},
	)

	fun IrSetValue.visit(): BackendIR = singleLineListIR(
		symbol.visit(),
		plainIR(" = "),
		value.visitNeedReturn(),
	)

	fun IrWhen.visit() = branches.visit()

	fun IrWhen.visitNeedReturn() = branches.visitNeedReturn(type.map(TypeStyle.ReturnType))

	fun List<IrBranch>.visit(): BackendIR {
		val car = first()
		val cdr = drop(1)
		return ifPadding(
			condition = car.condition.visitNeedReturn(),
			content = car.result.let {
				when (it is IrBlock) {
					true -> it.visitNeedReturn().toPadding()
					else -> singleLineIR(it.visitNeedReturn(), plainIR(";"))
				}
			},
			elseContent = when (cdr.size) {
				0 -> noneIR
				1 -> cdr.single().result.let {
					when (it is IrBlock) {
						true -> it.visit().toPadding()
						else -> singleLineIR(it.visit(), plainIR(";"))
					}
				}

				else -> singleLineIR(cdr.visit(), plainIR(";"))
			}
		)
	}

	fun List<IrBranch>.visitNeedReturn(type: String): BackendIR {
		val car = first()
		val cdr = drop(1)
		return ifExpPadding(
			condition = car.condition.visitNeedReturn(),
			content = when (val content = car.result) {
				is IrBlock -> content.visitNeedReturn().let { node ->
					node as PaddingIR.Block
					blockListPadding(
						*node.nodes.dropLast(1).toTypedArray(),
						node.nodes.last().pushSingleLine(plainIR("return ")),
					)
				}

				else -> content.visitNeedReturn()
			} to type,
			elseContent = when (cdr.size) {
				1 -> when (val content = cdr.single().result) {
					is IrBlock -> content.visitNeedReturn().let { node ->
						node as PaddingIR.Block
						blockListPadding(
							*node.nodes.dropLast(1).toTypedArray(),
							node.nodes.last().pushSingleLine(plainIR("return ")),
						)
					}

					else -> content.visitNeedReturn()
				} to type

				else -> cdr.visitNeedReturn(type) to type
			}
		)
	}

	fun IrBlock.visit() = when (origin) {
		IrStatementOrigin.PREFIX_INCR -> singleLineListIR(
			plainIR("++"),
			plainIR("("),
			(statements[0] as IrVariable).initializer!!.visitNeedReturn(),
			plainIR(")"),
		)

		IrStatementOrigin.POSTFIX_INCR -> singleLineListIR(
			plainIR("("),
			(statements[0] as IrVariable).initializer!!.visitNeedReturn(),
			plainIR(")"),
			plainIR("++"),
		)

		IrStatementOrigin.PREFIX_DECR -> singleLineListIR(
			plainIR("--"),
			plainIR("("),
			(statements[0] as IrVariable).initializer!!.visitNeedReturn(),
			plainIR(")"),
		)

		IrStatementOrigin.POSTFIX_DECR -> singleLineListIR(
			plainIR("("),
			(statements[0] as IrVariable).initializer!!.visitNeedReturn(),
			plainIR(")"),
			plainIR("--"),
		)

		else -> blockListPadding(statements.mapNotNull { it.visit() })
	}

	fun IrVararg.visit() = singleLineListIR(elements.map { it.visit() }.join(plainIR(", ")))

	fun IrWhileLoop.visit(): BackendIR = multiLineListIR(
		singleLineListIR(plainIR("while ("), condition.visit(), plainIR(")")),
		multiLineIR(body?.visit() ?: blockListPadding())
	)

	fun IrVarargElement.visit(): BackendIR = when (this) {
		is IrExpression -> visit()
		else -> multiLinePlain(
			"/*",
			"Unsupported vararg element: ${this::class.java.simpleName}",
			"at IrVarargElement.visit",
			"*/",
		)
	}

	fun IrStatement.visit(): BackendIR? = when (this) {
		is IrWhen -> visit()
		is IrExpression -> singleLineIR(visit(), plainIR(";"))
		is IrDeclaration -> visit()?.appendSingleLine(plainIR(";"))
		else -> multiLinePlain(
			"/*",
			"Unsupported statement: ${this::class.java.simpleName}",
			"at IrStatement.visit",
			"*/",
		)
	}

	fun IrExpression.visit(): BackendIR = when (this) {
		is IrConst -> visit()
		is IrCall -> visit()
		is IrStringConcatenation -> visit()
		is IrGetValue -> visit()
		is IrConstructorCall -> visit()
		is IrGetObjectValue -> visit()
		is IrReturn -> visit()
		is IrSetValue -> visit()
		is IrWhen -> visit()
		is IrBlock -> visit()
		is IrVararg -> visit()
		is IrWhileLoop -> visit()
		is IrGetField -> visit()
		is IrSetField -> visit()
		is IrTypeOperatorCall -> visit()
		is IrFunctionExpression -> visit()
		else -> multiLinePlain(
			"/*",
			"Unsupported expression: ${this::class.java.simpleName}",
			"at IrExpression.visit",
			"*/",
		)
	}

	// Using return type
	fun IrExpression.visitNeedReturn() = when (this) {
		is IrWhen -> visitNeedReturn()
		else -> visit()
	}

	fun IrConst.visit() = when (value) {
		is String -> plainIR("\"$value\"")
		is Number -> plainIR(value.toString())
		is Boolean -> plainIR(value.toString())
		is Char -> plainIR("'$value'")
		null -> plainIR("null")
		else -> multiLinePlain(
			"/*",
			"Unsupported constant type: ${(value!!)::class.java.simpleName}",
			"at IrConst.visit: $this",
			"*/",
		)
	}

	fun IrStringConcatenation.visit(): BackendIR = stringConcatenationIR(arguments.map { it.visitNeedReturn() })

	fun IrGetValue.visit() = symbol.visit()

	fun IrGetField.visit(): BackendIR = singleLineListIR(
		buildList {
			receiver?.let {
				add(it.visit())
				add(plainIR("."))
			}
			add(symbol.visit().appendSingleLineList(plainIR("_backingField")))
		}
	)

	fun IrSetField.visit(): BackendIR = singleLineListIR(
		buildList {
			receiver?.let {
				add(it.visit())
				add(plainIR("."))
			}
			add(symbol.visit().appendSingleLineList(plainIR("_backingField")))
			add(plainIR(" = "))
			add(value.visitNeedReturn())
		}
	)

	fun IrSymbol.visit() = when (this) {
		is IrVariableSymbol,
		is IrValueSymbol,
			-> plainIR(owner.name.visit())

		is IrFieldSymbol -> plainIR(owner.name.visit())

		else -> multiLinePlain(
			"/*",
			"Unsupported symbol: ${this::class.java.simpleName}",
			"at IrSymbol.visit",
			"*/",
		)
	}

	fun Name.visit() = when (isSpecial) {
		true -> when (asString()) {
			"<this>" -> "this"
			"<iterator>" -> "iterator"
			"<set-?>" -> "value"
			else -> asString()
		}

		else -> asString()
	}

	fun FqName.visit() = asString()

	fun Visibility.visit() = when (this) {
		is Visibilities.Private -> plainIR("private ")
		is Visibilities.Protected -> plainIR("protected ")
		is Visibilities.Internal -> plainIR("internal ")
		is Visibilities.Public -> plainIR("public ")
		else -> multiLinePlain(
			"/*",
			"Unsupported visibility: $this",
			"at Visibility.visit",
			"*/",
		)
	}

	fun Modality.visit() = when (this) {
		FINAL -> plainIR("sealed ")
		ABSTRACT -> plainIR("abstract ")
		SEALED -> multiLinePlain(
			"/*",
			"TODO sealed modality",
			"at Modality.visit",
			"*/",
		)

		OPEN -> plainIR("")
	}

	@OptIn(DeprecatedForRemovalCompilerApi::class)
	private val IrFunctionAccessExpression.valueArguments
		get() = List(valueArgumentsCount) { getValueArgument(it) }
}