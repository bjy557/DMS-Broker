package io.moquette.server;

import com.jayway.jsonpath.JsonPath;
import io.moquette.spi.ClientSession;
import io.moquette.spi.IMessagesStore;
import io.moquette.spi.ISessionsStore;
import io.moquette.parser.proto.messages.AbstractMessage;
import io.moquette.parser.proto.messages.PublishMessage;
import io.moquette.spi.impl.ProtocolProcessor;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.eclipse.paho.client.mqttv3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.List;


public class ClusterClient {

    private class ClusterCallback implements MqttCallback {

        private Server m_server;
        private ProtocolProcessor m_protocolProcessor;
        private ISessionsStore m_sessionStore;
        private IMessagesStore m_messageStore;

        ClusterCallback(Server server) {
            this.m_server = server;
            this.m_protocolProcessor = server.getProtocolProcessor();
            this.m_sessionStore = this.m_protocolProcessor.getsessionsStore();
            this.m_messageStore = this.m_protocolProcessor.getMessageStore();
        }

        @Override
        public void connectionLost(Throwable throwable) {

        }

        @Override
        public void messageArrived(String topic, MqttMessage message) throws Exception {
            String payload = new String(message.getPayload());

            if(topic.compareTo(broadcastTopic) == 0) {
                processBroadCast(payload);
            }
            else if(topic.compareTo(m_brokerId) == 0) {
                processGroupMessage(payload);
            }
            else {
                System.out.println("-------------------------------------------------");
                System.out.println("| Topic:" + topic);
                System.out.println("| Message: " + new String(message.getPayload()));
                System.out.println("-------------------------------------------------");
            }
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {

        }

        private void processBroadCast(String payload) {
            String broker_id = JsonPath.read(payload, "$.broker_id");
            String original_topic = JsonPath.read(payload, "$.topic");
            String msg = JsonPath.read(payload, "$.msg");
            AbstractMessage.QOSType qos = AbstractMessage.QOSType.valueOf(JsonPath.read(payload, "$.qos").toString());

            if(broker_id.compareTo(m_brokerId) == 0) {
                return;
            }
            PublishMessage pmsg = new PublishMessage();
            pmsg.setTopicName(original_topic);
            pmsg.setPayload(ByteBuffer.wrap(msg.getBytes()));
            pmsg.setQos(qos);
            this.m_server.internalPublish(pmsg);
        }

        private void processGroupMessage(String payload) {
            List<String> clients = JsonPath.read(payload, "$.clients");
            String msg = JsonPath.read(payload, "$.msg");
            String topic = JsonPath.read(payload, "$.topic");
            for(String client_id : clients) {
                ClientSession session = this.m_sessionStore.sessionForClient(client_id);

                //TODO If session is null, record disconnected event in database
                if(session == null)
                    continue;

                ByteBuffer msgbuf = ByteBuffer.wrap(msg.getBytes());
                try {
                    this.m_protocolProcessor.directSend(session, topic, AbstractMessage.QOSType.MOST_ONE, msgbuf, false, null);
                } catch(Exception e) {
                    LOG.error("error while sending group message - " + e.getMessage());
                }

            }
        }
    }

    private String m_manageHost;
    private String m_brokerId;
    private final String broadcastTopic = "broadcast";

    private MqttClient m_client;
    private Server m_server;

    private static final Logger LOG = LoggerFactory.getLogger(ClusterClient.class);

    ClusterClient(Server server) {
        this.m_server = server;
    }

    void start(){
        setClusterInfoFromEnv();

        try {
        	System.out.println(this.m_manageHost);
        	System.out.println(this.m_brokerId);
            m_client = new MqttClient(this.m_manageHost, this.m_brokerId);
            MqttConnectOptions opts = new MqttConnectOptions();
            opts.setConnectionTimeout(20);

            m_client.setCallback(new ClusterCallback(this.m_server));
            m_client.connect(opts);

            String[] topics = new String[2];
            topics[0] = this.broadcastTopic;
            topics[1] = this.m_brokerId;

            int[] qos = new int[2];
            qos[0] = AbstractMessage.QOSType.MOST_ONE.ordinal();
            qos[1] = AbstractMessage.QOSType.MOST_ONE.ordinal();
            m_client.subscribe(topics, qos);

        } catch(MqttException me) {
            LOG.error(me.getMessage());
            LOG.error("Broker can't connect to Manager");
            me.printStackTrace();
            System.exit(1);
        }
    }

    void stop() {
        try {
            m_client.disconnect(20);
        } catch (MqttException me) {
            LOG.error(me.getMessage());
        }
    }

    private void setClusterInfoFromEnv() {
        m_manageHost = System.getenv("MQTT_CLUSTER_HOST");
        m_brokerId = System.getenv("MQTT_CLUSTER_BROKER_ID");
    }

    public void sendBroadCast(PublishMessage message) {
        String topic = message.getTopicName();
        ByteBuffer original_payload = message.getPayload();
        AbstractMessage.QOSType qos = message.getQos();

        String json_payload = null;

        JSONObject obj = new JSONObject();
        try {
            obj.put("broker_id", this.m_brokerId);
            obj.put("topic", topic);
            obj.put("msg", new String(original_payload.array()));
            obj.put("qos", qos);
            json_payload = obj.toString();
        } catch(JSONException e) {
            e.printStackTrace();
        }

        MqttMessage msg = new MqttMessage();
        msg.setPayload(json_payload.getBytes());
        msg.setQos(qos.ordinal());

        try {
            this.m_client.publish(this.broadcastTopic, msg);
        } catch(MqttException e){
            e.printStackTrace();
        }
    }
}

