plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:localization"))
    implementation(project(":core:parser-api"))
    implementation(project(":core:parser-engine"))
    implementation(libs.jsoup)
    implementation(libs.json)

    testImplementation(libs.junit)
}
