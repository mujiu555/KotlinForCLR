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

namespace kotlin.collections;

public abstract class ByteIterator : Iterator<sbyte> {
	public abstract bool hasNext();
	public sbyte next() => nextByte();
	public abstract sbyte nextByte();
}

public abstract class CharIterator : Iterator<char> {
	public abstract bool hasNext();
	public char next() => nextChar();
	public abstract char nextChar();
}

public abstract class ShortIterator : Iterator<short> {
	public abstract bool hasNext();
	public short next() => nextShort();
	public abstract short nextShort();
}

public abstract class IntIterator : Iterator<int> {
	public abstract bool hasNext();
	public int next() => nextInt();
	public abstract int nextInt();
}

public abstract class LongIterator : Iterator<long> {
	public abstract bool hasNext();
	public long next() => nextLong();
	public abstract long nextLong();
}

public abstract class FloatIterator : Iterator<float> {
	public abstract bool hasNext();
	public float next() => nextFloat();
	public abstract float nextFloat();
}

public abstract class DoubleIterator : Iterator<double> {
	public abstract bool hasNext();
	public double next() => nextDouble();
	public abstract double nextDouble();
}

public abstract class BooleanIterator : Iterator<bool> {
	public abstract bool hasNext();
	public bool next() => nextBoolean();
	public abstract bool nextBoolean();
}