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

import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.name

fun Map<IrFile, BackendIR>.render() = Renderer().run {
	map { (file, node) ->
		buildString {
			append("    <File name=\"${file.name}\">")
			appendLine()
			append(node.render(2))
			appendLine()
			append("    </File>")
		}
	}.joinToString(
		separator = "\n",
		prefix = "<Root>\n",
		postfix = "\n</Root>"
	)
}

private class Renderer {
	fun BackendIR.render(padding: Int): String = when (this) {
		is BackendIR.None -> render(padding)
		is BackendIR.SingleLineList -> render(padding)
		is BackendIR.MultiLineList -> render(padding)
		is BackendIR.SingleLine -> render(padding)
		is BackendIR.MultiLine -> render(padding)
		is BackendIR.StringConcatenation -> render(padding)
		is PlainIR.Plain -> render(padding)
		is PlainIR.SingleLine -> render(padding)
		is PlainIR.MultiLine -> render(padding)
		is PaddingIR.If -> render(padding)
		is PaddingIR.IfExp -> render(padding)
		is PaddingIR.Block -> render(padding)
		is PaddingIR.BlockList -> render(padding)
	}

	private fun BackendIR.None.render(padding: Int) = buildString {
		repeat(padding) { append("    ") }
		append("<CodeNode.None />")
	}

	private fun BackendIR.SingleLineList.render(padding: Int) = buildString {
		repeat(padding) { append("    ") }
		appendLine("<CodeNode.SingleLineList>")

		appendLine(nodes.joinToString("\n") { it.render(padding + 1) })

		repeat(padding) { append("    ") }
		append("</CodeNode.SingleLineList>")
	}

	private fun BackendIR.MultiLineList.render(padding: Int) = buildString {
		repeat(padding) { append("    ") }
		appendLine("<CodeNode.MultiLineList>")

		appendLine(nodes.joinToString("\n") { it.render(padding + 1) })

		repeat(padding) { append("    ") }
		append("</CodeNode.MultiLineList>")
	}

	private fun BackendIR.SingleLine.render(padding: Int) = buildString {
		repeat(padding) { append("    ") }
		appendLine("<CodeNode.SingleLine>")

		appendLine(nodes.joinToString("\n") { it.render(padding + 1) })

		repeat(padding) { append("    ") }
		append("</CodeNode.SingleLine>")
	}

	private fun BackendIR.MultiLine.render(padding: Int) = buildString {
		repeat(padding) { append("    ") }
		appendLine("<CodeNode.MultiLine>")

		appendLine(nodes.joinToString("\n") { it.render(padding + 1) })

		repeat(padding) { append("    ") }
		append("</CodeNode.MultiLine>")
	}

	private fun BackendIR.StringConcatenation.render(padding: Int) = buildString {
		repeat(padding) { append("    ") }
		appendLine("<CodeNode.StringConcatenation>")

		appendLine(nodes.joinToString("\n") { it.render(padding + 1) })

		repeat(padding) { append("    ") }
		append("</CodeNode.StringConcatenation>")
	}

	private fun PlainIR.Plain.render(padding: Int) = buildString {
		repeat(padding) { append("    ") }
		append("<PlainNode.Plain>")
		append(text.replace("<", "&lt;").replace(">", "&gt;"))
		append("</PlainNode.Plain>")
	}

	private fun PlainIR.SingleLine.render(padding: Int) = buildString {
		repeat(padding) { append("    ") }
		appendLine("<PlainNode.SingleLine>")

		appendLine(nodes.joinToString("\n") { it.render(padding + 1) })

		repeat(padding) { append("    ") }
		append("</PlainNode.SingleLine>")
	}

	private fun PlainIR.MultiLine.render(padding: Int) = buildString {
		repeat(padding) { append("    ") }
		appendLine("<PlainNode.MultiLine>")

		appendLine(nodes.joinToString("\n") { it.render(padding + 1) })

		repeat(padding) { append("    ") }
		append("</PlainNode.MultiLine>")
	}

	private fun PaddingIR.If.render(padding: Int) = buildString {
		repeat(padding) { append("    ") }
		appendLine("<PaddingNode.If>")

		repeat(padding + 1) { append("    ") }
		appendLine("<condition>")

		appendLine(condition.render(padding + 2))

		repeat(padding + 1) { append("    ") }
		appendLine("</condition>")

		repeat(padding + 1) { append("    ") }
		appendLine("<content>")

		appendLine(content.render(padding + 2))

		repeat(padding + 1) { append("    ") }
		appendLine("</content>")

		repeat(padding + 1) { append("    ") }
		appendLine("<else>")

		appendLine(elseContent.render(padding + 2))

		repeat(padding + 1) { append("    ") }
		appendLine("</else>")

		repeat(padding) { append("    ") }
		append("</PaddingNode.If>")
	}

	private fun PaddingIR.IfExp.render(padding: Int) = buildString {
		repeat(padding) { append("    ") }
		appendLine("<PaddingNode.IfExp>")

		repeat(padding + 1) { append("    ") }
		appendLine("<condition>")

		appendLine(condition.render(padding + 2))

		repeat(padding + 1) { append("    ") }
		appendLine("</condition>")

		repeat(padding + 1) { append("    ") }
		appendLine("<content type=\"${content.second}\">")

		appendLine(content.first.render(padding + 2))

		repeat(padding + 1) { append("    ") }
		appendLine("</content>")

		repeat(padding + 1) { append("    ") }
		appendLine("<else type=\"${content.second}\">")

		appendLine(elseContent.first.render(padding + 2))

		repeat(padding + 1) { append("    ") }
		appendLine("</else>")

		repeat(padding) { append("    ") }
		append("</PaddingNode.IfExp>")
	}

	private fun PaddingIR.Block.render(padding: Int) = buildString {
		repeat(padding) { append("    ") }
		appendLine("<PaddingNode.Block>")

		appendLine(nodes.joinToString("\n") { it.render(padding + 1) })

		repeat(padding) { append("    ") }
		append("</PaddingNode.Block>")
	}

	private fun PaddingIR.BlockList.render(padding: Int) = buildString {
		repeat(padding) { append("    ") }
		appendLine("<PaddingNode.BlockList>")

		appendLine(nodes.joinToString("\n") { it.render(padding + 1) })

		repeat(padding) { append("    ") }
		append("</PaddingNode.BlockList>")
	}
}