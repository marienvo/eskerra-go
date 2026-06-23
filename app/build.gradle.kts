plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.sentry.android)
}

// Reads a single key from local.properties as a tracked ValueSource so that
// changes to the file invalidate the configuration cache entry.
abstract class LocalPropertySource : ValueSource<String, LocalPropertySource.Params> {
    interface Params : ValueSourceParameters {
        val file: RegularFileProperty
        val key: Property<String>
    }

    override fun obtain(): String? {
        val f = parameters.file.asFile.get()
        if (!f.isFile) return null
        return java.util.Properties()
            .apply { f.inputStream().use(::load) }
            .getProperty(parameters.key.get())
    }
}

fun localProperty(name: String): Provider<String> =
    providers.of(LocalPropertySource::class.java) {
        parameters.file.set(rootProject.layout.projectDirectory.file("local.properties"))
        parameters.key.set(name)
    }

fun stringBuildConfigField(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

val sentryDsn = providers.environmentVariable("SENTRY_DSN")
    .orElse(providers.gradleProperty("sentry.dsn"))
    .orElse(localProperty("sentry.dsn"))
    .orElse("")
val sentryAuthToken = providers.environmentVariable("SENTRY_AUTH_TOKEN")
    .orElse(providers.gradleProperty("sentry.auth.token"))
    .orElse("")
val shouldUploadSentryMappings = sentryAuthToken.get().isNotBlank()

android {
    namespace = "com.eskerra.go"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.eskerra.go"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SENTRY_DSN", stringBuildConfigField(sentryDsn.get()))
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
        htmlReport = true
        xmlReport = true
    }

    packaging {
        resources {
            // JGit bundles service descriptors and metadata that collide when merged.
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/*.txt"
            excludes += "META-INF/jgit-*"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/LICENSE"
        }
    }
}

sentry {
    org.set("personal-133")
    projectName.set("eskerra-go")
    url.set("https://sentry.io/")
    authToken.set(sentryAuthToken)
    includeProguardMapping.set(shouldUploadSentryMappings)
    autoUploadProguardMapping.set(shouldUploadSentryMappings)
    ignoredBuildTypes.set(setOf("debug"))
    tracingInstrumentation {
        enabled.set(false)
    }
}

ktlint {
    android.set(true)
    additionalEditorconfig.set(
        mapOf(
            "ktlint_code_style" to "android_studio",
            "ktlint_function_naming_ignore_when_annotated_with" to "Composable"
        )
    )
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.jgit)
    implementation(libs.okhttp)

    // Compose markdown renderer (Phase 3+)
    implementation(libs.markdown.renderer.core)
    implementation(libs.markdown.renderer.m3)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)

    // Image loading for vault attachments (Phase 5+)
    implementation(libs.coil.compose)

    // Date/time for relative labels, reminder-pill tone, Today Hub week math (Phase 2+)
    implementation(libs.kotlinx.datetime)

    // Bundled SQLite includes FTS5; stock Android SQLite often omits the fts5 module.
    implementation(libs.androidx.sqlite)
    implementation(libs.androidx.sqlite.bundled)
    implementation(libs.sentry.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
