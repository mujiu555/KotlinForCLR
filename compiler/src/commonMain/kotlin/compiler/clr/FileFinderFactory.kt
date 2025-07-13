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

package compiler.clr

import compiler.clr.frontend.NodeAssembly
import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import org.jetbrains.kotlin.com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.load.kotlin.MetadataFinderFactory
import org.jetbrains.kotlin.load.kotlin.VirtualFileFinder
import org.jetbrains.kotlin.load.kotlin.VirtualFileFinderFactory
import org.jetbrains.kotlin.serialization.deserialization.KotlinMetadataFinder
import org.jetbrains.kotlin.util.PerformanceManager

class ClrAssemblyFileFinderFactory(
	private val assemblies: Map<String, NodeAssembly>,
	private val perfManager: PerformanceManager?,
) : VirtualFileFinderFactory {
	// 确保即使没有显式加载System程序集也能处理
	private fun getEffectiveAssemblies(): Map<String, NodeAssembly> {
		return assemblies
	}
	
	override fun create(scope: GlobalSearchScope): VirtualFileFinder {
		return ClrAssemblyFileFinder(getEffectiveAssemblies(), scope, perfManager)
	}

	override fun create(
		project: Project,
		module: ModuleDescriptor,
	): VirtualFileFinder {
		return ClrAssemblyFileFinder(getEffectiveAssemblies(), GlobalSearchScope.allScope(project), perfManager)
	}
}

class ClrMetadataFinderFactory(
	private val fileFinderFactory: ClrAssemblyFileFinderFactory,
) : MetadataFinderFactory {
	override fun create(scope: GlobalSearchScope): KotlinMetadataFinder =
		fileFinderFactory.create(scope)

	override fun create(
		project: Project,
		module: ModuleDescriptor,
	): KotlinMetadataFinder =
		fileFinderFactory.create(project, module)
}