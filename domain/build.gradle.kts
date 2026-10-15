plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(libs.arrow.core)
    api(libs.javax.inject)
    implementation(libs.kotlin.coroutines.core)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlin.coroutines.test)
    testImplementation(libs.kotest.assertions)
    testImplementation(libs.kotest.assertions.arrow)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
