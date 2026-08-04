package com.backend.cloudflare.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Document(collection = "#{@zoneCollectionNameProvider.getCollectionName(#root.accountId)}")
public class CfZoneEntity {

    @Id
    private String mongoId;

    private String zoneId;
    private String accountId;
    private String accountName;
    private String name;
    private String status;
    private Boolean paused;
    private String plan;
    private List<String> nameServers;
    private String sslMode;
    private LocalDateTime syncedAt;
}