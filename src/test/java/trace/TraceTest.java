package trace;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.HashMap;

public class TraceTest {

    public static void main(String[] args) {

        HashMap<String, Object> map = new HashMap<String, Object>();
        map.put("toto", "tata");
        map.put("foo", "bar");
        map.put("bar", 2);

        HashMap<String, Object> map2 = new HashMap<String, Object>();
        map.put("toto", "tata");
        map.put("foo2", "babar");
        map.put("bar2", 3);


        Gson gson = new Gson();
        map.putAll(map2);
        String json = gson.toJson(map);
        System.out.println(String.format("JSON: %s", json));

        Type type = new TypeToken<HashMap<String, Object>>() {}.getType();

        HashMap<String, Object> returnMap = gson.fromJson(json, type);
        System.out.println(type);

    }
}
