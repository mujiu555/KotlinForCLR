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
import compiler.clr.backend.mapping.IrTypeMapper
import compiler.clr.backend.mapping.TypeStyle
import org.jetbrains.kotlin.DeprecatedForRemovalCompilerApi
import org.jetbrains.kotlin.descriptors.ClassKind.*
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Modality.*
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.fir.lazy.Fir2IrLazySimpleFunction
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.symbols.*
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.isNothing
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
	val typeMapper = IrTypeMapper(context)

	fun IrFile.visit(): CodeNode {
		val `package` = packageFqName

		val content = multiLineCode(
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
				.join(noneCode)
				.toTypedArray()
		).let {
			when (it.nodes.size == 1) {
				true -> it.nodes.single()
				else -> it
			}
		}

		return when (!`package`.isRoot) {
			true -> multiLineCode(
				singleLinePlain("namespace $`package`"),
				blockPadding(content),
			)

			else -> content
		}
	}

	fun IrClass.visit(): CodeNode = when {
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

	fun IrClass.visitFileClass() = multiLineCode(
		singleLinePlain(
			"[global::kotlin.clr.KotlinFileClass]"
		),
		singleLineCode(
			visibility.delegate.visit(),
			plainPlain("static "),
			plainPlain("class "),
			plainPlain(name.visit())
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
			}.join(noneCode)
		),
	)

	fun IrClass.visitClass() = multiLineCode(
		singleLineCode(
			visibility.delegate.visit(),
			modality.visit(),
			plainPlain("class "),
			plainPlain(name.visit()),
			plainPlain(" : "),
			plainPlain(superTypes.joinToString(", ") { typeMapper.mapType(it, TypeStyle.NoModifier) }),
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
			}.join(noneCode)
		),
	)

	fun IrClass.visitInterface() = multiLineCode(
		singleLineCode(
			buildList {
				add(visibility.delegate.visit())
				add(modality.visit())
				add(plainPlain("interface "))
				add(plainPlain(name.visit()))
				if (superTypes.isNotEmpty()) {
					add(plainPlain(" : "))
					add(plainPlain(superTypes.joinToString(", ") { typeMapper.mapType(it, TypeStyle.NoModifier) }))
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
			}.join(noneCode)
		),
	)

	fun IrClass.visitEnumClass() = multiLineCode(
		singleLineCode(
			visibility.delegate.visit(),
			modality.visit(),
			plainPlain("enum "),
			plainPlain(name.visit()),
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
			}.join(noneCode)
		),
	)

	fun IrClass.visitAnnotationClass() = multiLineCode(
		singleLineCode(
			visibility.delegate.visit(),
			modality.visit(),
			plainPlain("class "),
			plainPlain(name.visit()),
			plainPlain(" : global::System.Attribute"),
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
			}.join(noneCode)
		),
	)

	fun IrClass.visitObject() = multiLineCode(
		singleLineCode(
			visibility.delegate.visit(),
			modality.visit(),
			plainPlain("class "),
			plainPlain(name.visit()),
			plainPlain(" : "),
			plainPlain(superTypes.joinToString(", ") { typeMapper.mapType(it, TypeStyle.NoModifier) }),
		),
		blockPadding(
			buildList {
				add(
					multiLineListCode(
						buildList {
							if (!defaultType.isNullable()) {
								add(singleLinePlain("[global::kotlin.clr.KotlinNotNull]"))
							}
							add(
								singleLinePlain(
									"public static ",
									typeMapper.mapType(defaultType, TypeStyle.Property),
									" INSTANCE { get; } = new ",
									typeMapper.mapType(defaultType, TypeStyle.NoModifier),
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
			}.join(noneCode)
		),
	)

	fun IrDeclaration.visit(): CodeNode? {
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

	fun IrFunction.visit() = multiLineCode(
		buildList {
			when (returnType.isNothing()) {
				true -> add(
					singleLineCode(plainPlain("[global::System.Diagnostics.CodeAnalysis.DoesNotReturnAttribute]"))
				)

				else -> when {
					!returnType.isNullable() && !returnType.isUnit() -> add(
						singleLineCode(plainPlain("[global::kotlin.clr.KotlinNotNull]"))
					)
				}
			}
			if (parameters.firstOrNull { it.kind == IrParameterKind.ExtensionReceiver } != null) {
				add(singleLineCode(plainPlain("[global::kotlin.clr.KotlinExtension]")))
			}
			add(
				singleLineCode(
					buildList {
						val isStatic = when {
							parent is IrFile -> true
							isStatic -> true
							else -> false
						}

						val returnType = typeMapper.mapType(returnType, TypeStyle.ReturnType)

						val parameters = parameters.filter {
							it.kind == IrParameterKind.Regular || it.kind == IrParameterKind.Context
						}.map {
							typeMapper.mapType(it.type, TypeStyle.Normal) to it.name.visit()
						}

						add(visibility.delegate.visit())
						if (isStatic) {
							add(plainPlain("static "))
						}
						add(plainPlain("$returnType "))
						add(plainPlain("${name.visit()}("))
						this@visit.parameters.firstOrNull { it.kind == IrParameterKind.ExtensionReceiver }?.let {
							add(plainPlain("${typeMapper.mapType(it.type, TypeStyle.Normal)} receiver, "))
						}
						add(plainPlain(parameters.joinToString(", ") { "${it.first} ${it.second}" }))
						add(plainPlain(")"))
					}
				)
			)
			add(body?.visit() ?: blockPadding())
		}
	)

	fun IrConstructor.visit() = multiLineCode(
		singleLineCode(
			buildList {
				val className = (parent as? IrClass)?.name?.visit()!!

				val parameters = parameters.filter {
					it.kind == IrParameterKind.Regular || it.kind == IrParameterKind.Context
				}.map {
					typeMapper.mapType(it.type, TypeStyle.Normal) to it.name.visit()
				}

				add(visibility.delegate.visit())
				add(plainPlain("$className("))
				add(plainPlain(parameters.joinToString(", ") { "${it.first} ${it.second}" }))
				add(plainPlain(")"))
				if (body?.statements?.get(0) is IrDelegatingConstructorCall) {
					add(plainPlain(" : "))
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
							singleLineCode(
								plainPlain("this.${property.name.visit()}_backingField = "),
								initializer.visitUsing(),
								plainPlain(";")
							)
						}
				)
			}
		),
	)

	fun IrField.visit() = multiLineCode(
		buildList {
			if (!type.isNullable()) {
				add(singleLineCode(plainPlain("[global::kotlin.clr.KotlinNotNull]")))
			}
			add(
				singleLineListCode(
					buildList {
						add(plainPlain("private "))
						if (isStatic) {
							add(plainPlain("static "))
						}

						add(plainPlain(typeMapper.mapType(type, TypeStyle.Property)))
						add(plainPlain(" "))

						add(plainPlain(name.visit() + "_backingField"))
						add(plainPlain(";"))
					}
				)
			)
		}
	)

	fun IrProperty.visit() = multiLineCode(
		buildList {
			val type = getter?.returnType
				?: setter?.returnType
				?: error("Property must have either getter or setter: $this")

			if (!type.isNullable()) {
				add(singleLineCode(plainPlain("[global::kotlin.clr.KotlinNotNull]")))
			}
			add(
				singleLineCode(
					buildList {
						val isStatic = when {
							parent is IrFile -> true
							(backingField?.isStatic ?: getter?.isStatic ?: setter?.isStatic) == true -> true
							else -> false
						}
						add(visibility.delegate.visit())
						if (isStatic) {
							add(plainPlain("static "))
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

							OPEN -> add(plainPlain("virtual "))
							ABSTRACT -> add(plainPlain("abstract "))
						}

						add(plainPlain(typeMapper.mapType(type, TypeStyle.ReturnType)))
						add(plainPlain(" "))

						add(plainPlain(name.visit()))
					}
				)
			)
			add(
				blockPadding(
					multiLineCode(
						buildList {
							getter?.let { getter ->
								add(plainPlain("get"))
								add(getter.body!!.visit()!!)
							}
							setter?.let { setter ->
								add(plainPlain("set"))
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
					is IrExpression -> singleLineCode(it.visit(), plainPlain(";"))
					is IrVariable -> it.visit().appendSingleLine(plainPlain(";"))
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
					is IrExpression -> singleLineCode(it.visit(), plainPlain(";"))
					is IrVariable -> it.visit().appendSingleLine(plainPlain(";"))
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
					else -> multiLineCode(it)
				}
			}
	}

	fun IrDelegatingConstructorCall.visit() = singleLineListCode(
		plainPlain("base("),
		*valueArguments
			.filterNotNull()
			.map { it.visitUsing() }
			.join(plainPlain(", "))
			.toTypedArray(),
		plainPlain(")"),
	)

	fun IrGetObjectValue.visit() = singleLineListCode(
		plainPlain(typeMapper.mapType(symbol.owner.defaultType, TypeStyle.NoModifier)),
		plainPlain(".INSTANCE"),
	)

	fun IrConstructorCall.visit(): CodeNode = singleLineListCode(
		buildList {
			val constructedClass = symbol.owner.parent as IrClass
			add(plainPlain("new "))
			add(plainPlain(typeMapper.mapType(constructedClass.defaultType, TypeStyle.NoModifier)))
			add(plainPlain("("))
			valueArguments
				.filterNotNull()
				.map { it.visitUsing() }
				.join(plainPlain(", "))
				.forEach { add(it) }
			add(plainPlain(")"))
		}
	)

	fun IrCall.visitClassParent(): CodeNode {
		val function = symbol.owner
		val parent = function.parent as IrClass

		val isStatic = function.isStatic
		val isCompanionStatic = (function as? Fir2IrLazySimpleFunction)?.fir?.annotations?.any {
			it.annotationTypeRef.coneType.classId == classId("kotlin.clr", "ClrStatic")
		} == true

		return when {
			isStatic -> singleLineListCode(
				buildList {
					add(plainPlain(typeMapper.mapType(parent.defaultType, TypeStyle.NoModifier)))
					add(plainPlain("."))
					when (function.name.isSpecial) {
						true -> {
							val name = function.name.visit()

							when {
								name.startsWith("<set-") -> {
									add(plainPlain(name.substring("<set-".length, name.length - 1)))
									add(plainPlain(" = "))
									add(valueArguments[0]!!.visitUsing())
								}

								name.startsWith("<get-") -> {
									add(plainPlain(name.substring("<get-".length, name.length - 1)))
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
							add(plainPlain(function.name.visit()))
							if (typeArguments.isNotEmpty()) {
								add(plainPlain("<"))
								add(
									singleLineListCode(
										typeArguments.map {
											it?.let { typeMapper.mapType(it, TypeStyle.TypeArgument) }
												?: "global::System.Object"
										}.map {
											plainPlain(it)
										}
									)
								)
								add(plainPlain(">"))
							}
							add(plainPlain("("))
							listOfNotNull(
								arguments.getOrNull(symbol.owner.parameters.indexOfFirst {
									it.kind == IrParameterKind.ExtensionReceiver
								}),
								*valueArguments.toTypedArray()
							)
								.map { it.visitUsing() }
								.join(plainPlain(", "))
								.forEach { add(it) }
							add(plainPlain(")"))
						}
					}
				}
			)

			isCompanionStatic -> singleLineListCode(
				buildList {
					val outer = parent.parent as? IrClass ?: return multiLinePlain(
						"/*",
						"Expected IrClass but got ${parent::class.java.simpleName}",
						"at IrCall.visitClassParent: ${this@visitClassParent}",
						"is companion static",
						"*/",
					)
					add(plainPlain(typeMapper.mapType(outer.defaultType, TypeStyle.NoModifier)))
					add(plainPlain("."))
					when (function.name.isSpecial) {
						true -> {
							val name = function.name.visit()
							when {
								name.startsWith("<set-") -> {
									add(plainPlain(name.substring("<set-".length, name.length - 1)))
									add(plainPlain(" = "))
									add(valueArguments[0]!!.visitUsing())
								}

								name.startsWith("<get-") -> {
									add(plainPlain(name.substring("<get-".length, name.length - 1)))
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
							add(plainPlain(function.name.visit()))
							if (typeArguments.isNotEmpty()) {
								add(plainPlain("<"))
								add(
									singleLineListCode(
										typeArguments.map {
											it?.let { typeMapper.mapType(it, TypeStyle.TypeArgument) }
												?: "global::System.Object"
										}.map {
											plainPlain(it)
										}
									)
								)
								add(plainPlain(">"))
							}
							add(plainPlain("("))
							listOfNotNull(
								arguments.getOrNull(symbol.owner.parameters.indexOfFirst {
									it.kind == IrParameterKind.ExtensionReceiver
								}),
								*valueArguments.toTypedArray()
							)
								.map { it.visitUsing() }
								.join(plainPlain(", "))
								.forEach { add(it) }
							add(plainPlain(")"))
						}
					}
				}
			)

			else -> when {
				function.isOperator -> when (function.name.visit()) {
					"plus" -> singleLineListCode(
						plainPlain("("),
						arguments[0]!!.visitUsing(),
						plainPlain(")"),
						plainPlain(" + "),
						plainPlain("("),
						arguments[1]!!.visitUsing(),
						plainPlain(")"),
					)

					"times" -> singleLineListCode(
						plainPlain("("),
						arguments[0]!!.visitUsing(),
						plainPlain(")"),
						plainPlain(" * "),
						plainPlain("("),
						arguments[1]!!.visitUsing(),
						plainPlain(")"),
					)

					"iterator" -> singleLineListCode(
						plainPlain("new global::kotlin.collections.KotlinIterator<"),
						dispatchReceiver!!.type.let {
							it as IrSimpleType
							plainPlain(typeMapper.mapType(it.arguments.single().typeOrFail, TypeStyle.TypeArgument))
						},
						plainPlain(">("),
						singleLineListCode(
							dispatchReceiver!!.visit(),
							plainPlain(".GetEnumerator()")
						),
						plainPlain(")")
					)

					"hasNext" -> singleLineListCode(
						dispatchReceiver!!.visit(),
						plainPlain("."),
						plainPlain(function.name.visit()),
						plainPlain("("),
						*valueArguments
							.filterNotNull()
							.map { it.visitUsing() }
							.join(plainPlain(", "))
							.toTypedArray(),
						plainPlain(")")
					)

					"next" -> singleLineListCode(
						dispatchReceiver!!.visit(),
						plainPlain("."),
						plainPlain(function.name.visit()),
						plainPlain("("),
						*valueArguments
							.filterNotNull()
							.map { it.visitUsing() }
							.join(plainPlain(", "))
							.toTypedArray(),
						plainPlain(")")
					)

					"not" -> singleLineListCode(
						plainPlain("!("),
						dispatchReceiver!!.visit(),
						plainPlain(")")
					)

					else -> {
						val name = function.name.visit()
						when {
							name.startsWith("<set-") -> {
								multiLineCode(
									plainPlain(name.substring("<set-".length, name.length - 1)),
									plainPlain(" = "),
									valueArguments[0]!!.visitUsing()
								)
							}

							name.startsWith("<get-") -> {
								plainPlain(name.substring("<get-".length, name.length - 1))
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

				else -> singleLineListCode(
					buildList {
						add(dispatchReceiver!!.visit())
						add(plainPlain("."))
						when (function.name.isSpecial) {
							true -> {
								val name = function.name.visit()
								when {
									name.startsWith("<set-") -> {
										add(plainPlain(name.substring("<set-".length, name.length - 1)))
										add(plainPlain(" = "))
										add(valueArguments[0]!!.visitUsing())
									}

									name.startsWith("<get-") -> {
										add(plainPlain(name.substring("<get-".length, name.length - 1)))
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
								add(plainPlain(function.name.visit().let {
									when (it) {
										"equals" -> "Equals"
										else -> it
									}
								}))
								if (typeArguments.isNotEmpty()) {
									add(plainPlain("<"))
									add(
										singleLineListCode(
											typeArguments.map {
												it?.let { typeMapper.mapType(it, TypeStyle.TypeArgument) }
													?: "global::System.Object"
											}.map {
												plainPlain(it)
											}
										)
									)
									add(plainPlain(">"))
								}
								add(plainPlain("("))
								listOfNotNull(
									arguments.getOrNull(symbol.owner.parameters.indexOfFirst {
										it.kind == IrParameterKind.ExtensionReceiver
									}),
									*valueArguments.toTypedArray()
								)
									.map { it.visitUsing() }
									.join(plainPlain(", "))
									.forEach { add(it) }
								add(plainPlain(")"))
							}
						}
					}
				)
			}
		}
	}

	fun IrCall.visitExternalPackageFragmentParent(): CodeNode {
		val function = symbol.owner
		val parent = function.parent as IrExternalPackageFragment

		return when (parent.packageFqName.visit()) {
			"kotlin.internal.ir" -> when (function.name.visit()) {
				"greater" -> singleLineListCode(
					plainPlain("("),
					valueArguments[0]!!.visitUsing(),
					plainPlain(")"),
					plainPlain(" > "),
					plainPlain("("),
					valueArguments[1]!!.visitUsing(),
					plainPlain(")"),
				)

				"EQEQ" -> singleLineListCode(
					valueArguments[0]!!.visitUsing(),
					plainPlain(" == "),
					valueArguments[1]!!.visitUsing()
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

	fun IrCall.visit(): CodeNode {
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

	fun IrTypeOperatorCall.visit(): CodeNode = argument.visit()

	fun IrReturn.visit(): CodeNode = singleLineListCode(
		plainPlain("return "),
		value.visitUsing(),
	)

	fun IrVariable.visit(): CodeNode = singleLineCode(
		buildList {
			add(plainPlain(typeMapper.mapType(type, TypeStyle.ReturnType)))
			add(plainPlain(" "))
			add(plainPlain(name.visit()))
			initializer?.let {
				add(plainPlain(" = "))
				add(it.visitUsing())
			}
		},
	)

	fun IrSetValue.visit(): CodeNode = singleLineListCode(
		symbol.visit(),
		plainPlain(" = "),
		value.visitUsing(),
	)

	fun IrWhen.visit() = branches.visit()

	fun IrWhen.visitUsing() = branches.visitUsing(typeMapper.mapType(type, TypeStyle.ReturnType))

	fun List<IrBranch>.visit(): CodeNode {
		val car = first()
		val cdr = drop(1)
		return ifPadding(
			condition = car.condition.visit(),
			content = car.result.visit().toPadding(),
			elseContent = when (cdr.size) {
				1 -> cdr.single().result.visit()
				else -> cdr.visit()
			}.toPadding()
		)
	}

	fun List<IrBranch>.visitUsing(type: String): CodeNode {
		val car = first()
		val cdr = drop(1)
		return ifExpPadding(
			condition = car.condition.visitUsing(),
			content = when (val content = car.result) {
				is IrBlock -> content.visitUsing().let { node ->
					node as PaddingNode.Block
					blockListPadding(
						*node.nodes.dropLast(1).toTypedArray(),
						node.nodes.last().pushSingleLine(plainPlain("return ")),
					)
				}

				else -> content.visitUsing()
			} to type,
			elseContent = when (cdr.size) {
				1 -> when (val content = cdr.single().result) {
					is IrBlock -> content.visitUsing().let { node ->
						node as PaddingNode.Block
						blockListPadding(
							*node.nodes.dropLast(1).toTypedArray(),
							node.nodes.last().pushSingleLine(plainPlain("return ")),
						)
					}

					else -> content.visitUsing()
				} to type

				else -> cdr.visitUsing(type) to type
			}
		)
	}

	fun IrBlock.visit() = blockListPadding(statements.mapNotNull { it.visit() })

	fun IrVararg.visit() = singleLineListCode(elements.map { it.visit() }.join(plainPlain(", ")))

	fun IrWhileLoop.visit(): CodeNode = multiLineListCode(
		singleLineListCode(plainPlain("while ("), condition.visit(), plainPlain(")")),
		multiLineCode(body?.visit() ?: blockListPadding())
	)

	fun IrVarargElement.visit(): CodeNode = when (this) {
		is IrExpression -> visit()
		else -> multiLinePlain(
			"/*",
			"Unsupported vararg element: ${this::class.java.simpleName}",
			"at IrVarargElement.visit",
			"*/",
		)
	}

	fun IrStatement.visit(): CodeNode? = when (this) {
		is IrWhen -> visit()
		is IrExpression -> singleLineCode(visit(), plainPlain(";"))
		is IrDeclaration -> visit()?.appendSingleLine(plainPlain(";"))
		else -> multiLinePlain(
			"/*",
			"Unsupported statement: ${this::class.java.simpleName}",
			"at IrStatement.visit",
			"*/",
		)
	}

	fun IrExpression.visit() = when (this) {
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
		else -> multiLinePlain(
			"/*",
			"Unsupported expression: ${this::class.java.simpleName}",
			"at IrExpression.visit",
			"*/",
		)
	}

	// Using return type
	fun IrExpression.visitUsing() = when (this) {
		is IrWhen -> visitUsing()
		else -> visit()
	}

	fun IrConst.visit() = when (value) {
		is String -> plainPlain("\"$value\"")
		is Number -> plainPlain(value.toString())
		is Boolean -> plainPlain(value.toString())
		is Char -> plainPlain("'$value'")
		null -> plainPlain("null")
		else -> multiLinePlain(
			"/*",
			"Unsupported constant type: ${(value!!)::class.java.simpleName}",
			"at IrConst.visit: $this",
			"*/",
		)
	}

	fun IrStringConcatenation.visit(): CodeNode = stringConcatenationCode(arguments.map { it.visitUsing() })

	fun IrGetValue.visit() = symbol.visit()

	fun IrGetField.visit(): CodeNode = singleLineListCode(
		buildList {
			receiver?.let {
				add(it.visit())
				add(plainPlain("."))
			}
			add(symbol.visit().appendSingleLineList(plainPlain("_backingField")))
		}
	)

	fun IrSetField.visit(): CodeNode = singleLineListCode(
		buildList {
			receiver?.let {
				add(it.visit())
				add(plainPlain("."))
			}
			add(symbol.visit().appendSingleLineList(plainPlain("_backingField")))
			add(plainPlain(" = "))
			add(value.visitUsing())
		}
	)

	fun IrSymbol.visit() = when (this) {
		is IrVariableSymbol,
		is IrValueSymbol,
			-> plainPlain(owner.name.visit())

		is IrFieldSymbol -> plainPlain(owner.name.visit())

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
		is Visibilities.Private -> plainPlain("private ")
		is Visibilities.Protected -> plainPlain("protected ")
		is Visibilities.Internal -> plainPlain("internal ")
		is Visibilities.Public -> plainPlain("public ")
		else -> multiLinePlain(
			"/*",
			"Unsupported visibility: $this",
			"at Visibility.visit",
			"*/",
		)
	}

	fun Modality.visit() = when (this) {
		FINAL -> plainPlain("sealed ")
		ABSTRACT -> plainPlain("abstract ")
		SEALED -> multiLinePlain(
			"/*",
			"TODO sealed modality",
			"at Modality.visit",
			"*/",
		)

		OPEN -> plainPlain("")
	}

	@OptIn(DeprecatedForRemovalCompilerApi::class)
	private val IrFunctionAccessExpression.valueArguments
		get() = List(valueArgumentsCount) { getValueArgument(it) }
}