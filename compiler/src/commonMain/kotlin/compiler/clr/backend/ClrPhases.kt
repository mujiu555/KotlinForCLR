package compiler.clr.backend

import org.jetbrains.kotlin.config.phaser.NamedCompilerPhase
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

val clrPhases: List<NamedCompilerPhase<ClrBackendContext, IrModuleFragment, IrModuleFragment>>
	get() = clrLoweringPhases