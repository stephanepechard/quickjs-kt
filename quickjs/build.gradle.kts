/// Based on https://github.com/cashapp/zipline/blob/trunk/zipline/build.gradle.kts
import com.dokar.quickjs.applyQuickJsNativeBuildTasks
import com.dokar.quickjs.disableUnsupportedPlatformTasks
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.mavenPublish)
}

kotlin {
    androidTarget {
        publishLibraryVariants("release")
    }
    jvm()

    mingwX64()
    linuxX64()
    linuxArm64()
    macosX64()
    macosArm64()
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    applyDefaultHierarchyTemplate()

    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
            }
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }

        val jniMain by creating {
            dependsOn(commonMain)
        }

        val jvmMain by getting {
            dependsOn(jniMain)
        }

        val jvmTest by getting {
            kotlin.srcDir("src/jniTest/kotlin/")
        }

        val androidMain by getting {
            dependsOn(jniMain)
        }

        targets.withType<KotlinNativeTarget> {
            val main by compilations.getting

            main.cinterops {
                create("quickjs") {
                    headers(
                        file("native/quickjs/quickjs.h"),
                        file("native/common/quickjs_version.h"),
                    )
                    packageName("quickjs")
                }
            }
        }
    }
}

val cmakeFile = file("native/CMakeLists.txt")

android {
    namespace = "com.dokar.quickjs"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        multiDexEnabled = true

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        ndk {
            abiFilters += listOf("x86", "x86_64", "armeabi-v7a", "arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                arguments(
                    "-DANDROID_TOOLCHAIN=clang",
                    "-DTARGET_PLATFORM=android",
                    "-DLIBRARY_TYPE=shared",
                    "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"
                )
                cFlags("-fstrict-aliasing")
            }
        }

        packaging {
            jniLibs.keepDebugSymbols += "**/libquickjs.so"
        }
    }

    buildTypes {
        val release by getting {
            externalNativeBuild {
                cmake {
                    arguments("-DCMAKE_BUILD_TYPE=MinSizeRel", "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON")
                    cFlags(
                        "-g0",
                        "-Os",
                        "-fomit-frame-pointer",
                        "-DNDEBUG",
                        "-fvisibility=hidden"
                    )
                }
            }
        }

        val debug by getting {
            externalNativeBuild {
                cmake {
                    arguments += listOf("-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON")
                    cFlags("-g", "-DDEBUG", "-DDUMP_LEAKS")
                }
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = cmakeFile
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

applyQuickJsNativeBuildTasks(cmakeFile)

disableUnsupportedPlatformTasks()

afterEvaluate {
    // Disable Android tests
    tasks.named("testDebugUnitTest").configure {
        enabled = false
    }
    tasks.named("testReleaseUnitTest").configure {
        enabled = false
    }

    // use ./gradlew publishAllPublicationsToMavenRepository to publish all publications
    publishing {
        publications {
            if (name.contains("jvm", ignoreCase = true)) {
                create<MavenPublication>("mavenJava") {
                    groupId = "io.github.dokar3"
                    artifactId = "quickjs-kt"
                    version = "1.0.0-alpha13-16kb"
                    //from(components["java"])
                }
            }
        }
        repositories {
            maven {
                url = uri("https://da-android-965585009786.d.codeartifact.eu-west-3.amazonaws.com/maven/quickjs-kt/")
                credentials {
                    username = "aws"
                    password = System.env.CODEARTIFACT_AUTH_TOKEN
                }
            }
        }
    }
}
