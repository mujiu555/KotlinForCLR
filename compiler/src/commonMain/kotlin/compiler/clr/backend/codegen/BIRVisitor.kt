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

fun BackendIR.visit() = Visitor().run { visit(0) }

private class Visitor {
	fun BackendIR.visit(padding: Int): String = when (this) {
		is BackendIR.None -> ""
		is BackendIR.SingleLineList -> visit(padding)
		is BackendIR.MultiLineList -> visit(padding)
		is BackendIR.SingleLine -> visit(padding)
		is BackendIR.MultiLine -> visit(padding)
		is BackendIR.StringConcatenation -> visit(padding)
		is PlainIR.Plain -> visit(padding)
		is PlainIR.SingleLine -> visit(padding)
		is PlainIR.MultiLine -> visit(padding)
		is PaddingIR.If -> visit(padding)
		is PaddingIR.IfExp -> visit(padding)
		is PaddingIR.Block -> visit(padding)
		is PaddingIR.BlockList -> visit(padding)
	}

	private fun BackendIR.SingleLineList.visit(padding: Int) = buildString {
		append(nodes.joinToString("") { it.visit(padding) })
	}

	private fun BackendIR.MultiLineList.visit(padding: Int) = buildString {
		append(nodes.joinToString("\n") { it.visit(padding) })
	}

	private fun BackendIR.SingleLine.visit(padding: Int) = buildString {
		repeat(padding) { append("    ") }
		append(nodes.joinToString("") { it.visit(padding) })
	}

	private fun BackendIR.MultiLine.visit(padding: Int) = nodes.joinToString("\n") {
		buildString {
			when (it) {
				is BackendIR.None,
				is BackendIR.SingleLine,
				is PaddingIR.Block,
					-> {
				}

				else -> repeat(padding) { append("    ") }
			}
			append(it.visit(padding))
		}
	}

	private fun BackendIR.StringConcatenation.visit(padding: Int) = nodes.joinToString("", "$\"", "\"") {
		"{(${it.visit(padding)})}"
	}

	private fun PlainIR.Plain.visit(padding: Int) = text

	private fun PlainIR.SingleLine.visit(padding: Int) = buildString {
		repeat(padding) { append("    ") }
		append(nodes.joinToString("") { it.visit(padding) })
	}

	private fun PlainIR.MultiLine.visit(padding: Int) = nodes.joinToString("\n") {
		buildString {
			repeat(padding) { append("    ") }
			append(it.visit(padding))
		}
	}

	private fun PaddingIR.If.visit(padding: Int) = buildString {
		repeat(padding) { append("    ") }
		append("if (")
		append(condition.visit(padding))
		appendLine(")")
		append(content.visit(padding))
		if (elseContent != noneIR) {
			appendLine()
			repeat(padding) { append("    ") }
			appendLine("else")
			append(elseContent.visit(padding))
		}
	}

	private fun PaddingIR.IfExp.visit(padding: Int) = buildString {
		append("(")
		append(condition.visit(padding))
		appendLine(")")
		repeat(padding + 1) { append("    ") }
		append("? ")
		when (content.first is PaddingIR.Block) {
			true -> {
				append("(")
				append("(global::System.Func<${content.second}>)")
				append("(")
				appendLine("() =>")
				append(content.first.visit(padding + 1))
				append(")")
				append(")")
				append("()")
			}
			else -> {
				append("(")
				append(content.first.visit(padding + 1))
				append(")")
			}
		}

		appendLine()
		repeat(padding + 1) { append("    ") }
		append(": ")
		when (elseContent.first is PaddingIR.Block) {
			true -> {
				append("(")
				append("(global::System.Func<${elseContent.second}>)")
				append("(")
				appendLine("() =>")
				append(elseContent.first.visit(padding + 1))
				append(")")
				append(")")
				append("()")
			}
			else -> {
				append("(")
				append(elseContent.first.visit(padding + 1))
				append(")")
			}
		}
	}

	private fun PaddingIR.Block.visit(padding: Int) = buildString {
		repeat(padding) { append("    ") }
		append("{")
		appendLine()
		append(nodes.joinToString("\n") { it.visit(padding + 1) })
		appendLine()
		repeat(padding) { append("    ") }
		append("}")
	}

	private fun PaddingIR.BlockList.visit(padding: Int) = buildString {
		append("{")
		appendLine()
		append(nodes.joinToString("\n") { it.visit(padding + 1) })
		appendLine()
		repeat(padding) { append("    ") }
		append("}")
	}
}