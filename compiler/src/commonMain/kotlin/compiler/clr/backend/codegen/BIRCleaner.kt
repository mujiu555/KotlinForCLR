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

fun BackendIR.clean() = Cleaner().run { clean() }

private class Cleaner {
	fun BackendIR.clean(): BackendIR = when (this) {
		is BackendIR.None -> clean()
		is BackendIR.SingleLineList -> clean()
		is BackendIR.MultiLineList -> clean()
		is BackendIR.SingleLine -> clean()
		is BackendIR.MultiLine -> clean()
		is BackendIR.StringConcatenation -> clean()
		is PlainIR.Plain -> clean()
		is PlainIR.SingleLine -> clean()
		is PlainIR.MultiLine -> clean()
		is PaddingIR.If -> clean()
		is PaddingIR.IfExp -> clean()
		is PaddingIR.Block -> clean()
		is PaddingIR.BlockList -> clean()
	}

	private fun BackendIR.None.clean() = this
	private fun BackendIR.SingleLineList.clean(): BackendIR {
		val list = mutableListOf<BackendIR>()
		when (nodes.size) {
			0 -> {}
			1 -> list += nodes.first().clean()
			else -> {
				var cur = nodes.first().clean()
				nodes.drop(1).map { it.clean() }.forEach {
					when (cur) {
						is BackendIR.None -> {
							list += cur
							cur = it
						}

						is BackendIR.SingleLineList -> {
							list += cur
							cur = it
						}

						is BackendIR.MultiLineList -> {
							list += cur
							cur = it
						}

						is BackendIR.SingleLine -> {
							list += cur
							cur = it
						}

						is BackendIR.MultiLine -> {
							list += cur
							cur = it
						}

						is BackendIR.StringConcatenation -> {
							list += cur
							cur = it
						}

						is PlainIR.Plain -> {
							when (it) {
								is PlainIR.Plain -> {
									cur = plainIR(cur.text + it.text)
								}

								else -> {
									list += cur
									cur = it
								}
							}
						}

						is PlainIR.SingleLine -> {
							list += cur
							cur = it
						}

						is PlainIR.MultiLine -> {
							list += cur
							cur = it
						}

						is PaddingIR.If -> {
							list += cur
							cur = it
						}

						is PaddingIR.IfExp -> {
							list += cur
							cur = it
						}

						is PaddingIR.Block -> {
							list += cur
							cur = it
						}

						is PaddingIR.BlockList -> {
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
		return singleLineListIR(list)
	}

	private fun BackendIR.MultiLineList.clean(): BackendIR {
		val list = mutableListOf<BackendIR>()
		when (nodes.size) {
			0 -> {}
			1 -> list += nodes.first().clean()
			else -> {
				var cur = nodes.first().clean()
				nodes.drop(1).map { it.clean() }.forEach {
					when (cur) {
						is BackendIR.None -> {
							list += cur
							cur = it
						}

						is BackendIR.SingleLineList -> {
							list += cur
							cur = it
						}

						is BackendIR.MultiLineList -> {
							list += cur
							cur = it
						}

						is BackendIR.SingleLine -> {
							list += cur
							cur = it
						}

						is BackendIR.MultiLine -> {
							list += cur
							cur = it
						}

						is BackendIR.StringConcatenation -> {
							list += cur
							cur = it
						}

						is PlainIR.Plain -> {
							when (it) {
								is PlainIR.Plain -> {
									cur = plainIR(cur.text + it.text)
								}

								else -> {
									list += cur
									cur = it
								}
							}
						}

						is PlainIR.SingleLine -> {
							list += cur
							cur = it
						}

						is PlainIR.MultiLine -> {
							list += cur
							cur = it
						}

						is PaddingIR.If -> {
							list += cur
							cur = it
						}

						is PaddingIR.IfExp -> {
							list += cur
							cur = it
						}

						is PaddingIR.Block -> {
							list += cur
							cur = it
						}

						is PaddingIR.BlockList -> {
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
		return multiLineListIR(list)
	}

	private fun BackendIR.SingleLine.clean(): BackendIR {
		val list = mutableListOf<BackendIR>()
		when (nodes.size) {
			0 -> {}
			1 -> list += nodes.first().clean()
			else -> {
				var cur = nodes.first().clean()
				nodes.drop(1).map { it.clean() }.forEach {
					when (cur) {
						is BackendIR.None -> {
							list += cur
							cur = it
						}

						is BackendIR.SingleLineList -> {
							list += cur
							cur = it
						}

						is BackendIR.MultiLineList -> {
							list += cur
							cur = it
						}

						is BackendIR.SingleLine -> {
							list += cur
							cur = it
						}

						is BackendIR.MultiLine -> {
							list += cur
							cur = it
						}

						is BackendIR.StringConcatenation -> {
							list += cur
							cur = it
						}

						is PlainIR.Plain -> {
							when (it) {
								is PlainIR.Plain -> {
									cur = plainIR(cur.text + it.text)
								}

								else -> {
									list += cur
									cur = it
								}
							}
						}

						is PlainIR.SingleLine -> {
							list += cur
							cur = it
						}

						is PlainIR.MultiLine -> {
							list += cur
							cur = it
						}

						is PaddingIR.If -> {
							list += cur
							cur = it
						}

						is PaddingIR.IfExp -> {
							list += cur
							cur = it
						}

						is PaddingIR.Block -> {
							list += cur
							cur = it
						}

						is PaddingIR.BlockList -> {
							list += cur
							cur = it
						}
					}
				}
				list += cur
			}
		}
		return singleLineIR(list)
	}

	private fun BackendIR.MultiLine.clean(): BackendIR {
		val list = mutableListOf<BackendIR>()
		when (nodes.size) {
			0 -> {}
			1 -> list += nodes.first().clean()
			else -> {
				var cur = nodes.first().clean()
				nodes.drop(1).map { it.clean() }.forEach {
					when (cur) {
						is BackendIR.None -> {
							list += cur
							cur = it
						}

						is BackendIR.SingleLineList -> {
							list += cur
							cur = it
						}

						is BackendIR.MultiLineList -> {
							list += cur
							cur = it
						}

						is BackendIR.SingleLine -> {
							list += cur
							cur = it
						}

						is BackendIR.MultiLine -> {
							list += cur
							cur = it
						}

						is BackendIR.StringConcatenation -> {
							list += cur
							cur = it
						}

						is PlainIR.Plain -> {
							when (it) {
								is PlainIR.Plain -> {
									cur = plainIR(cur.text + it.text)
								}

								else -> {
									list += cur
									cur = it
								}
							}
						}

						is PlainIR.SingleLine -> {
							list += cur
							cur = it
						}

						is PlainIR.MultiLine -> {
							list += cur
							cur = it
						}

						is PaddingIR.If -> {
							list += cur
							cur = it
						}

						is PaddingIR.IfExp -> {
							list += cur
							cur = it
						}

						is PaddingIR.Block -> {
							list += cur
							cur = it
						}

						is PaddingIR.BlockList -> {
							list += cur
							cur = it
						}
					}
				}
				list += cur
			}
		}
		return multiLineIR(list)
	}

	private fun BackendIR.StringConcatenation.clean() = stringConcatenationIR(nodes.map { it.clean() })

	private fun PlainIR.Plain.clean() = this

	private fun PlainIR.SingleLine.clean() = singleLinePlain(nodes.joinToString("") { it.text })

	private fun PlainIR.MultiLine.clean() = this

	private fun PaddingIR.If.clean() = ifPadding(condition.clean(), content.clean(), elseContent.clean())

	private fun PaddingIR.IfExp.clean() = ifExpPadding(
		condition.clean(),
		content.first.clean() to content.second,
		elseContent.first.clean() to elseContent.second
	)

	private fun PaddingIR.Block.clean() = blockPadding(nodes.map { it.clean() })

	private fun PaddingIR.BlockList.clean() = blockListPadding(nodes.map { it.clean() })
}