package com.mat1212fr;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.permissions.Permissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Timer;
import java.util.TimerTask;

public class EmptyServerStopper implements DedicatedServerModInitializer {
	public static final String MOD_ID = "emptyserverstopper";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final String MOD_NAME = "Empty Server Stopper";

	private static Timer timer;
	private static MinecraftServer minecraftServer = null;
	private static long expectedShutdownTime = 0;
	private boolean enabled = true;
	private long shutdownTimeMinutes;
	private boolean timerOnLaunch;
	private long shutdownTimeMs;
	private int minimumPlayersBeforeShutdown;
	private boolean playersCanAskShutdownTime;
	private boolean playersCanAskMinPlayer;

	public static void stop(MinecraftServer minecraftServer) {
		LOGGER.info("[{}] Server is shutting down.", MOD_NAME);
		minecraftServer.halt(true);
	}

	@Override
	public void onInitializeServer() {
		EmptyServerStopperConfigManager.init();
		updateVar();

		ServerLifecycleEvents.SERVER_STARTING.register((server) -> minecraftServer = server);

		ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
		ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);
		ServerPlayConnectionEvents.JOIN.register(this::onPlayerJoining);
		ServerPlayConnectionEvents.DISCONNECT.register(this::onPlayerDisconnecting);

		CommandRegistrationCallback.EVENT.register(
			(dispatcher, _, _) -> dispatcher.register(Commands.literal("ess")
				.then(Commands.literal("help")
					.executes(context -> {
						String helpText = String.join("\n",
							"--- Empty Server Stopper Commands ---",
							"/ess help - Shows this help message.",
							"/ess reload - Reloads the config and re-evaluates shutdown state. (OP)",
							"/ess time - Shows remaining time before shutdown.",
							"/ess time set <minutes> - Sets and persists a new shutdown delay. (OP)",
							"/ess enable - Enables the mod. (OP)", "/ess disable - Disables the mod and stops the timer. (OP)",
							"/ess restart - Re-evaluates and restarts the countdown. (OP)",
							"/ess min_players - Shows the current minimum player threshold.",
							"/ess min_players set <n> - Sets and persists a new threshold. (OP)");

						sendFeedback(context, helpText, ChatFormatting.AQUA);

						return Command.SINGLE_SUCCESS;
					})
				)
				.then(Commands.literal("reload").requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
					.executes(context -> {
						EmptyServerStopperConfigManager.loadConfig(false);
						updateVar();

						countPlayers(minecraftServer);

						sendFeedback(context, "Plugin has been reloaded.", ChatFormatting.LIGHT_PURPLE);

						return Command.SINGLE_SUCCESS;
					})
				)
				.then(Commands.literal("time")
					.requires(source -> {
						if (playersCanAskShutdownTime) {
							return true;
						} else {
							return source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
						}
					})
					.executes(context -> {
						String message;

						if (timer == null) {
							message = "No shutdown is currently scheduled.";
						} else {
							long remainingMs = expectedShutdownTime - System.currentTimeMillis();

							if (remainingMs <= 0) {
								message = "Shutdown is imminent.";
							} else {
								long minutes = (remainingMs / 1000) / 60;
								long seconds = (remainingMs / 1000) % 60;
								message = String.format("Time remaining before shutdown: %d min %d sec.", minutes, seconds);
							}
						}

						sendFeedback(context, message);

						return Command.SINGLE_SUCCESS;
					})
					.then(Commands.literal("set")
						.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
						.then(Commands.argument("minutes", IntegerArgumentType.integer(1))
							.executes(context -> {
								int minutes = IntegerArgumentType.getInteger(context, "minutes");

								shutdownTimeMinutes = minutes;
								shutdownTimeMs = 1000L * 60L * minutes;

								EmptyServerStopperConfigManager.getConfig().shutdownTimeMinutes = minutes;
								EmptyServerStopperConfigManager.saveConfig();

								startShutdownTimer();

								sendFeedback(context, "Shutdown time has been set to " + minutes + " minute(s) from now.");

								return Command.SINGLE_SUCCESS;
							})
						)
					)
				)
				.then(Commands.literal("enable")
					.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
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
						return Command.SINGLE_SUCCESS;
					})
				)
				.then(Commands.literal("disable")
					.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
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
						return Command.SINGLE_SUCCESS;
					})
				)
				.then(Commands.literal("restart")
					.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
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
				.then(Commands.literal("min_players")
					.requires(source -> {
						if (playersCanAskMinPlayer) {
							return true;
						} else {
							return source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
						}
					})
					.executes(context -> {
							sendFeedback(context, "The number of minimum player before shutdown is " + minimumPlayersBeforeShutdown + ".");
							return Command.SINGLE_SUCCESS;
						}
					)
					.then(Commands.literal("set")
						.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
						.then(Commands.argument("minimum_players", IntegerArgumentType.integer(1))
							.executes(context -> {
								int minimum_players = IntegerArgumentType.getInteger(context, "minimum_players");
								EmptyServerStopperConfigManager.getConfig().minimumPlayersBeforeShutdown = minimum_players;
								EmptyServerStopperConfigManager.saveConfig();

								minimumPlayersBeforeShutdown = minimum_players;

								sendFeedback(context, "The minimum players number before shutdown has been set to " + minimum_players + ".");

								countPlayers(minecraftServer);

								return Command.SINGLE_SUCCESS;
							})
						)
					)
				)
			)
		);
	}

	private void updateVar() {
		enabled = EmptyServerStopperConfigManager.getConfig().enabled;
		shutdownTimeMinutes = EmptyServerStopperConfigManager.getConfig().shutdownTimeMinutes;
		timerOnLaunch = EmptyServerStopperConfigManager.getConfig().timerOnLaunch;
		shutdownTimeMs = 1000L * 60L * shutdownTimeMinutes;
		minimumPlayersBeforeShutdown = EmptyServerStopperConfigManager.getConfig().minimumPlayersBeforeShutdown;
		playersCanAskShutdownTime = EmptyServerStopperConfigManager.getConfig().playersCanAskShutdownTime;
		playersCanAskMinPlayer = EmptyServerStopperConfigManager.getConfig().playersCanAskMinPlayer;
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

	private void onPlayerJoining(ServerGamePacketListenerImpl handler, PacketSender sender, MinecraftServer server) {
		countPlayers(server, 1);
	}

	private void onPlayerDisconnecting(ServerGamePacketListenerImpl handler, MinecraftServer server) {
		countPlayers(server, -1);
	}

	public void countPlayers(MinecraftServer server) {
		countPlayers(server, 0);
	}

	private void countPlayers(MinecraftServer server, int delta) {
		if (!enabled) return;

		int count = server.getPlayerCount() + delta;

		if (count < minimumPlayersBeforeShutdown) {
			if (timer == null) startShutdownTimer();
		} else {
			if (timer != null) cancelShutdown();
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

		if (minecraftServer != null && minecraftServer.getPlayerCount() > 0) {
			minecraftServer.getPlayerList().broadcastSystemMessage(
				Component.literal(messageContent)
					.withStyle(ChatFormatting.GOLD), false);
		} else {
			LOGGER.info("[{}] {}", MOD_NAME, messageContent);
		}

		if (shutdownTimeMinutes > 1) {
			timer.schedule(new TimerTask() {
				@Override
				public void run() {
					if (minecraftServer != null && timer != null) {
						minecraftServer.getPlayerList().broadcastSystemMessage(
							Component.literal("Server will shutdown in 1 minute!")
								.withStyle(ChatFormatting.RED), false);
					}
				}
			}, (shutdownTimeMs - 60000));
		}

		timer.schedule(task, shutdownTimeMs);
		expectedShutdownTime = System.currentTimeMillis() + shutdownTimeMs;
	}

	private void sendFeedback(CommandContext<CommandSourceStack> context, String message) {
		sendFeedback(context, message, ChatFormatting.RESET);
	}

	private void sendFeedback(CommandContext<CommandSourceStack> context, String message, ChatFormatting color) {
		if (context.getSource().getEntity() == null) {
			LOGGER.info("[{}] {}", MOD_NAME, message);
		} else {
			var prefix = Component.literal("[" + MOD_NAME + "] ").withStyle(ChatFormatting.GREEN);

			var content = Component.literal(message).withStyle(color);

			var fullMessage = Component.literal("").append(prefix).append(content);

			context.getSource().sendSuccess(() -> fullMessage, false);
		}
	}

	private void cancelShutdown() {
		if (timer != null) {
			stopTimer();
			String cancelMsg = "Shutdown cancelled. Player threshold reached!";

			if (minecraftServer != null && minecraftServer.getPlayerCount() > 0) {
				minecraftServer.getPlayerList().broadcastSystemMessage(
					Component.literal(cancelMsg)
						.withStyle(ChatFormatting.GREEN), false);
			} else {
				LOGGER.info("[{}] {}", MOD_NAME, cancelMsg);
			}
		}
	}
}