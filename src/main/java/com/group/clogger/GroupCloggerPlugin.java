package com.group.clogger;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.task.Schedule;

import java.time.temporal.ChronoUnit;

@Slf4j
@PluginDescriptor(
	name = "Group Clogger Plugin"
)
public class GroupCloggerPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private GroupCloggerPluginConfig config;

	@Inject
	private DataManager dataManager;

	private static final int SECONDS_BETWEEN_UPLOADS = 1;

	@Override
	protected void startUp() throws Exception
	{
		log.info("Group Clogger Plugin started!");
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("Group Clogger Plugin  stopped!");
	}

	@Schedule(
			period = SECONDS_BETWEEN_UPLOADS,
			unit = ChronoUnit.SECONDS,
			asynchronous = true
	)
	public void submitToApi() {
		dataManager.submitToApi();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if (gameStateChanged.getGameState() == GameState.LOGGED_IN)
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "I'm ripping my penis off " + config.greeting(), null);
			dataManager.submitToApi();
		}
	}

	@Provides
	GroupCloggerPluginConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GroupCloggerPluginConfig.class);
	}
}
