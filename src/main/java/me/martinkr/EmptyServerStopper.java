package me.martinkr;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.MinecraftServer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Timer;
import java.util.TimerTask;

public class EmptyServerStopper implements ModInitializer {
	public static final String MOD_ID = "emptyserverstopper";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final String MOD_NAME = "Empty Server Stopper";

	private long shutdownTimeMinutes;
	private boolean shutdownOnLaunch;
	private long shutdownTimeMs;
	private int minimumPlayersBeforeShutdown;
	private static Timer timer;

	@Override
	public void onInitialize() {
		EmptyServerStopperConfigManager.init();

		shutdownTimeMinutes = EmptyServerStopperConfigManager.getConfig().shutdownTimeMinutes;
		shutdownOnLaunch = EmptyServerStopperConfigManager.getConfig().shutdownOnLaunch;
		shutdownTimeMs = 1000L * 60L * shutdownTimeMinutes;
		minimumPlayersBeforeShutdown = EmptyServerStopperConfigManager.getConfig().minimumPlayersBeforeShutdown;

		ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
		ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);
		ServerPlayConnectionEvents.JOIN.register(this::onPlayerJoining);
		ServerPlayConnectionEvents.DISCONNECT.register(this::onPlayerDisconnecting);
	}

	private void onPlayerJoining(ServerPlayNetworkHandler serverPlayNetworkHandler, PacketSender packetSender, MinecraftServer minecraftServer) {
		if (timer != null) {
			LOGGER.info("[{}] Player joined - Server shutdown cancelled.", MOD_NAME);
			timer.cancel();
			timer.purge();
			timer = null;
		}
	}

	private void onPlayerDisconnecting(ServerPlayNetworkHandler serverPlayNetworkHandler, MinecraftServer minecraftServer) {
		countPlayers(minecraftServer);
	}

	public void onServerStarted(MinecraftServer minecraftServer) {
			if (shutdownOnLaunch) {
				countPlayers(minecraftServer);
			}
	}

	public void onServerStopping(MinecraftServer server) {
		if (timer != null) {
			timer.cancel();
		}
	}

	public void countPlayers(MinecraftServer minecraftServer) {
		if (minecraftServer.getCurrentPlayerCount() <= minimumPlayersBeforeShutdown) {
			LOGGER.info("[{}] Server Empty - Server will shutdown in {} minute(s)!", MOD_NAME, shutdownTimeMinutes);
			TimerTask task = new TimerTask() {
				public void run() {
					stop(minecraftServer);
				}
			};
			timer = new Timer();
			timer.schedule(task, (shutdownTimeMs));
		}
	}

	public static void stop(MinecraftServer minecraftServer) {
		LOGGER.info("[{}] Server is shutting down.", MOD_NAME);
		minecraftServer.stop(true);
	}
}