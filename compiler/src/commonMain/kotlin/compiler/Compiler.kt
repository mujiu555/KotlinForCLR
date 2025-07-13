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

package compiler

import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.config.Services
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.util.PerformanceManager
import org.jetbrains.kotlin.util.PerformanceManagerImpl
import java.io.PrintStream

abstract class Compiler<A : CommonCompilerArguments> {
	abstract val platform: TargetPlatform

	open val defaultPerformanceManager: PerformanceManager by lazy {
		PerformanceManagerImpl(platform, "Kotlin to ${if (platform.isCommon()) "Metadata" else platform.first().platformName} compiler")
	}

	abstract fun doExecute(
		arguments: A,
		services: Services,
		collector: MessageCollector
	): ExitCode

	fun exec(
		errStream: PrintStream,
		arguments: A,
		messageRenderer: MessageRenderer = defaultMessageRenderer()
	): ExitCode {
		val collector = PrintingMessageCollector(errStream, messageRenderer, arguments.verbose)
		return doExecute(arguments, Services.Companion.EMPTY, collector)
	}

	companion object {
		protected fun defaultMessageRenderer(): MessageRenderer =
			when (System.getProperty(MessageRenderer.PROPERTY_KEY)) {
				MessageRenderer.XML.name -> MessageRenderer.XML
				MessageRenderer.GRADLE_STYLE.name -> MessageRenderer.GRADLE_STYLE
				MessageRenderer.XCODE_STYLE.name -> MessageRenderer.XCODE_STYLE
				MessageRenderer.WITHOUT_PATHS.name -> MessageRenderer.WITHOUT_PATHS
				MessageRenderer.PLAIN_FULL_PATHS.name -> MessageRenderer.PLAIN_FULL_PATHS
				else -> MessageRenderer.PLAIN_RELATIVE_PATHS
			}
	}
}