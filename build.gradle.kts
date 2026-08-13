plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.minekot.toolchain)
}

group = project.findProperty("group")?.toString() ?: missingProperty("group")
version = project.findProperty("version")?.toString() ?: missingProperty("version")
val projectJavaVersion = 21

@Suppress("GradleDslConventions")
val cleanFinalArtifacts = tasks.register<Delete>("cleanFinalArtifacts") {
    description = "Cleans the final directory with a backup of the previous state"
    val finalFile = layout.projectDirectory.dir("final").asFile
    val backupFile = layout.projectDirectory.dir("final_bak").asFile

    doFirst {
        if (finalFile.listFiles()?.isEmpty() != false) return@doFirst

        backupFile.deleteRecursively()
        finalFile.copyRecursively(target = backupFile, overwrite = true)
    }
    delete(finalFile)
}

repositories {
    mavenCentral()
}

minekotToolchain {
    toolchainVersion = libs.versions.minekot.toolchain
    build {
        javaVersion = projectJavaVersion
        allWarningsAsErrors = true
    }
    publishing {
        enabled = false
    }
    shadow {
        enabled = true
        classifier = "all"
        mergeServiceFiles = true
    }
    lint {
        enabled = true
        configFile = rootProject.layout.projectDirectory.file("config/detekt/minekot.yml")
    }
    ciCd {
        enabled = true
    }
}

tasks {
    withType<Test>().configureEach {
        jvmArgs("-Xshare:off")
        systemProperty("minekot.rootDir", rootProject.projectDir.absolutePath)
    }

    withType<Jar>().configureEach {
        if (name == "jar") {
            dependsOn("shadowJar")
        }
        if (name == "shadowJar") {
            mustRunAfter(":cleanFinalArtifacts")
            (this as? com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar)?.destinationDirectory =
            rootProject.layout.projectDirectory.dir("final")
            archiveFileName = "${project.name}-${project.version}.jar"
        }
        if (name == "sourcesJar") {
            mustRunAfter(":cleanFinalArtifacts")
            destinationDirectory = rootProject.layout.projectDirectory.dir("final")
            archiveFileName = "${project.name}-${project.version}-sources.jar"
        }
    }

    withType<Task>().configureEach {
        if (name == "build") {
            dependsOn(":cleanFinalArtifacts")
        }
    }
}

private fun missingProperty(name: String): Nothing =
    throw IllegalStateException("Property '$name' is missing. Please define it in gradle.properties.")
