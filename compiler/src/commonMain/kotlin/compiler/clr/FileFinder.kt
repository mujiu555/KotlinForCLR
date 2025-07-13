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
import compiler.clr.frontend.NodeType
import org.jetbrains.kotlin.com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.com.intellij.openapi.vfs.VirtualFileListener
import org.jetbrains.kotlin.com.intellij.openapi.vfs.VirtualFileSystem
import org.jetbrains.kotlin.com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.load.kotlin.VirtualFileFinder
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.util.PerformanceManager
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap

class ClrAssemblyFileFinder(
	private val assemblies: Map<String, NodeAssembly>,
	private val scope: GlobalSearchScope,
	perfManager: PerformanceManager?,
) : VirtualFileFinder(perfManager) {
	// 创建NodeAssembly到虚拟文件的映射
	private val virtualFileCache = ConcurrentHashMap<ClassId, VirtualFile>()

	// 将NodeType转换为虚拟文件
	private fun createVirtualFileForType(assembly: NodeAssembly, type: NodeType): VirtualFile {
		val packageName = type.namespace
		val className = type.name

		return ClrAssemblyVirtualFile(
			className,
			assembly.name,
			packageName,
			type
		)
	}

	// 查找指定ClassId对应的类型
	private fun findType(classId: ClassId): Pair<NodeAssembly, NodeType>? {
		val packageName = classId.packageFqName.asString()
		val className = classId.relativeClassName.asString()
		
		// 从所有程序集中查找匹配的类型
		return assemblies.values.firstNotNullOfOrNull { assembly ->
			assembly.types.find {
				it.namespace == packageName && it.name == className.replace('$', '+')
			}?.let { assembly to it }
		}
	}

	override fun findVirtualFileWithHeader(classId: ClassId): VirtualFile? {
		return virtualFileCache.getOrPut(classId) {
			val (assembly, type) = findType(classId) ?: return null
			createVirtualFileForType(assembly, type)
		}
	}

	override fun findSourceOrBinaryVirtualFile(classId: ClassId): VirtualFile? {
		return findVirtualFileWithHeader(classId)
	}

	override fun findMetadata(classId: ClassId): InputStream? {
		assert(!classId.isNestedClass) { "Nested classes are not supported here: $classId" }

		val (assembly, type) = findType(classId) ?: return null

		// 创建包含序列化类型信息的元数据流
		return createSerializedMetadata(assembly, type)
	}

	private fun createSerializedMetadata(assembly: NodeAssembly, type: NodeType): InputStream {
		// 这里需要创建兼容Kotlin元数据格式的序列化数据
		// 简化版本：只返回类型的基本信息
		val metadata = buildString {
			append("KotlinClrMetadata\n")
			append("Assembly: ${assembly.name}\n")
			append("Type: ${type.namespace}.${type.name}\n")
			// 添加类型详细信息...
		}

		return metadata.byteInputStream()
	}

	override fun findMetadataTopLevelClassesInPackage(packageFqName: FqName): Set<String> {
		val result = HashSet<String>()
		val packageName = packageFqName.asString()

		for (assembly in assemblies.values) {
			for (type in assembly.types) {
				if (type.namespace == packageName && !type.isNested) {
					result.add(type.name)
				}
			}
		}

		return result
	}

	override fun hasMetadataPackage(fqName: FqName): Boolean {
		val packageName = fqName.asString()

		return assemblies.values.any { assembly ->
			assembly.types.any { it.namespace == packageName }
		}
	}

	override fun findBuiltInsData(packageFqName: FqName): InputStream? {
		// CLR内置类型处理
		// 如果是kotlin包，需要提供标准库类型映射
		if (packageFqName.asString() == "kotlin") {
			return createBuiltInsMetadata()
		}
		return null
	}

	private fun createBuiltInsMetadata(): InputStream {
		// 创建内置类型元数据
		// 需要映射CLR基础类型到Kotlin类型
		val builtins = """
            kotlin.Any -> System.Object
            kotlin.Int -> System.Int32
            kotlin.String -> System.String
            kotlin.Unit -> System.Void
            // 其他基本类型映射...
        """.trimIndent()

		return builtins.byteInputStream()
	}
}

// CLR程序集虚拟文件实现
class ClrAssemblyVirtualFile(
	private val name: String,
	private val assemblyName: String?,
	private val packageName: String?,
	private val nodeType: NodeType
) : VirtualFile() {
	private val content = lazy {
		// 生成表示此类型的序列化内容
		buildString {
			append("// CLR Type: $packageName.$name\n")
			append("// Assembly: $assemblyName\n")
			// 添加更多类型信息...
		}.toByteArray()
	}

	override fun getName(): String = "$name.class"

	override fun getFileSystem(): VirtualFileSystem = ClrAssemblyFileSystem.INSTANCE

	override fun getPath(): String = "clr://$assemblyName/$packageName/$name.class"

	override fun isWritable(): Boolean = false

	override fun isDirectory(): Boolean = false

	override fun isValid(): Boolean = true

	override fun getParent(): VirtualFile? = null

	override fun getChildren(): Array<VirtualFile> = emptyArray()

	override fun getOutputStream(requestor: Any?, newModificationStamp: Long, newTimeStamp: Long): OutputStream {
		throw UnsupportedOperationException()
	}

	override fun contentsToByteArray(): ByteArray = content.value

	override fun getTimeStamp(): Long = 0

	override fun getLength(): Long = content.value.size.toLong()

	override fun refresh(asynchronous: Boolean, recursive: Boolean, postRunnable: Runnable?) {}

	override fun getInputStream(): InputStream = content.value.inputStream()
}

// CLR程序集文件系统
class ClrAssemblyFileSystem private constructor() : VirtualFileSystem() {
	override fun getProtocol(): String = "clr"

	override fun findFileByPath(path: String): VirtualFile? = null

	override fun refresh(asynchronous: Boolean) {}

	override fun refreshAndFindFileByPath(path: String): VirtualFile? = null

	override fun addVirtualFileListener(p0: VirtualFileListener) {
		// 程序集文件系统是静态的，不需要监听器
	}

	override fun removeVirtualFileListener(p0: VirtualFileListener) {
		// 程序集文件系统是静态的，不需要监听器
	}

	override fun deleteFile(p0: Any?, p1: VirtualFile) {
		throw UnsupportedOperationException("CLR程序集文件系统是只读的")
	}

	override fun moveFile(
		p0: Any?,
		p1: VirtualFile,
		p2: VirtualFile,
	) {
		throw UnsupportedOperationException("CLR程序集文件系统是只读的")
	}

	override fun renameFile(
		p0: Any?,
		p1: VirtualFile,
		p2: String,
	) {
		throw UnsupportedOperationException("CLR程序集文件系统是只读的")
	}

	override fun createChildFile(
		p0: Any?,
		p1: VirtualFile,
		p2: String,
	): VirtualFile {
		throw UnsupportedOperationException("CLR程序集文件系统是只读的")
	}

	override fun createChildDirectory(
		p0: Any?,
		p1: VirtualFile,
		p2: String,
	): VirtualFile {
		throw UnsupportedOperationException("CLR程序集文件系统是只读的")
	}

	override fun copyFile(
		p0: Any?,
		p1: VirtualFile,
		p2: VirtualFile,
		p3: String,
	): VirtualFile {
		throw UnsupportedOperationException("CLR程序集文件系统是只读的")
	}

	override fun isReadOnly(): Boolean = true

	companion object {
		val INSTANCE = ClrAssemblyFileSystem()
	}
}