
package dev.coder2195.rewired;

import dev.coder2195.rewired.registry.RewiredBlockEntityTypes;
import dev.coder2195.rewired.registry.RewiredBlocks;
import dev.coder2195.rewired.registry.RewiredCreativeModeTabs;
import dev.coder2195.rewired.registry.RewiredItems;
import dev.seraphina.rewired.RewiredGameRules;
import dev.seraphina.rewired.fast.Bluestone;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import static dev.coder2195.rewired.Rewired.LOGGER;

public class RewiredFabric implements ModInitializer {
	@Override
	public void onInitialize() {
		LOGGER.info("Fabric!");

		RewiredBlocks.init();
		RewiredItems.init();
		RewiredCreativeModeTabs.init();
		RewiredBlockEntityTypes.init();
		RewiredGameRules.init();

		ServerLifecycleEvents.SERVER_STARTING.register(server -> Bluestone.INSTANCE.start(server));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> Bluestone.INSTANCE.stop());
	}
}

