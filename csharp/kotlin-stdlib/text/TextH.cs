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

namespace kotlin.text;

[KotlinFileClass]
public class TextH {
	[KotlinExtension]
	[KotlinNotNull]
	public static string replace(
		[KotlinNotNull]
		string receiver,
		[KotlinNotNull]
		string oldValue,
		[KotlinNotNull]
		string newValue,
		[KotlinNotNull]
		bool ignoreCase = false
	) {
		return receiver.Replace(oldValue, newValue, ignoreCase, null);
	}
}