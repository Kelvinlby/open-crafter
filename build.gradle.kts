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
}

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
