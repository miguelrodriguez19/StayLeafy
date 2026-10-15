/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.File
import org.gradle.api.GradleException

private val MAX_VERSION_CODE = 2_100_000_000L
private val versionPropertyKey = Regex("^[A-Z][A-Z0-9_]*$")
private val simpleSemVer = Regex("^(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)$")
private val positiveVersionCode = Regex("^[1-9][0-9]*$")

private data class StayLeafyVersion(
    val name: String,
    val code: Int,
)

private fun readStayLeafyVersion(file: File): StayLeafyVersion {
    require(file.isFile) {
        "Missing ${file.path}; VERSION_NAME and VERSION_CODE must be defined there."
    }

    val values = linkedMapOf<String, String>()
    file.readLines(Charsets.UTF_8).forEachIndexed { index, line ->
        val lineNumber = index + 1
        if (line.isBlank() || line.trimStart().startsWith("#")) return@forEachIndexed

        val separator = line.indexOf('=')
        require(separator > 0) {
            "${file.path}:$lineNumber: malformed property; expected KEY=value."
        }
        val key = line.substring(0, separator)
        val value = line.substring(separator + 1)
        require(versionPropertyKey.matches(key)) {
            "${file.path}:$lineNumber: malformed property key '$key'."
        }
        require(key == "VERSION_NAME" || key == "VERSION_CODE") {
            "${file.path}:$lineNumber: unknown version field '$key'."
        }
        require(!values.containsKey(key)) {
            "${file.path}:$lineNumber: duplicate version field '$key'."
        }
        require(value == value.trim()) {
            "${file.path}:$lineNumber: $key must not have surrounding whitespace."
        }
        values[key] = value
    }

    require(values.keys == setOf("VERSION_NAME", "VERSION_CODE")) {
        val missing = setOf("VERSION_NAME", "VERSION_CODE") - values.keys
        "${file.path}: missing version field(s): ${missing.joinToString(", ")}."
    }

    val versionName = values.getValue("VERSION_NAME")
    require(simpleSemVer.matches(versionName)) {
        "${file.path}: invalid VERSION_NAME '$versionName'; expected simple SemVer major.minor.patch."
    }
    val versionCodeText = values.getValue("VERSION_CODE")
    require(positiveVersionCode.matches(versionCodeText)) {
        "${file.path}: invalid VERSION_CODE '$versionCodeText'; expected a positive decimal integer."
    }
    val versionCode = versionCodeText.toLongOrNull()
    require(versionCode != null && versionCode <= MAX_VERSION_CODE) {
        "${file.path}: VERSION_CODE '$versionCodeText' must be between 1 and $MAX_VERSION_CODE."
    }

    return StayLeafyVersion(versionName, versionCode.toInt())
}

private val stayLeafyVersion = readStayLeafyVersion(rootProject.file("version.properties"))
private val releaseSigningEnvironment = listOf(
    "STAYLEAFY_RELEASE_KEYSTORE_PATH",
    "STAYLEAFY_RELEASE_STORE_PASSWORD",
    "STAYLEAFY_RELEASE_KEY_ALIAS",
    "STAYLEAFY_RELEASE_KEY_PASSWORD",
)
private val releaseSigningProviders = releaseSigningEnvironment.associateWith {
    project.providers.environmentVariable(it)
}
private val releaseSigningPresence = releaseSigningProviders.mapValues { (_, provider) ->
    provider.map { value -> value.isNotBlank() }.orElse(false).get()
}
private val releasePackagingPrefixes = listOf("assemble", "bundle", "package", "sign", "install")

private fun isReleasePackagingTask(taskName: String): Boolean {
    if (taskName == "assemble" || taskName == "build") return true
    return releasePackagingPrefixes.any { prefix ->
        taskName.startsWith(prefix) && taskName.removePrefix(prefix).startsWith("Release")
    }
}

private val releaseSigningIsConfigured = releaseSigningPresence.values.all { it }
private val releaseProjectDirectory = project.projectDir.absoluteFile

if (releaseSigningIsConfigured && gradle.startParameter.isConfigurationCacheRequested) {
    throw GradleException(
        "Release signing reads secret environment variables; rerun release packaging with --no-configuration-cache."
    )
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.hilt.gradle)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.miguelrodriguez19.stayleafy"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.miguelrodriguez19.stayleafy"
        minSdk = 24
        targetSdk = 37
        versionCode = stayLeafyVersion.code
        versionName = stayLeafyVersion.name

        testInstrumentationRunner = "com.miguelrodriguez19.stayleafy.HiltTestRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    if (releaseSigningIsConfigured) {
        signingConfigs {
            create("stayLeafyRelease") {
                storeFile = project.file(releaseSigningProviders.getValue("STAYLEAFY_RELEASE_KEYSTORE_PATH").get())
                storePassword = releaseSigningProviders.getValue("STAYLEAFY_RELEASE_STORE_PASSWORD").get()
                keyAlias = releaseSigningProviders.getValue("STAYLEAFY_RELEASE_KEY_ALIAS").get()
                keyPassword = releaseSigningProviders.getValue("STAYLEAFY_RELEASE_KEY_PASSWORD").get()
            }
        }
        buildTypes.getByName("release") {
            signingConfig = signingConfigs.getByName("stayLeafyRelease")
        }
    }

    buildFeatures {
        compose = true
        aidl = false
        buildConfig = false
        shaders = false
    }

    androidResources {
        generateLocaleConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    sourceSets {
        getByName("androidTest").assets.directories.add(
            "$projectDir/schemas",
        )
    }
}

val validateReleaseSigning = tasks.register("validateReleaseSigning") {
    group = "verification"
    description = "Checks the local release signing configuration before release packaging."
    val missingSigningEnvironment = releaseSigningEnvironment.filter { !releaseSigningPresence.getValue(it) }
    val keystorePathProvider = releaseSigningProviders.getValue("STAYLEAFY_RELEASE_KEYSTORE_PATH").orElse("")
    val keystorePath = keystorePathProvider.get()
    val keystore = File(keystorePath).let { candidate ->
        if (candidate.isAbsolute) candidate else File(releaseProjectDirectory, keystorePath)
    }
    inputs.property("keystorePath", keystorePathProvider)
    inputs.property("missingSigningEnvironment", missingSigningEnvironment.joinToString(","))
    inputs.property("signingEnvironmentConfigured", missingSigningEnvironment.isEmpty())
    doLast {
        require(missingSigningEnvironment.isEmpty()) {
            "Release signing is required for release packaging. Configure these environment variables: ${missingSigningEnvironment.joinToString(", ")}."
        }

        require(keystore.isFile && keystore.canRead()) {
            "Release signing keystore '$keystorePath' does not exist or is not readable."
        }
    }
}

tasks.configureEach {
    if (isReleasePackagingTask(name)) dependsOn(validateReleaseSigning)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Core Android dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.datastore.preferences)

    // Hilt Dependency Injection
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.work)
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)
    // Hilt and instrumented tests.
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.android.compiler)
    // Hilt and Robolectric tests.
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.android.compiler)

    // Arch Components
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    ksp(libs.androidx.room.compiler)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.serialization.json)

    // Compose
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.coil.compose)
    implementation(libs.ucrop)
    // Tooling
    debugImplementation(libs.androidx.compose.ui.tooling)
    // Instrumented tests
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Local tests: jUnit, coroutines, Android runner
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // Instrumented tests: jUnit rules and runners

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)

    // Navigation
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
}
