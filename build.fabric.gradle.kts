import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	id("net.fabricmc.fabric-loom")
	id("dev.kikugie.fletching-table.fabric") version "0.1.0-alpha.22"
	id("me.modmuss50.mod-publish-plugin") version "2.1.1"
	id("org.jetbrains.kotlin.jvm") version "2.4.10"
}

// DO NOT set group = ...!
val modId = property("mod.id") as String
version = "${property("mod.version")}"
base.archivesName = "${modId}-fabric"

val requiredJava = JavaVersion.VERSION_25

// This can be used for publishing on Modrinth and Curseforge
val compatibleVersions: List<String> = sc.properties.rawOrNull("mod", "mc_releases")
	?.asList().orEmpty().map { it.toString() }

repositories {
	/**
	 * Restricts dependency search of the given [groups] to the [maven URL][url],
	 * improving the setup speed.
	 */
	fun strictMaven(url: String, alias: String, vararg groups: String) = exclusiveContent {
		forRepository { maven(url) { name = alias } }
		filter { groups.forEach(::includeGroup) }
	}
	strictMaven("https://www.cursemaven.com", "CurseForge", "curse.maven")
	strictMaven("https://api.modrinth.com/maven", "Modrinth", "maven.modrinth")
}

dependencies {
	minecraft("com.mojang:minecraft:${sc.current.version}")
	implementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
	implementation("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")
	implementation("net.fabricmc:fabric-language-kotlin:${property("deps.fabric_kotlin")}")
}

loom {
//	fabricModJsonPath = rootProject.file("src/main/resources/fabric.mod.json") // Useful for interface injection
	accessWidenerPath = sc.process(
		rootProject.file("src/main/resources/rewired.ct"),
		"build/processed.ct"
	)

	splitEnvironmentSourceSets()

	mods {
		create("rewired") {
			sourceSet(sourceSets["main"])
			sourceSet(sourceSets["client"])
		}
	}


	decompilerOptions.named("vineflower") {
		options.put("mark-corresponding-synthetics", "1")
	}

	runConfigs["client"].apply {
	}

	runConfigs["server"].apply {
		runDirectory = project.file("serverrun")
	}

	runConfigs.all {
		preferGradleTask = true
		generateRunConfig = true
		jvmArguments.add("-Dmixin.debug.export=true -XX:+AllowEnhancedClassRedefinition")
	}

}

fabricApi {
	configureDataGeneration {
		client = true
		outputDirectory = rootProject.file("src/generated/resources")
	}
}

	fletchingTable {
		fabric {
			applyMixinConfig = false
		}
	}

java {
	withSourcesJar()
	targetCompatibility = requiredJava
	sourceCompatibility = requiredJava

	toolchain {
		vendor = JvmVendorSpec.ADOPTIUM
		languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion)
	}
}

kotlin {
	compilerOptions {
		jvmTarget = JvmTarget.JVM_25
	}
}

tasks.withType<ProcessResources> {
	dependsOn("stonecutterGenerate")
}

tasks {
	processResources {
		fun MutableMap<String, String>.register(key: String, property: String) {
			val value: String = sc.properties[property]
			inputs.property(key, value)
			set(key, value)
		}

		val props = buildMap {
			register("id", "mod.id")
			register("name", "mod.name")
			register("version", "mod.version")
			register("minecraft", "mod.mc_compat")
			register("description", "mod.description")
		}

		filesMatching("fabric.mod.json") { expand(props) }

		val mixinJava = "JAVA_${requiredJava.majorVersion}"
		filesMatching("*.mixins.json") { expand("java" to mixinJava) }

		exclude("META-INF/neoforge.mods.toml")
	}


	register<Copy>("buildAndCollect") {
		group = "build"
		description = "Builds mod jars and copies results to `build/libs/{mod version}/`"

		inputs.property("version", project.property("mod.version"))
		into(rootProject.layout.buildDirectory.file("libs/${project.property("mod.version")}"))
	}
}


publishMods {
	file = tasks.jar.map { it.archiveFile.get() }
	displayName = "${property("mod.name")} ${property("mod.version")} for Fabric"
	version = property("mod.version") as String
	changelog.set(rootProject.file("CHANGELOG.md").readText())
	type = STABLE
	modLoaders.add("fabric")

	dryRun = !env.isPresent("MODRINTH_TOKEN")
		|| !env.isPresent("CURSEFORGE_TOKEN")

	modrinth {
		projectId = property("publish.modrinth") as String
		accessToken = env.fetch("MODRINTH_TOKEN", "")
		minecraftVersions.addAll(compatibleVersions)
		requires("fabric-api")
		type = ALPHA

		environment = CLIENT_AND_SERVER
	}

	curseforge {
		projectId = property("publish.curseforge") as String
		accessToken = env.fetch("CURSEFORGE_TOKEN", "")
		requires("fabric-api")
		minecraftVersions.addAll(compatibleVersions)

		client = true
		server = true
		changelogType = "markdown"
		javaVersions.add(JavaVersion.VERSION_25)
	}
}