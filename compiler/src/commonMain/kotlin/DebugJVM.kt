import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.compilerRunner.toArgumentStrings

fun main() {
	val arguments = K2JVMCompilerArguments().apply {
		freeArgs += "../kotlin/src"
		destination = "../out-jvm"
		kotlinHome = "../home-jvm"
		noReflect = true
	}
	val compiler = K2JVMCompiler()
	val exitCode = compiler.exec(System.err, *arguments.toArgumentStrings().toTypedArray())
	println("exit, code: $exitCode")
}