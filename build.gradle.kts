import net.fabricmc.loom.api.LoomGradleExtensionAPI
import net.kyori.indra.licenser.spotless.IndraSpotlessLicenserExtension
import org.jfrog.gradle.plugin.artifactory.dsl.ArtifactoryPluginConvention
import org.jfrog.gradle.plugin.artifactory.task.ArtifactoryTask

plugins {
    base
    alias(libs.plugins.loom) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.indra.spotlessLicenser) apply false
    id("com.jfrog.artifactory") version "5.2.5"
}

allprojects {
    group = "org.enginehub.worldeditcui-protocol"

    repositories {
        // mirrors:
        // - https://maven.terraformersmc.com/releases/
        // - https://maven.minecraftforge.net/
        // - https://maven.neoforged.net/
        // - https://maven.parchmentmc.org/
        // - https://repo.viaversion.com/
        maven(url = "https://maven.enginehub.org/repo/") {
            name = "enginehub"
        }
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "net.kyori.indra.licenser.spotless")
    apply(plugin = "maven-publish")
    apply(plugin = "com.jfrog.artifactory")

    val targetJavaVersion: String by project
    val targetVersion = targetJavaVersion.toInt()
    extensions.configure(JavaPluginExtension::class) {
        sourceCompatibility = JavaVersion.toVersion(targetVersion)
        targetCompatibility = sourceCompatibility
        if (JavaVersion.current() < JavaVersion.toVersion(targetVersion)) {
            toolchain.languageVersion = JavaLanguageVersion.of(targetVersion)
        }
        withSourcesJar()
    }

    extensions.configure<BasePluginExtension> {
        archivesName.set("${project.name}-mc${rootProject.libs.versions.minecraft.get()}")
    }

    tasks.withType(JavaCompile::class).configureEach {
        options.release = targetVersion
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing"))
    }

    tasks.named("processResources", ProcessResources::class).configure {
        inputs.property("version", project.version)

        sequenceOf("fabric.mod.json", "META-INF/neoforge.mods.toml").forEach {
            filesMatching(it) {
                expand("version" to project.version)
            }
        }
    }

    extensions.configure(IndraSpotlessLicenserExtension::class) {
        licenseHeaderFile(rootProject.file("HEADER"))
    }

    plugins.withId("dev.architectury.loom") {
        val loom = extensions.getByType(LoomGradleExtensionAPI::class)
        loom.run {
            decompilerOptions.named("vineflower") {
                options.put("win", "0")
            }
            silentMojangMappingsLicense()
        }

        // Ugly hack for easy genSourcening
        afterEvaluate {
            tasks.matching { it.name == "genSources" }.configureEach {
                setDependsOn(setOf("genSourcesWithVineflower"))
            }

            tasks.named<ArtifactoryTask>("artifactoryPublish") {
                publications("maven")
            }
        }

        dependencies {
            "minecraft"(libs.minecraft)
            "mappings"(loom.layered {
                officialMojangMappings {
                    nameSyntheticMembers = false
                }
                parchment(variantOf(libs.parchment) { artifactType("zip") })
            })
            "vineflowerDecompilerClasspath"(libs.vineflower)
        }

        configurations.named("modLocalRuntime") {
            shouldResolveConsistentlyWith(configurations.getByName("modImplementation"))
        }
    }

    extensions.configure(PublishingExtension::class) {
        publications {
            register("maven", MavenPublication::class) {
                artifactId = the<BasePluginExtension>().archivesName.get()
                from(components.getByName("java"))
            }
        }
    }
}

val ARTIFACTORY_CONTEXT_URL = "artifactory_contextUrl"
val ARTIFACTORY_USER = "artifactory_user"
val ARTIFACTORY_PASSWORD = "artifactory_password"

if (!project.hasProperty(ARTIFACTORY_CONTEXT_URL)) ext[ARTIFACTORY_CONTEXT_URL] = "http://localhost"
if (!project.hasProperty(ARTIFACTORY_USER)) ext[ARTIFACTORY_USER] = "guest"
if (!project.hasProperty(ARTIFACTORY_PASSWORD)) ext[ARTIFACTORY_PASSWORD] = ""

configure<ArtifactoryPluginConvention> {
    setContextUrl("${project.property(ARTIFACTORY_CONTEXT_URL)}")
    clientConfig.publisher.run {
        repoKey = when {
            "${project.version}".contains("SNAPSHOT") -> "libs-snapshot-local"
            else -> "libs-release-local"
        }
        username = "${project.property(ARTIFACTORY_USER)}"
        password = "${project.property(ARTIFACTORY_PASSWORD)}"
        isMaven = true
        isIvy = false
    }
}

tasks.named<ArtifactoryTask>("artifactoryPublish") {
    isSkip = true
}
