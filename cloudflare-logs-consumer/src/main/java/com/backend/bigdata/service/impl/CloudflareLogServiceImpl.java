package com.backend.bigdata.service.impl;

import com.backend.bigdata.mapper.CloudflareLogsMapper;
import com.backend.bigdata.service.CloudflareLogService;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import network.CloudflareLogUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CloudflareLogServiceImpl extends CloudflareLogService {
    @Autowired
    private CloudflareLogsMapper cloudflareLogsMapper;

    public void consume(List<String> messages) {
        List<Map<String, Object>> batch = new ArrayList();
        ObjectMapper objectMapper = new ObjectMapper();

        for(String message : messages) {
            try {
                if (message != null && message.trim().startsWith("{") && message.trim().endsWith("}")) {
                    Map<String, Object> original = (Map)objectMapper.readValue(message, Map.class);
                    Map<String, Object> flattened = CloudflareLogUtils.flattenAndBuildParams(original);
                    batch.add(flattened);
                }
            } catch (Exception var8) {
            }
        }

        if (!batch.isEmpty()) {
            this.cloudflareLogsMapper.batchInsertCloudflareHttpLogsForU8App(batch);
        }

    }
}
