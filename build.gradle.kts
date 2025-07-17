import java.io.OutputStream
import de.undercouch.gradle.tasks.download.Download
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {

    kotlin("jvm") version "2.2.0"
    id("com.gradleup.shadow") version "8.3.0+"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.18"
    id("de.undercouch.download") version "5.4.0"
}

val relocate = (findProperty("relocate") as? String)?.toBoolean() ?: true
println("relocate = $relocate")

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://papermc.io/repo/repository/maven-public/")
    maven("https://jitpack.io")
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("com.github.noonmaru:tap:3.2.7")
    implementation("com.github.noonmaru:kommand:0.6.4")
    paperweight.paperDevBundle("1.21.7-R0.1-SNAPSHOT")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    jvmToolchain(21) // Kotlin DSL 내 Java toolchain 지정
    compilerOptions {
        freeCompilerArgs.addAll("-Xjvm-default=all", "-opt-in=kotlin.RequiresOptIn")
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

tasks.processResources {
    filesMatching("**/*.yml") {
        expand(project.properties)
    }
}

tasks.register<Jar>("sourcesJar") {
    archiveClassifier.set("sources")
    from(sourceSets["main"].allSource)
}

val shadowJar = tasks.named<ShadowJar>("shadowJar") {
    archiveBaseName.set(project.property("pluginName").toString())
    archiveVersion.set("")
    archiveClassifier.set("")

    if (relocate) {
        relocate("com.github.noonmaru.kommand", "${rootProject.group}.${rootProject.name}.kommand")
        relocate("com.github.noonmaru.tap", "${rootProject.group}.${rootProject.name}.tap")
    }
}

tasks.register<Copy>("paper") {
    from(shadowJar)
    into(file(".paper/plugins").let { dir ->
        val target = dir.resolve(shadowJar.get().archiveFileName.get())
        if (target.exists()) dir.resolve("update") else dir
    })
}

tasks.register("setupWorkspace") {
    group = "setup"
    description = "Generate Spigot remapped jars via BuildTools"

    doLast {
        val versions = arrayOf("1.18.2")
        val buildtoolsDir = file(".buildtools")
        val jarFile = buildtoolsDir.resolve("BuildTools.jar")

        val repoDir = System.getProperty("user.home")
            .let { "$it/.m2/repository/org/spigotmc/spigot/" }
            .let { file(it).listFiles()?.toList() ?: emptyList() }
        val missing = versions.filterNot { ver -> repoDir.any { it.name.startsWith(ver) } }
        if (missing.isEmpty()) {
            println("Spigot $versions already present")
            return@doLast
        }

        tasks.register<Download>("downloadBuildTools").get().apply {
            src("https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar")
            dest(jarFile)
            download()
        }

        missing.forEach { v ->
            println("Building spigot-$v...")
            javaexec {
                workingDir = buildtoolsDir
                mainClass.set("-jar")
                args = listOf(jarFile.name, "--rev", v, "--remapped")
                standardOutput = OutputStream.nullOutputStream()
                errorOutput = OutputStream.nullOutputStream()
            }
        }
        buildtoolsDir.deleteRecursively()
    }
}
