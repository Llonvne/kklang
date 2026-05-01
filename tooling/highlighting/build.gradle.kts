import kotlinx.kover.gradle.plugin.dsl.CoverageUnit

plugins {
    kotlin("jvm")
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
    testImplementation(kotlin("test"))
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
