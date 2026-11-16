plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:localization"))
    implementation(project(":core:parser-api"))
    implementation(libs.jsoup)
    implementation(libs.json)
    implementation(libs.re2j)

    testImplementation(libs.junit)
}
