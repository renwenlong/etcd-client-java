package util;
public interface JsonClient {

    /**
     * 转换成object
     *
     * @param json
     * @return
     */
    <T> T fromJson(String json, Class<T> classOfT);
}

