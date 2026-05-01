import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.bundling.Zip

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

val intellijIdeaHome = System.getenv("KKLANG_IDEA_HOME") ?: "/Applications/IntelliJ IDEA.app"
val intellijIdeaLib = file("$intellijIdeaHome/Contents/lib")
val intellijIdeaConfigDir =
    System.getenv("KKLANG_IDEA_CONFIG_DIR")
        ?: "${System.getProperty("user.home")}/Library/Application Support/JetBrains/IntelliJIdea2026.1"
val intellijIdeaPluginsDir = file("$intellijIdeaConfigDir/plugins")

dependencies {
    implementation(project(":compiler:core"))
    implementation(project(":tooling:highlighting"))
    compileOnly(fileTree(intellijIdeaLib) { include("*.jar") })
    testImplementation(fileTree(intellijIdeaLib) { include("*.jar") })
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

val buildPluginZip by tasks.registering(Zip::class) {
    group = "build"
    description = "Builds an IntelliJ IDEA plugin zip installable from disk."
    archiveBaseName.set("kklang-idea-plugin")
    archiveVersion.set(project.version.toString())
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))

    val pluginRoot = "kklang-idea-plugin"
    from(tasks.jar) {
        into("$pluginRoot/lib")
    }
    from(configurations.runtimeClasspath) {
        into("$pluginRoot/lib")
    }
}

tasks.assemble {
    dependsOn(buildPluginZip)
}

val uninstallLocalIdeaPlugin by tasks.registering(Delete::class) {
    group = "ide"
    description = "Removes the locally installed kklang IntelliJ IDEA plugin."
    delete(intellijIdeaPluginsDir.resolve("kklang-idea-plugin"))
}

val installLocalIdeaPlugin by tasks.registering(Copy::class) {
    group = "ide"
    description = "Installs the kklang plugin into the local IntelliJ IDEA plugins directory for testing."
    dependsOn(buildPluginZip)
    dependsOn(uninstallLocalIdeaPlugin)
    from(buildPluginZip.map { zipTree(it.archiveFile.get().asFile) })
    into(intellijIdeaPluginsDir)
    doFirst {
        intellijIdeaPluginsDir.mkdirs()
    }
    doLast {
        logger.lifecycle("Installed kklang IDEA plugin to ${intellijIdeaPluginsDir.resolve("kklang-idea-plugin")}")
        logger.lifecycle("Restart IntelliJ IDEA before testing updated .kk highlighting.")
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
