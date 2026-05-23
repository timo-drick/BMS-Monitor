import nl.littlerobots.vcu.plugin.resolver.VersionSelectors
import nl.littlerobots.vcu.plugin.versionCatalogUpdate

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.versionCatalogUpdate)
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

versionCatalogUpdate {
    sortByKey.set(false)
    versionSelector(VersionSelectors.PREFER_STABLE)
    keep {
        versions.add("android-compileSdk")
        versions.add("android-minSdk")
    }
}