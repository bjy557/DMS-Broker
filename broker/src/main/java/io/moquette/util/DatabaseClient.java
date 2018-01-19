package io.moquette.util;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by nexusz99 on 16. 8. 11.
 */
public class DatabaseClient {

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseClient.class);

    public static void record_client_connected(String client_id) {
        String broker_id = System.getenv("MQTT_CLUSTER_BROKER_ID");

        JSONObject request_body = new JSONObject();
        request_body.put("client_mqtt_id", client_id);
        request_body.put("broker_id", broker_id);
        request_body.put("last_connected", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()));

        try {
            HttpResponse<String> response = Unirest.post("http://13.124.40.73:8080/client")
                    .header("Content-Type", "application/json")
                    .body(request_body)
                    .asString();
            if (response.getStatus() != 200) {
                LOG.error("API call failed ["+response.getStatus()+"] " + response.getBody());
            }
        } catch(UnirestException e) {
            e.printStackTrace();
        }
    }

}
