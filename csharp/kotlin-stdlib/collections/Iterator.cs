using System.Collections;
using kotlin.clr;

namespace kotlin.collections;

public class KotlinIterator<T> : IEnumerator<T> {
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

	[KotlinNotNull]
	public bool MoveNext() => enumerator.MoveNext();
	
	public void Reset() => enumerator.Reset();
	
	[KotlinNotNull]
	public T Current => enumerator.Current;
	
	object? IEnumerator.Current => Current;
	
	public void Dispose() => enumerator.Dispose();
}