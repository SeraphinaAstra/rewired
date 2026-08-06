package dev.coder2195.rewired;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface Rewired {
	String MOD_ID = "rewired";
	Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	String VERSION = "0.1.0";
	String MINECRAFT = "26.2";
	LoaderAccess INSTANCE = new FabricLoaderAccess();

	static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
	static Identifier id(String namespace, String path) {
		return Identifier.fromNamespaceAndPath(namespace, path);
	}

	static Identifier mcId(String path) {
		return Identifier.withDefaultNamespace(path);
	}

	static <T> ResourceKey<T> key(ResourceKey<? extends Registry<T>> registry, String path) {
		return ResourceKey.create(registry, id(path));
	}

	static <T> ResourceKey<T> key(ResourceKey<? extends Registry<T>> registry, String namespace, String path) {
		return ResourceKey.create(registry, id(namespace, path));
	}

	static <T> ResourceKey<T> mcKey(ResourceKey<? extends Registry<T>> registry, String path) {
		return ResourceKey.create(registry, mcId(path));
	}

	interface LoaderAccess {
		boolean isClient();
		boolean isServer();
		boolean isModLoaded(String id);
	}

	final class FabricLoaderAccess implements LoaderAccess {
		private final net.fabricmc.loader.api.FabricLoader loader = net.fabricmc.loader.api.FabricLoader.getInstance();

		@Override
		public boolean isClient() {
			return loader.getEnvironmentType().equals(net.fabricmc.api.EnvType.CLIENT);
		}

		@Override
		public boolean isServer() {
			return loader.getEnvironmentType().equals(net.fabricmc.api.EnvType.SERVER);
		}

		@Override
		public boolean isModLoaded(String id) {
			return loader.isModLoaded(id);
		}
	}
}
