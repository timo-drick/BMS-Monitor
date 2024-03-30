// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version libs.versions.agp apply false
    id("org.jetbrains.kotlin.android") version libs.versions.kotlin apply false
    id("com.google.devtools.ksp") version libs.versions.ksp apply false
    id("com.github.ben-manes.versions") version libs.versions.benManesPlugin
}

fun isNonStable(version: String): Boolean {
    val unStableKeyword = listOf("alpha", "beta", "rc", "cr", "m", "preview", "dev").any { version.contains(it, ignoreCase = true) }
    val regex = "^[0-9,.v-]+(-r)?$".toRegex()
    val isStable = unStableKeyword.not() || regex.matches(version)
    return isStable.not()
}

tasks.named("dependencyUpdates", com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask::class.java).configure {
    rejectVersionIf {
        //isNonStable(candidate.version)
        (isNonStable(candidate.version) && isNonStable(currentVersion).not())
    }
}
