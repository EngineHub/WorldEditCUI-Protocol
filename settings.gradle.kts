rootProject.name = "WorldEditCUI-Protocol"

pluginManagement {
    repositories {
        // mirrors:
        // - https://maven.architectury.dev/
        // - https://maven.fabricmc.net/
        maven(url = "https://maven.enginehub.org/repo/") {
            name = "enginehub"
        }
        gradlePluginPortal()
    }
}

sequenceOf(
    "common",
    "fabric",
    "neoforge",
).forEach {
    include("worldeditcui-protocol-$it")
}
