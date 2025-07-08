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

package compiler.clr.backend

import compiler.clr.backend.mapping.IrTypeMapper
import org.jetbrains.kotlin.backend.common.CommonBackendContext
import org.jetbrains.kotlin.backend.common.Mapping
import org.jetbrains.kotlin.backend.common.ir.Ir
import org.jetbrains.kotlin.backend.common.ir.SharedVariablesManager
import org.jetbrains.kotlin.backend.common.lower.InnerClassesSupport
import org.jetbrains.kotlin.backend.jvm.JvmInnerClassesSupport
import org.jetbrains.kotlin.backend.jvm.JvmIrTypeSystemContext
import org.jetbrains.kotlin.backend.jvm.JvmLoweredStatementOrigin
import org.jetbrains.kotlin.ir.IrBuiltIns
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.IrFactoryImpl
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrSetValue
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.symbols.IrValueSymbol
import org.jetbrains.kotlin.ir.types.IrTypeSystemContext
import org.jetbrains.kotlin.ir.util.SymbolTable
import org.jetbrains.kotlin.resolve.jvm.JvmClassName

/**
 * CLR后端上下文，提供编译期间所需的核心功能
 */
class ClrBackendContext(
    val state: GenerationState,
    override val irBuiltIns: IrBuiltIns,
    val symbolTable: SymbolTable
) : CommonBackendContext {
    // 基础组件
    override val irFactory: IrFactory = IrFactoryImpl
    override val typeSystem: IrTypeSystemContext = JvmIrTypeSystemContext(irBuiltIns)
    
    // 映射器
    val defaultTypeMapper = IrTypeMapper(this)

    // 支持组件
    override val innerClassesSupport: InnerClassesSupport = JvmInnerClassesSupport(irFactory)
    override val mapping: Mapping = Mapping()
    override val ir = ClrIr()

    // 共享变量管理
    override val sharedVariablesManager = object : SharedVariablesManager {
        override fun declareSharedVariable(originalDeclaration: IrVariable): IrVariable {
            TODO("简化版本暂不实现共享变量管理")
        }

        override fun defineSharedValue(
            originalDeclaration: IrVariable,
            sharedVariableDeclaration: IrVariable
        ): IrStatement {
            TODO("简化版本暂不实现共享变量管理") 
        }

        override fun getSharedValue(
            sharedVariableSymbol: IrValueSymbol,
            originalGet: IrGetValue
        ): IrExpression {
            TODO("简化版本暂不实现共享变量管理")
        }

        override fun setSharedValue(
            sharedVariableSymbol: IrValueSymbol,
            originalSet: IrSetValue
        ): IrExpression {
            TODO("简化版本暂不实现共享变量管理")
        }
    }
    
    // 状态标记
    override var inVerbosePhase: Boolean = false
    override val configuration get() = state.configuration
    
    // 循环优化配置
    override val preferJavaLikeCounterLoop: Boolean = true
    override val optimizeLoopsOverUnsignedArrays: Boolean = true
    override val doWhileCounterLoopOrigin: IrStatementOrigin get() = JvmLoweredStatementOrigin.DO_WHILE_COUNTER_LOOP
    override val optimizeNullChecksUsingKotlinNullability: Boolean = false

    /**
     * CLR符号表，包含所有CLR平台特定的符号引用
     */
    inner class ClrIr : Ir() {
        override val symbols = ClrSymbols(this@ClrBackendContext)
        override fun shouldGenerateHandlerParameterForDefaultBodyFun() = true
    }

    // 用于多文件外观类（MultifileFacade）处理
    val multifileFacadesToAdd = mutableMapOf<JvmClassName, MutableList<IrClass>>()
}
