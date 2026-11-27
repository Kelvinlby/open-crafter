plugins {
	id("net.fabricmc.fabric-loom")
	`maven-publish`
}

repositories {
	maven("https://maven.isxander.dev/releases") {
		name = "Xander"
		content { includeGroup("dev.isxander") }
	}
	maven("https://maven.terraformersmc.com/releases") {
		name = "TerraformersMC"
		content { includeGroup("com.terraformersmc") }
	}
	maven("https://repo.viaversion.com/everything") {
		name = "ViaVersion"
		content { includeGroup("com.viaversion") }
	}
	mavenCentral()
}

// ---- Recording encoder --------------------------------------------------------------------------
// Replays packet recordings in headless clients and writes datasets. Never part of the mod jar:
// it is its own source set (and its own Fabric mod, loaded only by the encoder's worker clients).
val encoder: SourceSet by sourceSets.creating {
	compileClasspath += sourceSets.main.get().compileClasspath + sourceSets.main.get().output
	runtimeClasspath += sourceSets.main.get().runtimeClasspath + sourceSets.main.get().output
}
val encoderTest: SourceSet by sourceSets.creating {
	compileClasspath += encoder.compileClasspath + encoder.output
	runtimeClasspath += encoder.runtimeClasspath + encoder.output
}
configurations[encoderTest.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[encoderTest.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())

dependencies {
	// To change the versions see the gradle.properties file
	minecraft("com.mojang:minecraft:${providers.gradleProperty("minecraft_version").get()}")
	implementation("net.fabricmc:fabric-loader:${providers.gradleProperty("loader_version").get()}")

	// Fabric API. This is technically optional, but you probably want it anyway.
	implementation("net.fabricmc.fabric-api:fabric-api:${providers.gradleProperty("fabric_api_version").get()}")

	// The settings screen. Bundled (jar-in-jar) so users need no extra download; a newer standalone copy wins.
	val yacl = "dev.isxander:yet-another-config-lib:${providers.gradleProperty("yacl_version").get()}"
	// Its own libraries ship inside its jar, so its POM dependencies are not needed.
	implementation(yacl) { isTransitive = false }
	include(yacl)
	// Optional: the settings screen also opens from Mod Menu's list when it is installed.
	compileOnly("com.terraformersmc:modmenu:${providers.gradleProperty("modmenu_version").get()}") { isTransitive = false }

	// Encoder only: protocol translation of older recordings, and zstd for recordings and flow.
	"encoderImplementation"("com.viaversion:viaversion-common:${providers.gradleProperty("viaversion_version").get()}")
	"encoderImplementation"("com.viaversion:viabackwards-common:${providers.gradleProperty("viabackwards_version").get()}")
	"encoderImplementation"("com.viaversion:vialoader:${providers.gradleProperty("vialoader_version").get()}") { isTransitive = false }
	"encoderImplementation"("com.github.luben:zstd-jni:${providers.gradleProperty("zstd_version").get()}")

	testImplementation(platform("org.junit:junit-bom:5.13.4"))
	testImplementation("org.junit.jupiter:junit-jupiter")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
	useJUnitPlatform()
	// Log4j (on the Minecraft classpath) writes logs/ into the working directory: keep it in build/.
	val testRunDir = layout.buildDirectory.dir("test-run").get().asFile
	workingDir = testRunDir
	doFirst { testRunDir.mkdirs() }
	systemProperty("opencrafter.projectDir", layout.projectDirectory.asFile.absolutePath)
}

loom {
	mods {
		register("open-crafter") { sourceSet(sourceSets.main.get()) }
		register("open-crafter-encoder") { sourceSet(encoder) }
	}
	runs {
		// A worker client of the encoder: the mod plus the encoder mod. Started by the `encode` task
		// (through the launch spec below), one process per recording, each in its own game directory.
		register("encoderWorker") {
			client()
			source(encoder)
			configName = "Encoder worker"
			ideConfigGenerated(false)
			runDir("build/encoder/worker-run")
		}
	}
}

tasks.named<ProcessResources>(encoder.processResourcesTaskName) {
	val version = version
	inputs.property("version", version)
	filesMatching("fabric.mod.json") { expand("version" to version) }
}

// Writes how to start a worker client (java, JVM arguments, classpath, main class) so the encoder
// can start many of them itself; Loom only knows how to start one, from Gradle.
val encoderWorkerLaunch = tasks.register("encoderWorkerLaunch") {
	description = "Writes the launch spec of an encoder worker client."
	group = "encoder"
	val runTask = tasks.named<JavaExec>("runEncoderWorker")
	// What the run task needs before launching (classes, DLI config, assets), but never the run task itself.
	dependsOn(encoder.classesTaskName, sourceSets.main.get().classesTaskName, "configureClientLaunch", "downloadAssets")
	val spec = layout.buildDirectory.file("encoder/worker-launch.json")
	outputs.file(spec)
	outputs.upToDateWhen { false }
	notCompatibleWithConfigurationCache("reads the Loom run task at execution time")
	doLast {
		val run = runTask.get()
		val json = groovy.json.JsonOutput.toJson(mapOf(
			"java" to run.javaLauncher.get().executablePath.asFile.absolutePath,
			"jvmArgs" to run.allJvmArgs,
			"classpath" to run.classpath.files.map { it.absolutePath },
			"mainClass" to run.mainClass.get(),
			"args" to run.args,
			"profileDir" to layout.projectDirectory.dir("run").asFile.absolutePath
		))
		spec.get().asFile.writeText(groovy.json.JsonOutput.prettyPrint(json))
	}
}

// ./gradlew encode -Pinput=<recordings dir or file> -Poutput=<datasets dir> [-Pworkers=N] [-Pconfig=encoder.json]
tasks.register<JavaExec>("encode") {
	description = "Replays packet recordings in parallel headless clients and writes datasets."
	group = "encoder"
	dependsOn(encoderWorkerLaunch)
	classpath = encoder.runtimeClasspath
	mainClass = "mod.kelvinlby.opencrafter.encoder.pipeline.EncodeMain"
	workingDir = layout.projectDirectory.asFile
	maxHeapSize = "2g"
	notCompatibleWithConfigurationCache("starts worker clients from the Loom run setup")
	val launch = layout.buildDirectory.file("encoder/worker-launch.json")
	doFirst {
		args(listOfNotNull(
			"--launch", launch.get().asFile.absolutePath,
			"--input", providers.gradleProperty("input").orNull ?: throw GradleException("Give -Pinput=<recording or folder>"),
			"--output", providers.gradleProperty("output").orNull ?: throw GradleException("Give -Poutput=<folder>"),
			providers.gradleProperty("workers").orNull?.let { "--workers=$it" },
			providers.gradleProperty("config").orNull?.let { "--config=$it" },
			providers.gradleProperty("profile").orNull?.let { "--profile=$it" },
			providers.gradleProperty("display").orNull?.let { "--display=$it" },
			providers.gradleProperty("force").orNull?.let { "--force" }
		))
	}
}

val encoderTests = tasks.register<Test>("encoderTest") {
	description = "Tests of the recording encoder (not part of the mod)."
	group = "verification"
	testClassesDirs = encoderTest.output.classesDirs
	classpath = encoderTest.runtimeClasspath
	useJUnitPlatform()
	val runDir = layout.buildDirectory.dir("encoder-test-run").get().asFile
	workingDir = runDir
	doFirst { runDir.mkdirs() }
	// Optional: a folder of real recordings for the end-to-end translation tests.
	providers.gradleProperty("sampleRecordings").orNull?.let { systemProperty("encoder.sampleRecordings", it) }
	maxHeapSize = "4g"
}
tasks.check { dependsOn(encoderTests) }

tasks.processResources {
	val version = version
	inputs.property("version", version)

	filesMatching("fabric.mod.json") {
		expand("version" to version)
	}
}

tasks.withType<JavaCompile>().configureEach {
	options.release = 25
}

java {
	// Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task
	// if it is present.
	// If you remove this line, sources will not be generated.
	withSourcesJar()

	sourceCompatibility = JavaVersion.VERSION_25
	targetCompatibility = JavaVersion.VERSION_25
}

tasks.jar {
	val projectName = project.name
	inputs.property("projectName", projectName)

	from("LICENSE") {
		rename { "${it}_$projectName" }
	}
}

// configure the maven publication
publishing {
	publications {
		register<MavenPublication>("mavenJava") {
			from(components["java"])
		}
	}

	// See https://docs.gradle.org/current/userguide/publishing_maven.html for information on how to set up publishing.
	repositories {
		// Add repositories to publish to here.
		// Notice: This block does NOT have the same function as the block in the top level.
		// The repositories here will be used for publishing your artifact, not for
		// retrieving dependencies.
	}
}
