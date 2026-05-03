import org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

plugins {
    kotlin("multiplatform")
}

group = "cn.llonvne"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val runtimeCIncludeDir = layout.projectDirectory.dir("../c/include")
val runtimeCSourceFile = layout.projectDirectory.file("../c/src/kklang_runtime.c")
val runtimeCTestFile = layout.projectDirectory.file("../c/test/kklang_runtime_test.c")
val runtimeCTestAllocHeader = layout.projectDirectory.file("../c/test/kklang_runtime_test_alloc.h")
val runtimeCBuildDir = layout.buildDirectory.dir("c-runtime")
val runtimeCObjectFile = runtimeCBuildDir.map { it.file("kklang_runtime.o") }
val runtimeCCoverageObjectFile = runtimeCBuildDir.map { it.file("kklang_runtime.coverage.o") }
val runtimeCLibraryFile = runtimeCBuildDir.map { it.file("libkklang_runtime.a") }
val runtimeCTestExecutable = runtimeCBuildDir.map { it.file("kklang_runtime_test") }
val runtimeCTestMarker = runtimeCBuildDir.map { it.file("kklang_runtime_test.ok") }
val runtimeCTestProfileRaw = runtimeCBuildDir.map { it.file("kklang_runtime_test.profraw") }
val runtimeCTestProfileData = runtimeCBuildDir.map { it.file("kklang_runtime_test.profdata") }
val runtimeHostDebugExecutable = layout.buildDirectory.file("bin/host/debugTest/test.kexe")
val runtimeSingleFileDebugExecutable = layout.buildDirectory.file("bin/host/kkrunDebugExecutable/kkrun.kexe")

val compileKklangRuntimeC by tasks.registering(Exec::class) {
    inputs.file(runtimeCSourceFile)
    inputs.dir(runtimeCIncludeDir)
    outputs.file(runtimeCObjectFile)

    doFirst {
        runtimeCBuildDir.get().asFile.mkdirs()
    }

    commandLine(
        "clang",
        "-std=c17",
        "-Wall",
        "-Wextra",
        "-Werror",
        "-pedantic",
        "-O0",
        "-g",
        "-I",
        runtimeCIncludeDir.asFile.absolutePath,
        "-c",
        runtimeCSourceFile.asFile.absolutePath,
        "-o",
        runtimeCObjectFile.get().asFile.absolutePath,
    )
}

val archiveKklangRuntimeC by tasks.registering(Exec::class) {
    dependsOn(compileKklangRuntimeC)
    inputs.file(runtimeCObjectFile)
    outputs.file(runtimeCLibraryFile)

    commandLine(
        "ar",
        "rcs",
        runtimeCLibraryFile.get().asFile.absolutePath,
        runtimeCObjectFile.get().asFile.absolutePath,
    )
}

val compileKklangRuntimeCCoverage by tasks.registering(Exec::class) {
    inputs.file(runtimeCSourceFile)
    inputs.file(runtimeCTestAllocHeader)
    inputs.dir(runtimeCIncludeDir)
    outputs.file(runtimeCCoverageObjectFile)

    doFirst {
        runtimeCBuildDir.get().asFile.mkdirs()
    }

    commandLine(
        "clang",
        "-std=c17",
        "-Wall",
        "-Wextra",
        "-Werror",
        "-pedantic",
        "-O0",
        "-g",
        "-fprofile-instr-generate",
        "-fcoverage-mapping",
        "-include",
        runtimeCTestAllocHeader.asFile.absolutePath,
        "-Dcalloc=kk_test_calloc",
        "-Dmalloc=kk_test_malloc",
        "-I",
        runtimeCIncludeDir.asFile.absolutePath,
        "-c",
        runtimeCSourceFile.asFile.absolutePath,
        "-o",
        runtimeCCoverageObjectFile.get().asFile.absolutePath,
    )
}

val compileKklangRuntimeCTest by tasks.registering(Exec::class) {
    dependsOn(compileKklangRuntimeCCoverage)
    inputs.file(runtimeCTestFile)
    inputs.file(runtimeCTestAllocHeader)
    inputs.dir(runtimeCIncludeDir)
    inputs.file(runtimeCCoverageObjectFile)
    outputs.file(runtimeCTestExecutable)

    commandLine(
        "clang",
        "-std=c17",
        "-Wall",
        "-Wextra",
        "-Werror",
        "-pedantic",
        "-O0",
        "-g",
        "-fprofile-instr-generate",
        "-fcoverage-mapping",
        "-I",
        runtimeCIncludeDir.asFile.absolutePath,
        runtimeCTestFile.asFile.absolutePath,
        runtimeCCoverageObjectFile.get().asFile.absolutePath,
        "-o",
        runtimeCTestExecutable.get().asFile.absolutePath,
    )
}

val runKklangRuntimeCTest by tasks.registering(Exec::class) {
    dependsOn(compileKklangRuntimeCTest)
    inputs.file(runtimeCTestExecutable)
    outputs.file(runtimeCTestMarker)

    environment("LLVM_PROFILE_FILE", runtimeCTestProfileRaw.get().asFile.absolutePath)
    commandLine(runtimeCTestExecutable.get().asFile.absolutePath)

    doFirst {
        runtimeCTestProfileRaw.get().asFile.delete()
    }

    doLast {
        runtimeCTestMarker.get().asFile.writeText("ok\n")
    }
}

val mergeKklangRuntimeCCoverage by tasks.registering(Exec::class) {
    dependsOn(runKklangRuntimeCTest)
    inputs.file(runtimeCTestProfileRaw)
    outputs.file(runtimeCTestProfileData)

    commandLine(
        "xcrun",
        "llvm-profdata",
        "merge",
        "-sparse",
        runtimeCTestProfileRaw.get().asFile.absolutePath,
        "-o",
        runtimeCTestProfileData.get().asFile.absolutePath,
    )
}

val verifyKklangRuntimeCCoverage by tasks.registering {
    dependsOn(mergeKklangRuntimeCCoverage)
    inputs.file(runtimeCTestExecutable)
    inputs.file(runtimeCTestProfileData)
    inputs.file(runtimeCSourceFile)

    doLast {
        val report = providers.exec {
            commandLine(
                "xcrun",
                "llvm-cov",
                "report",
                runtimeCTestExecutable.get().asFile.absolutePath,
                "-instr-profile=${runtimeCTestProfileData.get().asFile.absolutePath}",
                "--show-branch-summary",
                runtimeCSourceFile.asFile.absolutePath,
            )
        }.standardOutput.asText.get()
        val totalLine = report.lineSequence().firstOrNull { it.trimStart().startsWith("TOTAL ") }
            ?: error("Missing TOTAL line in llvm-cov report:\n$report")
        val percentages = Regex("""\d+(?:\.\d+)?%""").findAll(totalLine).map { it.value }.toList()
        val lineCoverage = percentages.getOrNull(1)
            ?: error("Missing line coverage in llvm-cov report:\n$report")
        val branchCoverage = percentages.getOrNull(2)
            ?: error("Missing branch coverage in llvm-cov report:\n$report")

        if (lineCoverage != "100.00%" || branchCoverage != "100.00%") {
            error(
                "C runtime coverage must be 100.00% line and branch; " +
                    "got line=$lineCoverage branch=$branchCoverage\n$report",
            )
        }
    }
}

kotlin {
    val osName = System.getProperty("os.name")
    val osArch = System.getProperty("os.arch")
    val nativeTarget = when {
        osName == "Mac OS X" && osArch == "aarch64" -> macosArm64("host")
        osName == "Linux" && osArch == "aarch64" -> linuxArm64("host")
        osName == "Linux" && (osArch == "amd64" || osArch == "x86_64") -> linuxX64("host")
        else -> error("Unsupported Kotlin/Native host: $osName $osArch")
    }

    nativeTarget.apply {
        binaries {
            executable("kkrun", listOf(NativeBuildType.DEBUG)) {
                entryPoint = "cn.llonvne.kklang.runtime.main"
            }
        }
        compilations.getByName("main") {
            cinterops {
                val kklangRuntime by creating {
                    defFile(project.file("src/nativeInterop/cinterop/kklangRuntime.def"))
                    includeDirs(runtimeCIncludeDir.asFile)
                    compilerOpts("-I${runtimeCIncludeDir.asFile.absolutePath}")
                }
            }
        }
        binaries.all {
            linkerOpts("-L${runtimeCBuildDir.get().asFile.absolutePath}", "-lkklang_runtime")
        }
    }

    sourceSets {
        val hostMain by getting {
            dependencies {
                implementation(project(":compiler:core"))
            }
        }
        val hostTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

tasks.withType<KotlinNativeLink>().configureEach {
    dependsOn(archiveKklangRuntimeC)
}

val printRuntimeHostDebugCommand by tasks.registering {
    dependsOn("linkDebugTestHost")
    dependsOn(archiveKklangRuntimeC)

    doLast {
        val executable = runtimeHostDebugExecutable.get().asFile
        if (!executable.isFile) {
            error("Missing Kotlin/Native host test executable at ${executable.absolutePath}")
        }

        println("Kotlin/Native host test executable: ${executable.absolutePath}")
        println("C runtime source: ${runtimeCSourceFile.asFile.absolutePath}")
        println("C runtime debug object: ${runtimeCObjectFile.get().asFile.absolutePath}")
        println("LLDB:")
        println("  lldb ${executable.absolutePath}")
        println("  (lldb) target modules add ${runtimeCObjectFile.get().asFile.absolutePath}")
        println("  (lldb) breakpoint set --file ${runtimeCSourceFile.asFile.name} --name kk_runtime_create")
        println("  (lldb) breakpoint set --file ${runtimeCSourceFile.asFile.name} --name kk_value_int64")
        println("  (lldb) run")
    }
}

val printRuntimeSingleFileDebugCommand by tasks.registering {
    dependsOn("linkKkrunDebugExecutableHost")
    dependsOn(archiveKklangRuntimeC)

    doLast {
        val executable = runtimeSingleFileDebugExecutable.get().asFile
        if (!executable.isFile) {
            error("Missing kkrun debug executable at ${executable.absolutePath}")
        }

        val source = rootProject.file(providers.gradleProperty("kklang.debug.source").orElse("main.kk").get())
        println("kkrun debug executable: ${executable.absolutePath}")
        println("Debug source path: ${source.absolutePath}")
        println("C runtime source: ${runtimeCSourceFile.asFile.absolutePath}")
        println("C runtime debug object: ${runtimeCObjectFile.get().asFile.absolutePath}")
        println("LLDB:")
        println("  lldb -- ${executable.absolutePath} ${source.absolutePath}")
        println("  (lldb) target modules add ${runtimeCObjectFile.get().asFile.absolutePath}")
        println("  (lldb) breakpoint set --file ${runtimeCSourceFile.asFile.name} --name kk_runtime_create")
        println("  (lldb) breakpoint set --file ${runtimeCSourceFile.asFile.name} --name kk_string_new")
        println("  (lldb) breakpoint set --file ${runtimeCSourceFile.asFile.name} --name kk_value_int64")
        println("  (lldb) breakpoint set --file ${runtimeCSourceFile.asFile.name} --name kk_value_string")
        println("  (lldb) breakpoint set --file ${runtimeCSourceFile.asFile.name} --name kk_value_unit")
        println("  (lldb) run")
    }
}

tasks.named("check") {
    dependsOn(runKklangRuntimeCTest)
    dependsOn(verifyKklangRuntimeCCoverage)
    dependsOn("linkKkrunDebugExecutableHost")
}
