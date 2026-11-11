import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
}

// 장소 중계 API 주소(F-16). 저장소에 올리지 않는 local.properties에서 읽고, 없으면 빈 값(데이터 없는 미션으로 전환)
val placesApiUrl: String =
    Properties()
        .apply {
            rootProject
                .file("local.properties")
                .takeIf { it.exists() }
                ?.inputStream()
                ?.use { load(it) }
        }.getProperty("PLACES_API_URL", "")
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")

android {
    namespace = "io.backseatlab.playground"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.backseatlab.playground"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "PLACES_API_URL", "\"$placesApiUrl\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        // 단위 테스트에서 android.util.Log 같은 호출이 예외 대신 빈 값을 돌려주게 한다
        unitTests.isReturnDefaultValues = true
    }
    lint {
        error += "UnusedResources"
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    source.setFrom(
        "src/main/java",
        "src/debug/java",
        "src/release/java",
        "src/test/java",
        "src/testDebug/java",
    )
}

// 핵심 로직(session·game)만 센다. pleos·ui·debug·di·place는 대상에서 빠진다.
kover {
    reports {
        filters {
            includes {
                packages(
                    "io.backseatlab.playground.session",
                    "io.backseatlab.playground.game",
                )
            }
        }
        verify {
            rule {
                minBound(80)
            }
        }
    }
}

// 테스트가 실패하면 이름과 원인을 바로 출력한다
tasks.withType<Test>().configureEach {
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.tts)
    implementation(libs.pleos.stt)
    implementation(libs.pleos.navi)
    testImplementation(libs.junit)
    testImplementation(libs.org.json) // 안드로이드 org.json은 단위 테스트에서 동작하지 않아 JVM용을 쓴다
    testImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
