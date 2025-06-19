package com.group.clogger;

import com.google.common.collect.ImmutableList;
import lombok.extern.slf4j.Slf4j;
import com.google.gson.Gson;
import net.runelite.client.task.Schedule;
import okhttp3.*;

import net.runelite.api.EnumComposition;
import net.runelite.api.ItemComposition;
import net.runelite.api.StructComposition;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.Text;
import org.apache.commons.lang3.StringUtils;
import net.runelite.api.Client;
import net.runelite.api.WorldType;
import net.runelite.client.RuneLiteProperties;

import javax.inject.Inject;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class DataManager {

    @Inject
    ItemManager itemManager;

    @Inject
    Client client;

    @Inject
    private Gson gson;
    @Inject
    private OkHttpClient okHttpClient;

    private static final String PUBLIC_BASE_URL = "http://192.168.51.113:5000/webhook";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final String USER_AGENT = "GroupClogger/0.0.0 " + "RuneLite/" + RuneLiteProperties.getVersion();

    private static final int collectionLogTabVarbit = 6905;
    private static final int collectionLogPageVarbit = 6906;
    static final Pattern COLLECTION_LOG_COUNT_PATTERN = Pattern.compile(".+:(.+)");
    static Map<String, Set<Integer>> pageItems;
    static Map<Integer, Map<Integer, String>> pageNameLookup;
    private static final List<Integer> COLLECTION_LOG_TAB_STRUCT_IDS = ImmutableList.of(
            471, // Bosses
            472, // Raids
            473, // Clues
            474, // Minigames
            475  // Other
    );
    private static final int COLLECTION_LOG_PAGE_NAME_PARAM_ID = 689;
    private static final int COLLECTION_LOG_TAB_ENUM_PARAM_ID = 683;
    private static final int COLLECTION_LOG_PAGE_ITEMS_ENUM_PARAM_ID = 690;
    private static final int SECONDS_BETWEEN_UPLOADS = 1;

    Map<String, Object> coll = new HashMap<>();

    public void initCollectionLog() {
        // NOTE: varbit 6905 gives us the selected collection log tab index and 6906 is the selected page index.
        // In here we build a lookup map which will give us the page name with the tab index and the page index.
        // This should be better than pulling the page name from the widget since that value can be changed by
        // other runelite plugins.
        // We also create a lookup of the item ids to the page name which should be better than using the item id
        // in the collection log window as these will match the ids in the container state change.
        pageItems = new HashMap<>();
        pageNameLookup = new HashMap<>();
        int tabIdx = 0;
        for (Integer structId : COLLECTION_LOG_TAB_STRUCT_IDS) {
            StructComposition tabStruct = client.getStructComposition(structId);
            int tabEnumId = tabStruct.getIntValue(COLLECTION_LOG_TAB_ENUM_PARAM_ID);
            EnumComposition tabEnum = client.getEnum(tabEnumId);
            Map<Integer, String> pageIdToName = pageNameLookup.computeIfAbsent(tabIdx, k -> new HashMap<>());

            int pageIdx = 0;
            for (Integer pageStructId : tabEnum.getIntVals()) {
                StructComposition pageStruct = client.getStructComposition(pageStructId);
                String pageName = pageStruct.getStringValue(COLLECTION_LOG_PAGE_NAME_PARAM_ID);
                int pageItemsEnumId = pageStruct.getIntValue(COLLECTION_LOG_PAGE_ITEMS_ENUM_PARAM_ID);
                EnumComposition pageItemsEnum = client.getEnum(pageItemsEnumId);

                pageIdToName.put(pageIdx, pageName);
                Set<Integer> items = pageItems.computeIfAbsent(pageName, k -> new HashSet<>());

                for (Integer pageItemId : pageItemsEnum.getIntVals()) {
                    ItemComposition itemComposition = itemManager.getItemComposition(pageItemId);
                    items.add(itemComposition.getId());
                }

                ++pageIdx;
            }

            ++tabIdx;
        }

    }

    public void updateCollection() {
        Widget collectionLogHeader = client.getWidget(WidgetInfo.COLLECTION_LOG_ENTRY_HEADER);
        Map<String, Object> collectionLogs = new HashMap<>();
        if (collectionLogHeader == null || collectionLogHeader.isHidden()) return;
        Widget[] collectionLogHeaderChildren = collectionLogHeader.getChildren();
        if (collectionLogHeaderChildren == null || collectionLogHeaderChildren.length == 0) return;

        // Get the completion count information from the lines in the collection log header
        List<Integer> completionCounts = new ArrayList<>();
        for (int i = 2; i < collectionLogHeaderChildren.length; ++i) {
            String text = Text.removeTags(collectionLogHeaderChildren[i].getText());
            log.info("text: {}", text);
            Matcher matcher = COLLECTION_LOG_COUNT_PATTERN.matcher(text);
            if (matcher.find()) {
                try {
                    Integer count = Integer.valueOf(matcher.group(1).trim());
                    completionCounts.add(count);
                    log.info("count: {}", count);
                    collectionLogs.put(text, count);
                } catch(Exception ignored) {}
            }
        }

        int tabIdx = client.getVarbitValue(collectionLogTabVarbit);
        int pageIdx = client.getVarbitValue(collectionLogPageVarbit);

        String pageName = getPageName(tabIdx, pageIdx);
        if (!StringUtils.isBlank(pageName)) {
            // Sending the tab index just in case the page name is not unique across them
            log.info("{}", pageName);
        }
        coll = collectionLogs;
    }

    private String getPageName(int tabIdx, int pageIdx) {
        Map<Integer, String> x = pageNameLookup.get(tabIdx);
        if (x != null) return x.get(pageIdx);
        return null;
    }

    public void submitToApi() {
        if (client.getLocalPlayer() == null || client.getLocalPlayer().getName() == null)    return;
        String playerName = client.getLocalPlayer().getName();
        Map<String, Object> updates = new HashMap<>();
        Map<String, Object> player = new HashMap<>();
        Map<String, Object> payload = new HashMap<>();
        player.put("playerName", playerName);
        payload.put("player", player);
        payload.put("collectionLogs", coll);
        updates.put("payload_json", payload);

        try {
            RequestBody body = RequestBody.create(JSON, gson.toJson(updates));
            log.info("{}", gson.toJson(updates));

            MultipartBody.Builder requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("payload_json", gson.toJson(updates));

            Request request = new Request.Builder()
                    .url(PUBLIC_BASE_URL)
                    .header("Authorization", "123123123")
                    .header("User-Agent", USER_AGENT)
                    .post(requestBody.build())
                    .build();
            Call call = okHttpClient.newCall(request);

            try (Response response = call.execute()) {
                if(!response.isSuccessful()) {
                    log.error(response.body().string());
                }
                log.info("Sent request");
            }
        } catch (Exception _error) {
            log.error(_error.toString());
        }
    }
}
