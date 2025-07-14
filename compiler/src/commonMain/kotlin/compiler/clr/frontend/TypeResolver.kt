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

import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirTypeParameterSymbol
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.fir.types.impl.ConeClassLikeTypeImpl
import org.jetbrains.kotlin.fir.types.impl.ConeTypeParameterTypeImpl
import org.jetbrains.kotlin.fir.types.toLookupTag
import org.jetbrains.kotlin.javac.resolve.classId
import org.jetbrains.kotlin.name.StandardClassIds

object TypeResolver {
	@OptIn(SymbolInternals::class)
	fun resolveType(
		namespace: String,
		name: String,
		isReturnPosition: Boolean,
		nullable: Boolean,
		typeParameters: List<FirTypeParameterSymbol>,
	): FirResolvedTypeRef {
		try {
			val classId = when (namespace) {
				"System" -> when (name) {
					"Attribute" -> StandardClassIds.Annotation
					"Object" -> StandardClassIds.Any
					"Array" -> StandardClassIds.Array
					"Void" -> StandardClassIds.Unit
					"Boolean" -> StandardClassIds.Boolean
					"Char" -> StandardClassIds.Char
					"Enum" -> StandardClassIds.Enum
					"SByte" -> StandardClassIds.Byte
					"Int16" -> StandardClassIds.Short
					"Int32" -> StandardClassIds.Int
					"Int64" -> StandardClassIds.Long
					"Single" -> StandardClassIds.Float
					"Double" -> StandardClassIds.Double
					"String" -> StandardClassIds.String
					"Exception" -> StandardClassIds.Throwable
					else -> classId(namespace, name)
				}

				"System.Collections.Generic" -> when (name) {
					"IReadOnlyList" -> StandardClassIds.List
					"IList" -> StandardClassIds.MutableList
					"IReadOnlySet" -> StandardClassIds.Set
					"ISet" -> StandardClassIds.MutableSet
					"IReadOnlyDictionary" -> StandardClassIds.Map
					"IDictionary" -> StandardClassIds.MutableMap
					else -> classId(namespace, name)
				}

				else -> classId(namespace, name)
			}

			return buildResolvedTypeRef {
				coneType = ConeClassLikeTypeImpl(
					classId.toLookupTag(),
					typeParameters.map { typeParameterSymbol ->
						typeParameterSymbol.annotations
						ConeTypeParameterTypeImpl(
							typeParameterSymbol.toLookupTag(),
							typeParameterSymbol.defaultType.isMarkedNullable
						)
					}.toTypedArray(),
					nullable
				)
			}
		} catch (e: Throwable) {
			println("exception on $namespace $name $isReturnPosition")
			throw e
		}
	}
}