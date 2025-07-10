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

package compiler.clr.backend.lower

import compiler.clr.backend.ClrBackendContext
import org.jetbrains.kotlin.backend.common.ClassLoweringPass
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.backend.common.phaser.PhaseDescription
import org.jetbrains.kotlin.backend.jvm.hasMangledParameters
import org.jetbrains.kotlin.backend.jvm.originalConstructorOfThisMfvcConstructorReplacement
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.builders.declarations.addConstructor
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irDelegatingConstructorCall
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.copyAnnotationsFrom
import org.jetbrains.kotlin.ir.util.hasDefaultValue
import org.jetbrains.kotlin.ir.util.passTypeArgumentsFrom

@PhaseDescription(
	name = "JvmDefaultConstructor"
)
internal class ClrDefaultConstructorLowering(val context: ClrBackendContext) : ClassLoweringPass {
	@OptIn(UnsafeDuringIrConstructionAPI::class)
	override fun lower(irClass: IrClass) {
		if (irClass.kind != ClassKind.CLASS || irClass.visibility == DescriptorVisibilities.LOCAL || irClass.isValue || irClass.isInner ||
			irClass.modality == Modality.SEALED
		)
			return

		// Skip if the default constructor is already defined by user.
		if (irClass.constructors.any { it.parameters.isEmpty() })
			return

		val primaryConstructor = irClass.constructors.firstOrNull { it.isPrimary } ?: return
		if (DescriptorVisibilities.isPrivate(primaryConstructor.visibility))
			return

		if ((primaryConstructor.originalConstructorOfThisMfvcConstructorReplacement ?: primaryConstructor).hasMangledParameters())
			return

		if (primaryConstructor.parameters.any { !it.hasDefaultValue() })
			return

		irClass.addConstructor {
			visibility = primaryConstructor.visibility
		}.apply {
			val irBuilder = context.createIrBuilder(this.symbol, startOffset, endOffset)
			copyAnnotationsFrom(primaryConstructor)
			body = irBuilder.irBlockBody {
				+irDelegatingConstructorCall(primaryConstructor).apply {
					passTypeArgumentsFrom(irClass)
					passTypeArgumentsFrom(primaryConstructor, irClass.typeParameters.size)
				}
			}
		}
	}
}