# KFC(Kotlin For CLR)

<div style="text-align: center;">
  <img src="img/KFC.png" style="width: 512" alt="KFC" />
</div>

这是一个 Kotlin/CLR 编译器, 旨在将 Kotlin 代码编译为 .NET CIL, 使得 Kotlin 开发者可以在 .NET 平台上使用 Kotlin 语言进行开发

[更多介绍](https://juejin.cn/post/7512779184029679666)<br>
[深圳 KUG 演讲](https://www.bilibili.com/video/BV1EF3RzwE4j)<br>
[交流群](https://qm.qq.com/q/ed5aIJqOrK)

## 项目结构
- `compiler` 编译器核心, 使用 Kotlin/JVM 编写, 依赖官方的 kotlin-compiler-embeddable
  - `src/commonMain/kotlin` 编译器核心代码
    - `Library.kt` Kotlin/CLR 标准库编译入口
    - `MainCLR.kt` Kotlin/CLR 编译入口
    - `MainJVM.kt` 官方 Kotlin/JVM 编译入口
- `csharp` C# 部分
  - `AssemblyResolver` 使用 C# 基于 Roslyn API 实现的程序集解析器, 用于解析 .dll 文件并传递给 Kotlin/CLR 编译器
  - `kotlin-stdlib` Kotlin 标准库的 C# 实现, 用于提供 Kotlin 标准库的支持
  - `KotlinCLR` 一个常规的 C# 项目, 用于测试和演示
  - `KotlinCLR/gen` 包含从 `kotlin` 编译而来的 C# 代码
- `kotlin` 一个使用 Kotlin/CLR 的 Kotlin 项目, 用于测试和演示
- `home` kotlin-home
  - `lib` 标准库
  - `resolver` 程序集解析器
- `stdlib` Kotlin/CLR 编写的标准库, 暂未使用, 作为未来的标准库实现

## 使用

**如果切换版本, 请清理缓存目录以避免编译错误(~/.kfc/cache)**

1. 前往 [Actions(非稳定版)](https://github.com/Nyayurin/KotlinForCLR/actions) / [Release(稳定版)](https://github.com/Nyayurin/KotlinForCLR/releases) 下载编译器成品(zip/tar)和 `Kotlin-home`
2. 解压编译器, 进入 bin 目录, 在 bin 目录下打开终端
3. 运行 compiler 脚本并传递参数
4. 参数为需要编译的 kotlin 源码/目录
5. `-kotlin-home` 选项提供解压后的 `kotlin-home` 路径
6. `-d` 选项提供输出路径
7. `-dotnet-home` 选项提供 dotnet 根目录
8. `-dotnet-version` 选项提供 dotnet 版本(x.y.z)
9. 完整的命令应该类似 `compiler.bat kotlin/src -d out -kotlin-home home -dotnet-home "C:/Program Files/dotnet" -dotnet-version 9.0.5`
10. 更多选项请参考 `compiler.clr.CLRCompilerArguments` 和 `org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments`

## 输出文件夹
1. `FIR@Raw.txt` Frontend IR(前端中间表示形式) 未经过处理的原始树
2. `FIR@Resolved.txt` FIR 经过处理后的树
3. `KIR@Raw.txt` Kotlin IR 未经过降级的原始树
4. `KIR@Lowered.txt` KIR 经过降级后的树
5. `BIR@Raw.xml` Backend IR 未经过清理的树
6. `BIR@Cleaned.xml` BIR 经过清理的树
7. `xxx.cs` 编译产物 C# 源文件

## 开发
1. 使用 Intellij IDEA 打开本项目, 使用 Rider / Visual Studio 打开 `csharp` 子目录
2. 使用 Rider / Visual Studio 构建 `AssemblyResolver` 项目, 并将生成的所有文件及文件夹放入 `home/resolver` 目录下
3. 使用 Rider / Visual Studio 构建 `kotlin-stdlib` 项目, 并将生成的 `kotlin-stdlib.dll` 放入 `home/lib` 目录下
4. 打开 `compiler/src/commonMain/kotlin/Debug.kt` 文件, 修改 dotnetHome 和 dotnetVersion 为符合你本地环境
5. 使用 Intellij IDEA 运行 Gradle 任务 `:compiler:jvmRun -DmainClass=DebugKt` 即可将 `kotlin` 目录内的源码编译至 `csharp/KotlinCLR/gen` 目录下
6. 使用 Rider / Visual Studio 运行 `KotlinCLR` 项目

## [样例](./Example.md)

## 原理
1. 使用官方 kotlin-compiler-embeddable, 复用官方编译器组件进行 Configuration, Frontend, Fir2Ir 编译
2. 编写 Kotlin/CLR 后端编译器将 Kotlin IR 降级并生成 C# 源码
3. 使用 C# 编写的 AssemblyResolver 解析 .dll 文件, 并将其传递给 Kotlin/CLR 编译器
4. 使用 C# 编写的标准库和 AssemblyResolver 为 Kotlin/CLR 提供标准库
5. 通过 ClrSymbolProvider 提供 C# 的符号解析, 使得 Kotlin/CLR 可以使用 .NET 的类型和方法

## 为什么编译到 C# 而不是直接编译到 CIL?
短期内编译到 C# 可以更快见效, 性价比更高, 未来会直接生成 CIL

## 为什么使用 C# 编写标准库而不是 Kotlin?
编译器还没搓完, 部分语法还没支持, 编译 Kotlin 标准库会报错(x
