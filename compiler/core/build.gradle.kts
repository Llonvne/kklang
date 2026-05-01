import kotlinx.kover.gradle.plugin.dsl.CoverageUnit

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.kotlinx.kover")
}

group = "cn.llonvne"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    val osName = System.getProperty("os.name")
    val osArch = System.getProperty("os.arch")
    when {
        osName == "Mac OS X" && osArch == "aarch64" -> macosArm64("host")
        osName == "Linux" && osArch == "aarch64" -> linuxArm64("host")
        osName == "Linux" && (osArch == "amd64" || osArch == "x86_64") -> linuxX64("host")
        else -> error("Unsupported Kotlin/Native host: $osName $osArch")
    }

    jvmToolchain(25)

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

kover {
    reports {
        verify {
            rule("100 percent line coverage") {
                minBound(100, CoverageUnit.LINE)
            }
            rule("100 percent branch coverage") {
                minBound(100, CoverageUnit.BRANCH)
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.named("allTests"))
    dependsOn(tasks.named("koverVerify"))
}
