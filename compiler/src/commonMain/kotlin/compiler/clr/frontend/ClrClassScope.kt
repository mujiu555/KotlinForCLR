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

import compiler.clr.frontend.symbol.ClrSymbolProvider
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.resolve.ScopeSession
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.scopes.DelicateScopeAPI
import org.jetbrains.kotlin.fir.scopes.FirContainingNamesAwareScope
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirVariableSymbol
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.utils.addToStdlib.UnsafeCastFunction

// CLR类成员作用域
class ClrClassMemberScope(
    private val klass: FirClass,
    private val session: FirSession,
    private val scopeSession: ScopeSession,
    private val delegatedScope: FirContainingNamesAwareScope
) : FirContainingNamesAwareScope() {
    override fun getCallableNames(): Set<Name> = delegatedScope.getCallableNames()
    override fun getClassifierNames(): Set<Name> = delegatedScope.getClassifierNames()
    
    @DelicateScopeAPI
    override fun withReplacedSessionOrNull(newSession: FirSession, newScopeSession: ScopeSession): FirContainingNamesAwareScope? {
        if (newSession == session && newScopeSession == scopeSession) return this
        val newDelegateScope = delegatedScope.withReplacedSessionOrNull(newSession, newScopeSession) ?: return null
        return ClrClassMemberScope(klass, newSession, newScopeSession, newDelegateScope)
    }
    
    @OptIn(SymbolInternals::class, UnsafeCastFunction::class)
    override fun processFunctionsByName(name: Name, processor: (FirNamedFunctionSymbol) -> Unit) {
        delegatedScope.processFunctionsByName(name, processor)

        val symbolProvider = session.symbolProvider as? ClrSymbolProvider
            ?: return
            
        val classId = klass.symbol.classId
        if (symbolProvider.hasClassSymbol(classId)) {
            val symbol = symbolProvider.getClassLikeSymbolByClassId(classId) as FirRegularClassSymbol
            symbol.fir.declarations
                .filter { it is FirSimpleFunction }
                .map { it as FirSimpleFunction }
                .map { it.symbol }
                .forEach(processor)
        }
    }

    override fun processDeclaredConstructors(processor: (FirConstructorSymbol) -> Unit) {
        delegatedScope.processDeclaredConstructors(processor)
    }

    override fun processPropertiesByName(
        name: Name,
        processor: (FirVariableSymbol<*>) -> Unit,
    ) {
        delegatedScope.processPropertiesByName(name, processor)
    }
}