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

using kotlin.clr;

namespace kotlin;

public class Deprecated(
	[KotlinNotNull]
	string message,
	ReplaceWith? replaceWith = null,
	[KotlinNotNull]
	DeprecationLevel level = DeprecationLevel.WARNING
) : Attribute {
	[KotlinNotNull]
	public string message { get; } = message;
	[KotlinNotNull]
	public ReplaceWith replaceWith { get; } = replaceWith ?? new ReplaceWith("");
	[KotlinNotNull]
	public DeprecationLevel level { get; } = level;
}

public class DeprecatedSinceKotlin(
	[KotlinNotNull]
	string warningSince = "",
	[KotlinNotNull]
	string errorSince = "",
	[KotlinNotNull]
	string hiddenSince = ""
) : Attribute {
	[KotlinNotNull]
	public string warningSince { get; } = warningSince;
	[KotlinNotNull]
	public string errorSince { get; } = errorSince;
	[KotlinNotNull]
	public string hiddenSince { get; } = hiddenSince;
}

public class ReplaceWith(
	[KotlinNotNull]
	string expression,
	[KotlinNotNull]
	params string[] imports
) : Attribute {
	[KotlinNotNull]
	public string expression { get; } = expression;
	[KotlinNotNull]
	public string[] imports { get; } = imports;
}

public enum DeprecationLevel {
	WARNING,
	ERROR,
	HIDDEN
}

public class ExtensionFunctionType : Attribute;

public class ContextFunctionTypeParams([KotlinNotNull] int count) : Attribute {
	[KotlinNotNull]
	public int count { get; } = count;
}

public class ParameterName([KotlinNotNull] string name) : Attribute {
	[KotlinNotNull]
	public string name { get; } = name;
}

public class Suppress([KotlinNotNull] params string[] names) : Attribute {
	[KotlinNotNull]
	public string[] names { get; } = names;
}
public class UnsafeVariance : Attribute;

public class SinceKotlin([KotlinNotNull] string version) : Attribute {
	[KotlinNotNull]
	public string version { get; } = version;
}

public class DslMarker : Attribute;

public class PublishedApi : Attribute;