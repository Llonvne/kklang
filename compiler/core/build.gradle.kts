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
    dependsOn(tasks.named("koverVerify"))
}
