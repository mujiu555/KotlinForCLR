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

package compiler.clr.frontend

import org.jetbrains.kotlin.platform.SimplePlatform
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.TargetPlatformVersion

abstract class ClrPlatform : SimplePlatform("CLR") {
	override val oldFashionedDescription: String
		get() = "CLR "
}

class ClrPlatformImpl : ClrPlatform() {
	override fun toString(): String = platformName

	override val oldFashionedDescription: String
		get() = "CLR"

	override val targetPlatformVersion: TargetPlatformVersion
		get() = TargetPlatformVersion.NoVersion

	override fun equals(other: Any?): Boolean = other is ClrPlatformImpl
	override fun hashCode(): Int = ClrPlatformImpl::class.hashCode()
}

object ClrPlatforms {
	private val UNSPECIFIED_SIMPLE_CLR_PLATFORM = ClrPlatformImpl()

	val unspecifiedClrPlatform: TargetPlatform
		get() = CompatClrPlatform

	object CompatClrPlatform : TargetPlatform(setOf(UNSPECIFIED_SIMPLE_CLR_PLATFORM))
}