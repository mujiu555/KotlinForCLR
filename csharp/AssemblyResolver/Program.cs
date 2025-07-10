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

using System;
using System.Collections.Generic;
using System.Linq;
using System.Text.Json;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp;

namespace AssemblyResolver;

public static class Program {
	public static void Main(string[] args) {
		var libraries = args[0].Split(";");
		MetadataReference? reference = null;
		var references = libraries
			.Select(it => {
				if (it != args[1]) return MetadataReference.CreateFromFile(it);
				reference = MetadataReference.CreateFromFile(it);
				return reference;
			})
			.ToList();
		var compilation = CSharpCompilation.Create(
			assemblyName: "Analysis",
			references: references
		);

		var symbol = compilation.GetAssemblyOrModuleSymbol(reference!);

		if (symbol is IAssemblySymbol assemblySymbol) {
			Console.WriteLine(JsonSerializer.Serialize(NodeAssembly.from(assemblySymbol)));
		} else {
			Console.WriteLine($"Unknown symbol: {symbol?.GetType()}");
		}
	}
}

public record NodeAssembly(
	string name,
	List<NodeType> types
) {
	public static NodeAssembly from(IAssemblySymbol symbol) => new(
		name: symbol.Name,
		types: makeTypes(symbol.GlobalNamespace)
	);

	private static List<NodeType> makeTypes(INamespaceSymbol symbol) => symbol.GetMembers()
		.SelectMany(it => {
			if (it is INamespaceSymbol namespaceSymbol) {
				return makeTypes(namespaceSymbol);
			}

			return [NodeType.from(it as INamedTypeSymbol)];
		}).ToList();
}

public record NodeType(
	string name,
	string @namespace,
	NodeTypeReference? baseType,
	List<NodeTypeReference> interfaces,
	List<NodeAttribute> attributes,
	List<NodeConstructor> constructors,
	List<NodeEvent> events,
	List<NodeField> fields,
	List<NodeMethod> methods,
	List<NodeType> nestedTypes,
	List<NodeProperty> properties,
	List<NodeTypeParameter> typeParameters,
	bool isAbstract,
	bool isArray,
	bool isClass,
	bool isEnum,
	bool isGenericType,
	bool isInterface,
	bool isNested,
	bool isNestedAssembly,
	bool isNestedFamily,
	bool isNestedPrivate,
	bool isNestedPublic,
	bool isNotPublic,
	bool isPointer,
	bool isPublic,
	bool isSealed,
	bool isValueType
) {
	public static NodeType from(INamedTypeSymbol symbol) => new(
		name: symbol.Name,
		@namespace: symbol.ContainingNamespace?.let(it =>
			it.IsGlobalNamespace ? "" : it.ToDisplayString()) ?? "",
		baseType: symbol.BaseType?.let(NodeTypeReference.from),
		interfaces: symbol.Interfaces.Select(NodeTypeReference.from).ToList(),
		attributes: symbol.GetAttributes().Select(NodeAttribute.from).ToList(),
		constructors: symbol.InstanceConstructors.Select(NodeConstructor.from).ToList(),
		events: symbol.GetMembers()
			.Where(it => it is IEventSymbol)
			.Cast<IEventSymbol>()
			.Select(NodeEvent.from)
			.ToList(),
		fields: symbol.GetMembers()
			.Where(it => it is IFieldSymbol)
			.Cast<IFieldSymbol>()
			.Select(NodeField.from)
			.ToList(),
		methods: symbol.GetMembers()
			.Where(it => it is IMethodSymbol)
			.Cast<IMethodSymbol>()
			.Select(NodeMethod.from)
			.ToList(),
		nestedTypes: symbol.GetMembers()
			.Where(it => it is INamedTypeSymbol)
			.Cast<INamedTypeSymbol>()
			.Select(from)
			.ToList(),
		properties: symbol.GetMembers()
			.Where(it => it is IPropertySymbol)
			.Cast<IPropertySymbol>()
			.Select(NodeProperty.from)
			.ToList(),
		typeParameters: symbol.TypeParameters.Select(NodeTypeParameter.from).ToList(),
		isAbstract: symbol.IsAbstract,
		isArray: symbol.TypeKind == TypeKind.Array,
		isClass: symbol.TypeKind == TypeKind.Class,
		isEnum: symbol.TypeKind == TypeKind.Enum,
		isGenericType: symbol.IsGenericType,
		isInterface: symbol.TypeKind == TypeKind.Interface,
		isNested: symbol.ContainingType != null,
		isNestedAssembly: symbol.ContainingType?.DeclaredAccessibility == Accessibility.Internal,
		isNestedFamily: symbol.ContainingType?.DeclaredAccessibility == Accessibility.Protected,
		isNestedPrivate: symbol.ContainingType?.DeclaredAccessibility == Accessibility.Private,
		isNestedPublic: symbol.ContainingType?.DeclaredAccessibility == Accessibility.Public,
		isNotPublic: symbol.DeclaredAccessibility != Accessibility.Public,
		isPointer: symbol.TypeKind == TypeKind.Pointer,
		isPublic: symbol.DeclaredAccessibility == Accessibility.Public,
		isSealed: symbol.IsSealed,
		isValueType: symbol.IsValueType
	);
}

public record NodeConstructor(
	List<NodeParameter> parameters,
	bool isAbstract,
	bool isAssembly,
	bool isFamily,
	bool isFinal,
	bool isPrivate,
	bool isPublic,
	bool isStatic,
	bool isVirtual
) {
	public static NodeConstructor from(IMethodSymbol symbol) => new(
		parameters: symbol.Parameters.Select(NodeParameter.from).ToList(),
		isAbstract: symbol.IsAbstract,
		isAssembly: symbol.DeclaredAccessibility == Accessibility.Internal,
		isFamily: symbol.DeclaredAccessibility == Accessibility.Protected,
		isFinal: symbol.IsSealed,
		isPrivate: symbol.DeclaredAccessibility == Accessibility.Private,
		isPublic: symbol.DeclaredAccessibility == Accessibility.Public,
		isStatic: symbol.IsStatic,
		isVirtual: symbol.IsVirtual
	);
}

public record NodeEvent(
) {
	public static NodeEvent from(IEventSymbol symbol) => new(
	);
}

public record NodeField(
) {
	public static NodeField from(IFieldSymbol symbol) => new(
	);
}

public record NodeMethod(
	string name,
	NodeTypeReference returnType,
	List<NodeAttribute> attributes,
	List<NodeTypeParameter> typeParameters,
	List<NodeParameter> parameters,
	bool isAbstract,
	bool isAssembly,
	bool isFamily,
	bool isFinal,
	bool isPrivate,
	bool isPublic,
	bool isStatic,
	bool isVirtual
) {
	public static NodeMethod from(IMethodSymbol symbol) => new(
		name: symbol.Name,
		returnType: NodeTypeReference.from(symbol.ReturnType),
		attributes: symbol.GetAttributes().Select(NodeAttribute.from).ToList(),
		typeParameters: symbol.TypeParameters.Select(NodeTypeParameter.from).ToList(),
		parameters: symbol.Parameters.Select(NodeParameter.from).ToList(),
		isAbstract: symbol.IsAbstract,
		isAssembly: symbol.DeclaredAccessibility == Accessibility.Internal,
		isFamily: symbol.DeclaredAccessibility == Accessibility.Protected,
		isFinal: symbol.IsSealed,
		isPrivate: symbol.DeclaredAccessibility == Accessibility.Private,
		isPublic: symbol.DeclaredAccessibility == Accessibility.Public,
		isStatic: symbol.IsStatic,
		isVirtual: symbol.IsVirtual
	);
}

public record NodeProperty(
) {
	public static NodeProperty from(IPropertySymbol symbol) => new(
	);
}

public record NodeParameter(
	string? name,
	NodeTypeReference type,
	List<NodeAttribute> attributes,
	bool hasDefaultValue,
	bool isParams
) {
	public static NodeParameter from(IParameterSymbol symbol) => new(
		name: symbol.Name,
		type: NodeTypeReference.from(symbol.Type),
		attributes: symbol.GetAttributes().Select(NodeAttribute.from).ToList(),
		hasDefaultValue: symbol.HasExplicitDefaultValue,
		isParams: symbol.IsParams
	);
}

public record NodeTypeParameter(
	string name,
	bool isOut,
	bool isIn,
	bool isInType,
	bool isInMethod
) {
	public static NodeTypeParameter from(ITypeParameterSymbol symbol) => new(
		name: symbol.Name,
		isOut: symbol.Variance == VarianceKind.Out,
		isIn: symbol.Variance == VarianceKind.In,
		isInType: symbol.TypeParameterKind == TypeParameterKind.Type,
		isInMethod: symbol.TypeParameterKind == TypeParameterKind.Method
	);
}

public record NodeAttribute(
	NodeTypeReference? type
) {
	public static NodeAttribute from(AttributeData data) => new(
		type: data.AttributeClass?.let(NodeTypeReference.from)
	);
}

public record NodeTypeReference(
	string? @namespace,
	string name,
	TypeKind typeKind,
	NodeTypeParameter? typeParameter,
	List<NodeTypeParameter>? typeParameters
) {
	public static NodeTypeReference from(ITypeSymbol symbol) => new(
		@namespace: symbol.ContainingNamespace?.ToDisplayString(),
		name: symbol.Name,
		typeKind: symbol.TypeKind,
		typeParameter: symbol is ITypeParameterSymbol typeParameterSymbol
			? NodeTypeParameter.from(typeParameterSymbol)
			: null,
		typeParameters: symbol is INamedTypeSymbol namedTypeSymbol
			? namedTypeSymbol.TypeParameters.Select(NodeTypeParameter.from).ToList()
			: null
	);
}

public static class Extensions {
	public static T also<T>(this T value, Action<T> action) {
		action(value);
		return value;
	}

	public static R let<T, R>(this T value, Func<T, R> action) {
		return action(value);
	}
}