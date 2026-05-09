package com.apk.claw.android.agent.langchain.http;

import com.apk.claw.android.utils.XLog;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;

public class ImageUrlInterceptor implements Interceptor {

    private static final String TAG = "ImageUrlInterceptor";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final Gson GSON = new Gson();

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        
        if (request.body() == null) {
            return chain.proceed(request);
        }
        
        Buffer buffer = new Buffer();
        request.body().writeTo(buffer);
        String requestBodyStr = buffer.readUtf8();
        
        String modifiedBody = transformImageUrlFormat(requestBodyStr);
        
        RequestBody newBody = RequestBody.create(modifiedBody, JSON);
        Request newRequest = request.newBuilder()
                .method(request.method(), newBody)
                .build();
        
        XLog.d(TAG, "Image URL format transformed");
        
        Response response = chain.proceed(newRequest);
        
        ResponseBody responseBody = response.body();
        String respStr = "";
        if (responseBody != null) {
            MediaType contentType = responseBody.contentType();
            respStr = responseBody.string();
            response = response.newBuilder()
                    .body(ResponseBody.create(contentType, respStr))
                    .build();
        }
        
        return response;
    }

    private String transformImageUrlFormat(String requestBody) {
        try {
            JsonObject json = JsonParser.parseString(requestBody).getAsJsonObject();
            
            if (json.has("messages")) {
                JsonArray messages = json.getAsJsonArray("messages");
                for (JsonElement msgElement : messages) {
                    JsonObject msg = msgElement.getAsJsonObject();
                    if (msg.has("content")) {
                        JsonElement contentElement = msg.get("content");
                        if (contentElement.isJsonArray()) {
                            JsonArray contentArray = contentElement.getAsJsonArray();
                            for (JsonElement contentItem : contentArray) {
                                if (contentItem.isJsonObject()) {
                                    JsonObject contentObj = contentItem.getAsJsonObject();
                                    transformContentObject(contentObj);
                                }
                            }
                        } else if (contentElement.isJsonObject()) {
                            transformContentObject(contentElement.getAsJsonObject());
                        }
                    }
                }
            }
            
            return GSON.toJson(json);
        } catch (Exception e) {
            XLog.w(TAG, "Failed to transform image URL format: " + e.getMessage());
            return requestBody;
        }
    }

    private void transformContentObject(JsonObject contentObj) {
        if (contentObj.has("type") && contentObj.get("type").getAsString().equals("image_url")) {
            if (contentObj.has("image_url")) {
                JsonElement imageUrlElement = contentObj.get("image_url");
                if (imageUrlElement.isJsonObject()) {
                    JsonObject imageUrlObj = imageUrlElement.getAsJsonObject();
                    if (imageUrlObj.has("url")) {
                        String url = imageUrlObj.get("url").getAsString();
                        if (!url.startsWith("data:image/jpeg;base64,")) {
                            if (url.startsWith("data:")) {
                                imageUrlObj.addProperty("url", url);
                            }
                        }
                    }
                }
            }
        } else if (contentObj.has("image_url")) {
            JsonElement imageUrlElement = contentObj.get("image_url");
            String url = "";
            if (imageUrlElement.isJsonObject()) {
                JsonObject imageUrlObj = imageUrlElement.getAsJsonObject();
                if (imageUrlObj.has("url")) {
                    url = imageUrlObj.get("url").getAsString();
                }
            } else if (imageUrlElement.isJsonPrimitive()) {
                url = imageUrlElement.getAsString();
            }
            
            JsonObject newImageUrl = new JsonObject();
            newImageUrl.addProperty("url", url);
            contentObj.remove("image_url");
            contentObj.add("image_url", newImageUrl);
            contentObj.addProperty("type", "image_url");
        }
    }
}