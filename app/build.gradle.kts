plugins {
	id("com.android.application")
	kotlin("android")
	alias(libs.plugins.kotlin.serialization)
	alias(libs.plugins.kotlin.compose)
	alias(libs.plugins.aboutlibraries)
}

java {
	toolchain {
		languageVersion.set(JavaLanguageVersion.of(21))
	}
}

android {
	namespace = "org.jellyfin.androidtv"
	compileSdk = libs.versions.android.compileSdk.get().toInt()

	defaultConfig {
		minSdk = libs.versions.android.minSdk.get().toInt()
		targetSdk = libs.versions.android.targetSdk.get().toInt()

		// Release version
		applicationId = "dune.enhanced.tv"
		versionName = project.getVersionName()
		versionCode = getVersionCode(versionName!!)
	}

	buildFeatures {
		buildConfig = true
		viewBinding = true
		compose = true
		dataBinding = true
	}

	compileOptions {
		isCoreLibraryDesugaringEnabled = true
		sourceCompatibility = JavaVersion.VERSION_21
		targetCompatibility = JavaVersion.VERSION_21
	}

	// Configure Kotlin compiler options
	kotlin {
		jvmToolchain(21)
	}

	tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
		compilerOptions {
			jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
			freeCompilerArgs.add("-Xjvm-default=all")
		}
	}

	buildTypes {
		val release by getting {
			isMinifyEnabled = false

			// Set flavored application name
			resValue("string", "app_name", "@string/app_name_release")

			buildConfigField("boolean", "DEVELOPMENT", "false")
		}

		val debug by getting {
			// Use different application id to run release and debug at the same time
			applicationIdSuffix = ".debug"

			// Set flavored application name
			resValue("string", "app_name", "@string/app_name_debug")

			buildConfigField("boolean", "DEVELOPMENT", (defaultConfig.versionCode!! < 100).toString())
		}
	}

	// Define a build flavor for the enhanced version that can be installed alongside the original
	flavorDimensions += listOf("variant")
	productFlavors {
		create("standard") {
			dimension = "variant"
			// Uses default applicationId
		}

		create("enhanced") {
			dimension = "variant"
			applicationId = "Dune.enhanced.tv"

			// Set specific version name for enhanced variant
			versionName = "0.0.8"

			// Set app name for the enhanced version
			resValue("string", "app_name_release", "DUNE")

			// Add required string resources that are referenced in XML files
			resValue("string", "app_id", applicationId!!)
			resValue("string", "app_search_suggest_authority", "${applicationId}.content")
			resValue("string", "app_search_suggest_intent_data", "content://${applicationId}.content/intent")
		}
	}

	lint {
		lintConfig = file("$rootDir/android-lint.xml")
		abortOnError = false
		sarifReport = true
		checkDependencies = true
	}

	// Configure output file names for APKs
	applicationVariants.all {
		val variant = this
		val variantName = variant.name
		val versionName = variant.versionName
		
		variant.outputs.all {
			val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
			if (variantName == "enhanced") {
				output.outputFileName = "Dune.androidtv-0.0.8.apk"
			} else {
				output.outputFileName = "Dune.androidtv-${versionName}.apk"
			}
		}
	}

	testOptions.unitTests.all {
		it.useJUnitPlatform()
	}
}

val versionTxt by tasks.registering {
	val path = layout.buildDirectory.asFile.get().resolve("version.txt")

	doLast {
		val versionString = "v${android.defaultConfig.versionName}=${android.defaultConfig.versionCode}"
		logger.info("Writing [$versionString] to $path")
		path.writeText("$versionString\n")
	}
}

// Simple task to build the enhanced version
tasks.register("buildEnhanced") {
	group = "build"
	description = "Builds the enhanced version with package ID: Dune.enhanced.tv"
	dependsOn("assembleEnhancedRelease")
	doLast {
		println("\nBuilding Enhanced version with:")
		println("Package ID: Dune.enhanced.tv")
		println("Version: 0.0.8")
		println("App Name: DUNE")
		println("Filename: Dune.androidtv-0.0.8.apk")
		println("The APK will be available in: app/build/outputs/apk/enhanced/release/")
	}
}



dependencies {
	implementation("androidx.compose.material:material:1.5.0")
	// Jellyfin
	implementation(projects.playback.core)
	implementation(projects.playback.jellyfin)
	implementation(projects.playback.media3.exoplayer)
	implementation(projects.playback.media3.session)
	implementation(projects.preference)
	implementation(libs.jellyfin.sdk) {
		// Change version if desired
		val sdkVersion = findProperty("sdk.version")?.toString()
		when (sdkVersion) {
			"local" -> version { strictly("latest-SNAPSHOT") }
			"snapshot" -> version { strictly("master-SNAPSHOT") }
			"unstable-snapshot" -> version { strictly("openapi-unstable-SNAPSHOT") }
		}
	}

	// Kotlin
	implementation(libs.kotlinx.coroutines)
	implementation(libs.kotlinx.serialization.json)
	implementation(kotlin("reflect"))

	// Android(x)
	implementation(libs.androidx.core)
	implementation(libs.androidx.activity)
	implementation(libs.androidx.fragment)
	implementation(libs.androidx.fragment.compose)
	implementation(libs.androidx.leanback.core)
	implementation(libs.androidx.leanback.preference)
	implementation(libs.androidx.preference)
	implementation(libs.androidx.appcompat)
	implementation(libs.androidx.tvprovider)
	implementation(libs.androidx.constraintlayout)
	implementation(libs.androidx.recyclerview)
	implementation(libs.androidx.work.runtime)
	implementation(libs.bundles.androidx.lifecycle)
	implementation(libs.androidx.window)
	implementation(libs.androidx.cardview)
	implementation(libs.androidx.startup)
	implementation(libs.bundles.androidx.compose)

	// Dependency Injection
	implementation(libs.bundles.koin)

	// Media players
	implementation(libs.androidx.media3.exoplayer)
	implementation(libs.androidx.media3.datasource.okhttp)
	implementation(libs.androidx.media3.exoplayer.hls)
	implementation(libs.androidx.media3.ui)
	implementation(libs.jellyfin.androidx.media3.ffmpeg.decoder)
	implementation(libs.libass.media3)

	// Markdown
	implementation(libs.bundles.markwon)

	// Logging
	implementation(libs.timber)
	implementation(libs.slf4j.timber)

	// Image utility
	implementation(libs.bundles.coil)
	implementation("com.github.bumptech.glide:glide:4.16.0")
	implementation("androidx.viewpager2:viewpager2:1.0.0")
	// GIF support
	implementation("pl.droidsonroids.gif:android-gif-drawable:1.2.28")

	// Crash Reporting
	implementation(libs.bundles.acra)

	// Licenses
	implementation(libs.aboutlibraries)

	// Logging
	implementation(libs.timber)
	implementation(libs.slf4j.timber)

	// Network
	implementation("com.squareup.okhttp3:okhttp:4.11.0")

	// Compatibility (desugaring)
	coreLibraryDesugaring(libs.android.desugar)

	// Testing
	testImplementation(libs.kotest.runner.junit5)
	testImplementation(libs.kotest.assertions)
	testImplementation(libs.mockk)
}
