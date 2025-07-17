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
using System.Collections;
using System.Collections.Generic;
using kotlin.clr;

namespace kotlin.collections;

public interface Iterator<T> {
	public bool hasNext();
	public T next();
}

public class KotlinIterator<T> : Iterator<T> {
	public KotlinIterator([KotlinNotNull] IEnumerator<T> enumerator) {
		this.enumerator = enumerator;
	}
	
	private readonly IEnumerator<T> enumerator;
	private bool hasAdvanced;
	private bool hasNextResult;

	[KotlinNotNull]
	public bool hasNext() {
		if (!hasAdvanced) {
			hasNextResult = enumerator.MoveNext();
			hasAdvanced = true;
		}

		return hasNextResult;
	}

	[KotlinNotNull]
	public T next() {
		if (!hasAdvanced) {
			hasNext();
		}

		if (!hasNextResult) throw new Exception("No Such Element");
		hasAdvanced = false;
		return enumerator.Current;
	}
}