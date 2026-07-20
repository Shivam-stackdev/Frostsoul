@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        google()
        mavenCentral {
            mavenContent {
                releasesOnly()
            }
        }
        exclusiveContent {
            forRepository {
                maven {
                    name = "JitPack"
                    setUrl("https://jitpack.io")
                }
            }
            filter {
                includeGroup("com.github.therealbush")
                includeGroup("com.github.TeamNewPipe")
            }
        }
    }
}

// F-Droid doesn't support foojay-resolver plugin
// plugins {
//     id("org.gradle.toolchains.foojay-resolver-convention") version("1.0.0")
// }

rootProject.name = "Frostsoul"
include(":app")
include(":core")
include(":lyrics:kugou")
include(":lyrics:lrclib")
include(":lyrics:simpmusic")
include(":lyrics:paxsenix")
include(":lyrics:betterlyrics")
include(":lyrics:unison")
include(":lyrics:youlyplus")
include(":lastfm")
include(":canvas")
include(":shazamkit")
include(":spotifycore")
include(":moriextractor")
include(":morideobfuscator")
