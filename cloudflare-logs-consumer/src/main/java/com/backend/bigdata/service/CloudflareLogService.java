package com.backend.bigdata.service;

import java.util.List;

public abstract class CloudflareLogService {
    public abstract void consume(List<String> messages);
}

