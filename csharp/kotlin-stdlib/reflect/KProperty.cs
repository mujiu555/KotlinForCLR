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

namespace kotlin.reflect;

public interface KProperty<out V> : KCallable<V>;
public interface KMutableProperty<V> : KProperty<V>;

public interface KProperty0<out V> : KProperty<V>;
public interface KMutableProperty0<V> : KProperty0<V>, KMutableProperty<V>;
public interface KProperty1<T, out V> : KProperty<V>;
public interface KMutableProperty1<T, V> : KProperty1<T, V>, KMutableProperty<V>;
public interface KProperty2<D, E, out V> : KProperty<V>;
public interface KMutableProperty2<D, E, V> : KProperty2<D, E, V>, KMutableProperty<V>;