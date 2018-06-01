package util.Impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.JsonClient;

public class GsonClient implements JsonClient {

    private static Logger logger = LoggerFactory.getLogger(GsonClient.class);
    private GsonBuilder gsonBuilder = new GsonBuilder();
    private Gson gson = null;

    public <T> T fromJson(String json, Class<T> classOfT) {
        if (gson == null){
            init();
        }
        return gson.fromJson(json,classOfT);
    }

    private void init(){
        gsonBuilder.disableHtmlEscaping();
        gsonBuilder.serializeSpecialFloatingPointValues();
        gson = gsonBuilder.create();
    }
}
