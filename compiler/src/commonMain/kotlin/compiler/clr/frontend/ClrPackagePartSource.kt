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

import org.jetbrains.kotlin.descriptors.SourceFile
import org.jetbrains.kotlin.metadata.deserialization.MetadataVersion
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.serialization.deserialization.IncompatibleVersionErrorData
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DeserializedContainerAbiStability
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DeserializedContainerSource
import org.jetbrains.kotlin.serialization.deserialization.descriptors.PreReleaseInfo

class ClrPackagePartSource(
    override val facadeClassId: ClassId,
    override val incompatibility: IncompatibleVersionErrorData<MetadataVersion>? = null,
    override val preReleaseInfo: PreReleaseInfo = PreReleaseInfo.DEFAULT_VISIBLE,
    override val abiStability: DeserializedContainerAbiStability = DeserializedContainerAbiStability.STABLE,
) : DeserializedContainerSource, FacadeClassSource {
    override val presentableString: String
        get() = "Class '$facadeClassId'"

    override fun toString() = "${this::class.java.simpleName}: $facadeClassId"

    override fun getContainingFile(): SourceFile = SourceFile.NO_SOURCE_FILE
}
