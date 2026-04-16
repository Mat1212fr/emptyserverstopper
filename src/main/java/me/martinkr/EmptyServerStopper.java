package me.martinkr;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.MinecraftServer;

import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Timer;
import java.util.TimerTask;

import net.minecraft.server.command.CommandManager;

public class EmptyServerStopper implements DedicatedServerModInitializer {
	public static final String MOD_ID = "emptyserverstopper";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final String MOD_NAME = "Empty Server Stopper";

	private boolean enabled = true;

	private long shutdownTimeMinutes;
	private boolean timerOnLaunch;
	private long shutdownTimeMs;
	private int minimumPlayersBeforeShutdown;
	private boolean playersCanAskShutdownTime;

	private static Timer timer;
	private static MinecraftServer minecraftServer = null;
	private static long expectedShutdownTime = 0;

	@Override
	public void onInitializeServer() {
		EmptyServerStopperConfigManager.init();
		updateVar();

		ServerLifecycleEvents.SERVER_STARTING.register((server) -> {
			minecraftServer = server;
		});

		ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
		ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);
		ServerPlayConnectionEvents.JOIN.register(this::onPlayerJoining);
		ServerPlayConnectionEvents.DISCONNECT.register(this::onPlayerDisconnecting);

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(CommandManager.literal("ess")
					.then(CommandManager.literal("reload")
							.requires(source -> source.hasPermissionLevel(2))
							.executes(context -> {
								EmptyServerStopperConfigManager.loadConfig(false);
								updateVar();

								countPlayers(minecraftServer);

								sendFeedback(context, "§rPlugin has been reloaded.");

								return Command.SINGLE_SUCCESS;
							})
					)
					.then(CommandManager.literal("time")
							.requires(source -> {
								if (playersCanAskShutdownTime) {
									return true;
								} else {
									return source.hasPermissionLevel(2);
								}
							})
							.executes(context -> {
								String message;

								// Check if the timer is actually running
								if (timer == null) {
									message = "No shutdown is currently scheduled.";
								} else {
									long remainingMs = expectedShutdownTime - System.currentTimeMillis();

									if (remainingMs <= 0) {
										message = "Shutdown is imminent.";
									} else {
										long minutes = (remainingMs / 1000) / 60;
										long seconds = (remainingMs / 1000) % 60;
										message = String.format("Time remaining before shutdown: %d min %d sec.",
												minutes, seconds);
									}
								}

								sendFeedback(context, message);

								return Command.SINGLE_SUCCESS;
							})
					)
					.then(CommandManager.literal("enable")
							.requires(source -> source.hasPermissionLevel(2))
							.executes(context -> {
								if (enabled) {
									sendFeedback(context, "Already enabled.");
								} else {
									enabled = true;
									EmptyServerStopperConfigManager.getConfig().enabled = true;
									EmptyServerStopperConfigManager.saveConfig();

									countPlayers(minecraftServer);

									sendFeedback(context, "Mod enabled. Checking player count...");
								}
								return 1;
							})
					)
					.then(CommandManager.literal("disable")
							.requires(source -> source.hasPermissionLevel(2))
							.executes(context -> {
								if (!enabled) {
									sendFeedback(context, "Already disabled.");
								} else {
									enabled = false;
									EmptyServerStopperConfigManager.getConfig().enabled = false;
									EmptyServerStopperConfigManager.saveConfig();

									stopTimer(); // stop the countdown if one is running
									sendFeedback(context, "Mod disabled. Shutdown timer stopped.");
								}
								return 1;
							})
					)
					.then(CommandManager.literal("restart")
							.requires(source -> source.hasPermissionLevel(2))
							.executes(context -> {
								if (!enabled) {
									sendFeedback(context, "Mod is currently disabled. Use /ess enable first.");
									return 0;
								}

								countPlayers(minecraftServer);

								if (timer != null) {
									sendFeedback(context, "Shutdown timer restarted.");
								} else {
									sendFeedback(context, "Restart ignored: player count is above the threshold.");
								}

								return Command.SINGLE_SUCCESS;
							})
					)
			);
		});
	}

	private void updateVar() {
		enabled = EmptyServerStopperConfigManager.getConfig().enabled;
		shutdownTimeMinutes = EmptyServerStopperConfigManager.getConfig().shutdownTimeMinutes;
		timerOnLaunch = EmptyServerStopperConfigManager.getConfig().timerOnLaunch;
		shutdownTimeMs = 1000L * 60L * shutdownTimeMinutes;
		minimumPlayersBeforeShutdown = EmptyServerStopperConfigManager.getConfig().minimumPlayersBeforeShutdown;
		playersCanAskShutdownTime = EmptyServerStopperConfigManager.getConfig().playersCanAskShutdownTime;
	}

	private void stopTimer() {
		if (timer != null) {
			timer.cancel();
			timer.purge();
			timer = null;
		}
	}

	private void onServerStarted(MinecraftServer minecraftServer) {
		if (timerOnLaunch) {
			startShutdownTimer();
		}
	}

	private void onServerStopping(MinecraftServer server) {
		stopTimer();
	}

	private void onPlayerJoining(ServerPlayNetworkHandler handler, PacketSender sender, MinecraftServer server) {
		if (!enabled) return;

		int countWithNewPlayer = server.getCurrentPlayerCount() + 1;

		if (countWithNewPlayer >= minimumPlayersBeforeShutdown) {
			if (timer != null) {
				cancelShutdown();
				LOGGER.info("[{}] Player joined - Threshold exceeded, shutdown cancelled.", MOD_NAME);
			}
		}
	}

	private void onPlayerDisconnecting(ServerPlayNetworkHandler handler, MinecraftServer server) {
		if (!enabled) return;

		// Minus 1 before player seems to still be counted
		int countAfterDisconnect = server.getCurrentPlayerCount() - 1;
		if (countAfterDisconnect <= minimumPlayersBeforeShutdown) {
			if (timer == null) startShutdownTimer();
		}
	}

	public void countPlayers(MinecraftServer server) {
		if (!enabled) return;

		if (server.getCurrentPlayerCount() < minimumPlayersBeforeShutdown) {
			if (timer == null) startShutdownTimer();
		} else {
			if (timer != null) {
				cancelShutdown();
			}
		}
	}

	private void startShutdownTimer() {
		stopTimer();
		timer = new Timer();

		TimerTask task = new TimerTask() {
			@Override
			public void run() {
				if (minecraftServer != null) stop(minecraftServer);
			}
		};

		String messageContent = String.format("Server is empty - Server will shutdown in %d minute(s)!", shutdownTimeMinutes);

		LOGGER.info("[{}] {}", MOD_NAME, messageContent);

		if (minecraftServer != null && minecraftServer.getCurrentPlayerCount() > 0) {
			minecraftServer.getPlayerManager().broadcast(Text.literal("§6" + messageContent), false);
		}

		if (shutdownTimeMinutes > 1) {
			timer.schedule(new TimerTask() {
				@Override
				public void run() {
					if (minecraftServer != null && timer != null) {
						minecraftServer.getPlayerManager().broadcast(
								Text.literal("§cServer will shutdown in 1 minute!"), false);
					}
				}
			}, (shutdownTimeMs - 60000));
		}

		timer.schedule(task, shutdownTimeMs);
		expectedShutdownTime = System.currentTimeMillis() + shutdownTimeMs;
	}

	public static void stop(MinecraftServer minecraftServer) {
		LOGGER.info("[{}] Server is shutting down.", MOD_NAME);
		minecraftServer.stop(true);
	}

	private void sendFeedback(CommandContext<ServerCommandSource> context, String message) {
		String formattedMessage = String.format("[%s] ", MOD_NAME) + message;
		if (context.getSource().getEntity() == null) {
			LOGGER.info(formattedMessage);
		} else {
			context.getSource().sendFeedback(() -> Text.literal("§a" + formattedMessage), false);
		}
	}

	private void cancelShutdown() {
		if (timer != null) {
			stopTimer();
			String cancelMsg = "Shutdown cancelled. Player threshold reached!";
			LOGGER.info("[{}] {}", MOD_NAME, cancelMsg);

			if (minecraftServer != null && minecraftServer.getCurrentPlayerCount() > 0) {
				minecraftServer.getPlayerManager().broadcast(Text.literal("§a" + cancelMsg), false);
			}
		}
	}
}