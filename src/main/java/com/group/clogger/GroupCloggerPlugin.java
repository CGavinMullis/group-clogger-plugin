package com.group.clogger;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.task.Schedule;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.client.game.ItemManager;

import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.HashMap;
import java.util.Map;
import java.util.*;

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
	private static final int COLLECTION_LOG_INVENTORYID = 620;

	@Inject
	ClientThread clientThread;

	@Override
	protected void startUp() throws Exception
	{
		log.info("Group Clogger Plugin started!");
		clientThread.invokeLater(() -> {
			dataManager.initCollectionLog();
		});
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
	public void onItemContainerChanged(ItemContainerChanged event) {
		String playerName = client.getLocalPlayer().getName();
		final int id = event.getContainerId();
		ItemContainer container = event.getItemContainer();
		if (id == COLLECTION_LOG_INVENTORYID) {
			dataManager.updateCollection();
			submitToApi();
		}
	}

	@Provides
	GroupCloggerPluginConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GroupCloggerPluginConfig.class);
	}
}
