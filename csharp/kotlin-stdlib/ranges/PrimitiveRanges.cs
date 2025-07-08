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

namespace kotlin.ranges;

public sealed class CharRange(
	[KotlinNotNull]
	char start,
	[KotlinNotNull]
	char endInclusive
) : CharProgression(start, endInclusive, 1), ClosedRange, OpenEndRange;

public sealed class IntRange(
	[KotlinNotNull]
	int start,
	[KotlinNotNull]
	int endInclusive
) : IntProgression(start, endInclusive, 1), ClosedRange, OpenEndRange;

public sealed class LongRange(
	[KotlinNotNull]
	long start,
	[KotlinNotNull]
	long endInclusive
) : LongProgression(start, endInclusive, 1), ClosedRange, OpenEndRange;