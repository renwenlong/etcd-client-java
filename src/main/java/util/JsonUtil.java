
package util;

import util.Impl.GsonClient;

public class JsonUtil {

    private static GsonClient gson = new GsonClient();

    private enum Type{
        GSON,JSONOBJECT;
    }
    public static JsonClient getClient(){
        return getClient(Type.GSON);
    }
    public static JsonClient getClient(Type type){
        switch (type){
            case GSON : return gson;
            default: return gson;
        }
    }

}