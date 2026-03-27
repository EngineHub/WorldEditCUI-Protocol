plugins {
    alias(libs.plugins.architecturyPlugin)
    alias(libs.plugins.loom)
    alias(libs.plugins.shadow)
}

architectury {
    fabric()
}

indraSpotlessLicenser {
    licenseHeaderFile(rootProject.file("HEADER"))
}

configurations {
    val common = dependencyScope("common")
    compileClasspath { extendsFrom(common.get()) }
    runtimeClasspath { extendsFrom(common.get()) }
    "developmentFabric" { extendsFrom(common.get()) }
}

val shadowBundle = configurations.dependencyScope("shadowBundle")
val shadowBundleClasspath = configurations.resolvable("shadowBundleClasspath") {
    extendsFrom(shadowBundle.get())
}

dependencies {
    "common"(project(":worldeditcui-protocol-common")) { isTransitive = false }
    "shadowBundle"(project(":worldeditcui-protocol-common", configuration = "transformProductionFabric"))
    implementation(libs.fabric.loader)
    implementation(platform(libs.fabric.api.bom))
    implementation(libs.fabric.api.networking)
}

tasks {
    shadowJar {
        configurations = listOf(shadowBundleClasspath.get())
        archiveClassifier = ""
    }

    jar {
        archiveClassifier = "dev"
    }

    assemble {
        dependsOn(shadowJar)
    }
}
