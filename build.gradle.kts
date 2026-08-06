import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	id("net.fabricmc.fabric-loom") version "1.17-SNAPSHOT"
	id("org.jetbrains.kotlin.jvm") version "2.4.10"
}

val modId = property("mod.id") as String
val modName = property("mod.name") as String
val modVersion = property("mod.version") as String
val modDescription = property("mod.description") as String

group = property("mod.group") as String
version = modVersion
base.archivesName = modId

val requiredJava = JavaVersion.VERSION_25
val minecraftVersion = property("minecraft_version") as String
val compatibleVersions: List<String> = listOf(minecraftVersion)

repositories {
	fun strictMaven(url: String, alias: String, vararg groups: String) = exclusiveContent {
		forRepository { maven(url) { name = alias } }
		filter { groups.forEach(::includeGroup) }
	}
	strictMaven("https://www.cursemaven.com", "CurseForge", "curse.maven")
	strictMaven("https://api.modrinth.com/maven", "Modrinth", "maven.modrinth")
}

dependencies {
	minecraft("com.mojang:minecraft:$minecraftVersion")
	implementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
	implementation("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")
	implementation("net.fabricmc:fabric-language-kotlin:${property("deps.fabric_kotlin")}")
}

loom {
	accessWidenerPath = rootProject.file("src/main/resources/rewired.ct")

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

tasks {
	processResources {
		val props = mapOf(
			"id" to modId,
			"name" to modName,
			"version" to modVersion,
			"minecraft" to minecraftVersion,
			"description" to modDescription
		)
		inputs.properties(props)

		filesMatching("fabric.mod.json") { expand(props) }

		val mixinJava = "JAVA_${requiredJava.majorVersion}"
		filesMatching("*.mixins.json") { expand("java" to mixinJava) }
	}

	register<Copy>("buildAndCollect") {
		group = "build"
		description = "Builds mod jars and copies results to `build/libs/{mod version}/`"

		inputs.property("version", modVersion)
		into(rootProject.layout.buildDirectory.file("libs/$modVersion"))
	}
}
