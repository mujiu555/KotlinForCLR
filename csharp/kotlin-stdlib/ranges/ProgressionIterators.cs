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
using kotlin.collections;

namespace kotlin.ranges;

public class CharProgressionIterator : CharIterator {
	private int step;
	private int finalElement;
	private bool property_hasNext;
	private int next;

	public CharProgressionIterator(char first, char last, int step) {
		this.step = step;
		this.finalElement = last;
		this.property_hasNext = step > 0 ? first <= last : first >= last;
		this.next = property_hasNext ? first : finalElement;
	}

	public override bool hasNext() => property_hasNext;

	public override char nextChar() {
		var value = next;
		if (value == finalElement) {
			if (!property_hasNext) throw new Exception("NoSuchElementException");
			property_hasNext = false;
		} else {
			next += step;
		}

		return (char)value;
	}
}

public class IntProgressionIterator : IntIterator {
	private int step;
	private int finalElement;
	private bool property_hasNext;
	private int next;

	public IntProgressionIterator(int first, int last, int step) {
		this.step = step;
		this.finalElement = last;
		this.property_hasNext = step > 0 ? first <= last : first >= last;
		this.next = property_hasNext ? first : finalElement;
	}

	public override bool hasNext() => property_hasNext;

	public override int nextInt() {
		var value = next;
		if (value == finalElement) {
			if (!property_hasNext) throw new Exception("NoSuchElementException");
			property_hasNext = false;
		} else {
			next += step;
		}

		return value;
	}
}

public class LongProgressionIterator : LongIterator {
	private long step;
	private long finalElement;
	private bool property_hasNext;
	private long next;

	public LongProgressionIterator(long first, long last, long step) {
		this.step = step;
		this.finalElement = last;
		this.property_hasNext = step > 0 ? first <= last : first >= last;
		this.next = property_hasNext ? first : finalElement;
	}

	public override bool hasNext() => property_hasNext;

	public override long nextLong() {
		var value = next;
		if (value == finalElement) {
			if (!property_hasNext) throw new Exception("NoSuchElementException");
			property_hasNext = false;
		} else {
			next += step;
		}

		return value;
	}
}