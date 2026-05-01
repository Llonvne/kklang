import kotlinx.kover.gradle.plugin.dsl.CoverageUnit

plugins {
    kotlin("jvm")
    application
    id("org.jetbrains.kotlinx.kover")
}

group = "cn.llonvne"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    implementation(project(":compiler:core"))
    implementation(project(":tooling:highlighting"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("cn.llonvne.kklang.lsp.KkLspMain")
}

tasks.test {
    useJUnitPlatform()
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
