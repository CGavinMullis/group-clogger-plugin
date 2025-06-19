package com.group.clogger;

import lombok.extern.slf4j.Slf4j;
import com.google.gson.Gson;
import okhttp3.*;

import net.runelite.api.Client;
import net.runelite.api.WorldType;
import net.runelite.client.RuneLiteProperties;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class DataManager {

    @Inject
    Client client;

    @Inject
    private Gson gson;
    @Inject
    private OkHttpClient okHttpClient;

    private static final String PUBLIC_BASE_URL = "http://192.168.51.113:5000/webhook";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final String USER_AGENT = "GroupClogger/0.0.0 " + "RuneLite/" + RuneLiteProperties.getVersion();

    public void submitToApi() {
        if (client.getLocalPlayer() == null || client.getLocalPlayer().getName() == null)    return;
        String playerName = client.getLocalPlayer().getName();
        Map<String, Object> updates = new HashMap<>();
        Map<String, Object> player = new HashMap<>();
        player.put("playerName", playerName);
        updates.put("payload_json", player);
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
