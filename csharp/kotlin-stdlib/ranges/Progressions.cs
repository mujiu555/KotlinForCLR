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
using kotlin.collections;

namespace kotlin.ranges;

public class CharProgression {
	internal CharProgression(
		[KotlinNotNull]
		char first,
		[KotlinNotNull]
		char endInclusive,
		[KotlinNotNull]
		int step
	) {
		this.first = first;
		this.step = step;
	}
	
	[KotlinNotNull]
	public char first { get; }
	
	[KotlinNotNull]
	public int step { get; }
}

public class IntProgression {
	internal IntProgression(
		[KotlinNotNull]
		int first,
		[KotlinNotNull]
		int endInclusive,
		[KotlinNotNull]
		int step
	) {
		this.first = first;
		this.step = step;
	}

	[KotlinNotNull]
	 public int first { get; }

	[KotlinNotNull]
	 public int step { get; }

	[KotlinNotNull]
	public IntIterator iterator() => new IntProgressionIterator(first, 0, step);
}

public class LongProgression {
	internal LongProgression(
		[KotlinNotNull]
		long first,
		[KotlinNotNull]
		long endInclusive,
		[KotlinNotNull]
		long step
	) {
		this.first = first;
		this.step = step;
	}
	
	[KotlinNotNull]
	public long first { get; }
	
	[KotlinNotNull]
	public long step { get; }
}