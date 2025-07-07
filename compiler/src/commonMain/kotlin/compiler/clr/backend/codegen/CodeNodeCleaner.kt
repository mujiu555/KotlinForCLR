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

fun CodeNode.clean() = Cleaner().run { clean() }

private class Cleaner {
	fun CodeNode.clean(): CodeNode = when (this) {
		is CodeNode.None -> clean()
		is CodeNode.SingleLineList -> clean()
		is CodeNode.MultiLineList -> clean()
		is CodeNode.SingleLine -> clean()
		is CodeNode.MultiLine -> clean()
		is CodeNode.StringConcatenation -> clean()
		is PlainNode.Plain -> clean()
		is PlainNode.SingleLine -> clean()
		is PlainNode.MultiLine -> clean()
		is PaddingNode.If -> clean()
		is PaddingNode.IfExp -> clean()
		is PaddingNode.Block -> clean()
		is PaddingNode.BlockList -> clean()
	}

	private fun CodeNode.None.clean() = this
	private fun CodeNode.SingleLineList.clean(): CodeNode {
		val list = mutableListOf<CodeNode>()
		when (nodes.size) {
			0 -> {}
			1 -> list += nodes.first().clean()
			else -> {
				var cur = nodes.first().clean()
				nodes.drop(1).map { it.clean() }.forEach {
					when (cur) {
						is CodeNode.None -> {
							list += cur
							cur = it
						}

						is CodeNode.SingleLineList -> {
							list += cur
							cur = it
						}

						is CodeNode.MultiLineList -> {
							list += cur
							cur = it
						}

						is CodeNode.SingleLine -> {
							list += cur
							cur = it
						}

						is CodeNode.MultiLine -> {
							list += cur
							cur = it
						}

						is CodeNode.StringConcatenation -> {
							list += cur
							cur = it
						}

						is PlainNode.Plain -> {
							when (it) {
								is PlainNode.Plain -> {
									cur = plainPlain(cur.text + it.text)
								}

								else -> {
									list += cur
									cur = it
								}
							}
						}

						is PlainNode.SingleLine -> {
							list += cur
							cur = it
						}

						is PlainNode.MultiLine -> {
							list += cur
							cur = it
						}

						is PaddingNode.If -> {
							list += cur
							cur = it
						}

						is PaddingNode.IfExp -> {
							list += cur
							cur = it
						}

						is PaddingNode.Block -> {
							list += cur
							cur = it
						}

						is PaddingNode.BlockList -> {
							list += cur
							cur = it
						}
					}
				}
				list += cur
			}
		}
		if (list.size == 1) {
			return list.single()
		}
		return singleLineListCode(list)
	}

	private fun CodeNode.MultiLineList.clean(): CodeNode {
		val list = mutableListOf<CodeNode>()
		when (nodes.size) {
			0 -> {}
			1 -> list += nodes.first().clean()
			else -> {
				var cur = nodes.first().clean()
				nodes.drop(1).map { it.clean() }.forEach {
					when (cur) {
						is CodeNode.None -> {
							list += cur
							cur = it
						}

						is CodeNode.SingleLineList -> {
							list += cur
							cur = it
						}

						is CodeNode.MultiLineList -> {
							list += cur
							cur = it
						}

						is CodeNode.SingleLine -> {
							list += cur
							cur = it
						}

						is CodeNode.MultiLine -> {
							list += cur
							cur = it
						}

						is CodeNode.StringConcatenation -> {
							list += cur
							cur = it
						}

						is PlainNode.Plain -> {
							when (it) {
								is PlainNode.Plain -> {
									cur = plainPlain(cur.text + it.text)
								}

								else -> {
									list += cur
									cur = it
								}
							}
						}

						is PlainNode.SingleLine -> {
							list += cur
							cur = it
						}

						is PlainNode.MultiLine -> {
							list += cur
							cur = it
						}

						is PaddingNode.If -> {
							list += cur
							cur = it
						}

						is PaddingNode.IfExp -> {
							list += cur
							cur = it
						}

						is PaddingNode.Block -> {
							list += cur
							cur = it
						}

						is PaddingNode.BlockList -> {
							list += cur
							cur = it
						}
					}
				}
				list += cur
			}
		}
		if (list.size == 1) {
			return list.single()
		}
		return multiLineListCode(list)
	}

	private fun CodeNode.SingleLine.clean(): CodeNode {
		val list = mutableListOf<CodeNode>()
		when (nodes.size) {
			0 -> {}
			1 -> list += nodes.first().clean()
			else -> {
				var cur = nodes.first().clean()
				nodes.drop(1).map { it.clean() }.forEach {
					when (cur) {
						is CodeNode.None -> {
							list += cur
							cur = it
						}

						is CodeNode.SingleLineList -> {
							list += cur
							cur = it
						}

						is CodeNode.MultiLineList -> {
							list += cur
							cur = it
						}

						is CodeNode.SingleLine -> {
							list += cur
							cur = it
						}

						is CodeNode.MultiLine -> {
							list += cur
							cur = it
						}

						is CodeNode.StringConcatenation -> {
							list += cur
							cur = it
						}

						is PlainNode.Plain -> {
							when (it) {
								is PlainNode.Plain -> {
									cur = plainPlain(cur.text + it.text)
								}

								else -> {
									list += cur
									cur = it
								}
							}
						}

						is PlainNode.SingleLine -> {
							list += cur
							cur = it
						}

						is PlainNode.MultiLine -> {
							list += cur
							cur = it
						}

						is PaddingNode.If -> {
							list += cur
							cur = it
						}

						is PaddingNode.IfExp -> {
							list += cur
							cur = it
						}

						is PaddingNode.Block -> {
							list += cur
							cur = it
						}

						is PaddingNode.BlockList -> {
							list += cur
							cur = it
						}
					}
				}
				list += cur
			}
		}
		return singleLineCode(list)
	}

	private fun CodeNode.MultiLine.clean(): CodeNode {
		val list = mutableListOf<CodeNode>()
		when (nodes.size) {
			0 -> {}
			1 -> list += nodes.first().clean()
			else -> {
				var cur = nodes.first().clean()
				nodes.drop(1).map { it.clean() }.forEach {
					when (cur) {
						is CodeNode.None -> {
							list += cur
							cur = it
						}

						is CodeNode.SingleLineList -> {
							list += cur
							cur = it
						}

						is CodeNode.MultiLineList -> {
							list += cur
							cur = it
						}

						is CodeNode.SingleLine -> {
							list += cur
							cur = it
						}

						is CodeNode.MultiLine -> {
							list += cur
							cur = it
						}

						is CodeNode.StringConcatenation -> {
							list += cur
							cur = it
						}

						is PlainNode.Plain -> {
							when (it) {
								is PlainNode.Plain -> {
									cur = plainPlain(cur.text + it.text)
								}

								else -> {
									list += cur
									cur = it
								}
							}
						}

						is PlainNode.SingleLine -> {
							list += cur
							cur = it
						}

						is PlainNode.MultiLine -> {
							list += cur
							cur = it
						}

						is PaddingNode.If -> {
							list += cur
							cur = it
						}

						is PaddingNode.IfExp -> {
							list += cur
							cur = it
						}

						is PaddingNode.Block -> {
							list += cur
							cur = it
						}

						is PaddingNode.BlockList -> {
							list += cur
							cur = it
						}
					}
				}
				list += cur
			}
		}
		return multiLineCode(list)
	}

	private fun CodeNode.StringConcatenation.clean() = stringConcatenationCode(nodes.map { it.clean() })

	private fun PlainNode.Plain.clean() = this

	private fun PlainNode.SingleLine.clean() = singleLinePlain(nodes.joinToString("") { it.text })

	private fun PlainNode.MultiLine.clean() = this

	private fun PaddingNode.If.clean() = ifPadding(condition.clean(), content.clean(), elseContent.clean())

	private fun PaddingNode.IfExp.clean() = ifExpPadding(
		condition.clean(),
		content.first.clean() to content.second,
		elseContent.first.clean() to elseContent.second
	)

	private fun PaddingNode.Block.clean() = blockPadding(nodes.map { it.clean() })

	private fun PaddingNode.BlockList.clean() = blockListPadding(nodes.map { it.clean() })
}