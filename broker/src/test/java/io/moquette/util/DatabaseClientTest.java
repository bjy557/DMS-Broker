package io.moquette.util;

import org.junit.Test;

/**
 * Created by nexusz99 on 16. 8. 11.
 */
public class DatabaseClientTest {


    @Test
    public void test_record_client_connected() {
        DatabaseClient.record_client_connected("client1");
    }
}
