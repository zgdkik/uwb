package com.zhilutec.services;


import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import javax.websocket.Session;
import java.io.IOException;
import java.util.List;

public interface IKafkaService {


    List<JSONObject> str2Json(List<ConsumerRecord<String, String>> records);

    void wsSendMsgBase(Session wsSession, String rs) throws IOException;

    void wsSendMsgAsync(Session wsSession, String rs) throws IOException;
}
