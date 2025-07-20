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

package compiler.clr.backend.codegen

sealed class BackendIR {
	data object None : BackendIR()
	data class SingleLineList(val nodes: List<BackendIR>) : BackendIR()
	data class MultiLineList(val nodes: List<BackendIR>) : BackendIR()
	data class SingleLine(val nodes: List<BackendIR>) : BackendIR()
	data class MultiLine(val nodes: List<BackendIR>) : BackendIR()
	data class StringConcatenation(val nodes: List<BackendIR>) : BackendIR()
}

sealed class PlainIR : BackendIR() {
	data class Plain(val text: String) : PlainIR()
	data class SingleLine(val nodes: List<Plain>) : PlainIR()
	data class MultiLine(val nodes: List<Plain>) : PlainIR()
}

sealed class PaddingIR : BackendIR() {
	data class If(
		val condition: BackendIR,
		val content: BackendIR,
		val elseContent: BackendIR,
	) : PaddingIR()

	data class IfExp(
		val condition: BackendIR,
		val content: Pair<BackendIR, String>,
		val elseContent: Pair<BackendIR, String>,
	) : PaddingIR()

	data class Block(
		val nodes: List<BackendIR>,
	) : PaddingIR()

	data class BlockList(
		val nodes: List<BackendIR>,
	) : PaddingIR()
}

val noneIR = BackendIR.None

fun singleLineListIR(vararg nodes: BackendIR) = singleLineListIR(nodes.toList())
fun singleLineListIR(nodes: List<BackendIR>) = BackendIR.SingleLineList(nodes)

fun multiLineListIR(vararg nodes: BackendIR) = multiLineListIR(nodes.toList())
fun multiLineListIR(nodes: List<BackendIR>) = BackendIR.MultiLineList(nodes)

fun singleLineIR(vararg nodes: BackendIR) = singleLineIR(nodes.toList())
fun singleLineIR(nodes: List<BackendIR>) = BackendIR.SingleLine(nodes)

fun multiLineIR(vararg nodes: BackendIR) = multiLineIR(nodes.toList())
fun multiLineIR(nodes: List<BackendIR>) = BackendIR.MultiLine(nodes)

fun stringConcatenationIR(nodes: List<BackendIR>) = BackendIR.StringConcatenation(nodes)

fun plainIR(text: String) = PlainIR.Plain(text)

fun singleLinePlain(vararg nodes: String) = singleLinePlain(nodes.toList())
fun singleLinePlain(nodes: List<String>) = PlainIR.SingleLine(nodes.map { plainIR(it) })

fun multiLinePlain(vararg nodes: String) = multiLinePlain(nodes.toList())
fun multiLinePlain(nodes: List<String>) = PlainIR.MultiLine(nodes.map { plainIR(it) })

fun ifPadding(
	condition: BackendIR,
	content: BackendIR,
	elseContent: BackendIR,
) = PaddingIR.If(condition, content, elseContent)

fun ifExpPadding(
	condition: BackendIR,
	content: Pair<BackendIR, String>,
	elseContent: Pair<BackendIR, String>,
) = PaddingIR.IfExp(condition, content, elseContent)

fun blockPadding(vararg nodes: BackendIR) = blockPadding(nodes.toList())
fun blockPadding(nodes: List<BackendIR>) = PaddingIR.Block(nodes)

fun blockListPadding(vararg nodes: BackendIR) = blockListPadding(nodes.toList())
fun blockListPadding(nodes: List<BackendIR>) = PaddingIR.BlockList(nodes)

fun BackendIR.appendSingleLine(vararg appends: BackendIR) = when (this) {
	is BackendIR.SingleLine -> singleLineIR(*nodes.toTypedArray(), *appends)
	else -> singleLineIR(this, *appends)
}

fun BackendIR.pushSingleLine(vararg appends: BackendIR) = when (this) {
	is BackendIR.SingleLine -> singleLineIR(*appends, *nodes.toTypedArray())
	else -> singleLineIR(*appends, this)
}

fun BackendIR.appendSingleLineList(vararg appends: BackendIR) = when (this) {
	is BackendIR.SingleLineList -> singleLineListIR(*nodes.toTypedArray(), *appends)
	else -> singleLineListIR(this, *appends)
}

fun BackendIR.pushSingleLineList(vararg appends: BackendIR) = when (this) {
	is BackendIR.SingleLineList -> singleLineListIR(*appends, *nodes.toTypedArray())
	else -> singleLineListIR(*appends, this)
}

fun BackendIR.toPadding() = when (this) {
	is BackendIR.MultiLine -> this
	is BackendIR.MultiLineList -> multiLineIR(nodes)
	BackendIR.None -> singleLineIR(this)
	is BackendIR.SingleLine -> this
	is BackendIR.SingleLineList -> singleLineIR(nodes)
	is BackendIR.StringConcatenation -> singleLineIR(this)
	is PaddingIR.Block -> this
	is PaddingIR.BlockList -> blockPadding(nodes)
	is PaddingIR.If -> singleLineIR(this)
	is PaddingIR.IfExp -> singleLineIR(this)
	is PlainIR.MultiLine -> singleLineIR(this)
	is PlainIR.Plain -> singleLineIR(this)
	is PlainIR.SingleLine -> singleLineIR(this)
}