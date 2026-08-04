package com.backend.task.service;

import com.backend.task.entity.TaskEntity;
import com.backend.task.mapper.TaskMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Task CRUD service - manages task records in MySQL.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskMapper taskMapper;
    private final ObjectMapper objectMapper;

    public List<Map<String, Object>> listTasks() {
        var tasks = taskMapper.selectAllOrderByCreatedDesc();
        List<Map<String, Object>> result = new ArrayList<>();
        for (var task : tasks) {
            result.add(toMap(task));
        }
        return result;
    }

    public Map<String, Object> getTask(Long id) {
        var task = taskMapper.selectById(id);
        if (task == null) return null;
        return toMap(task);
    }

    public TaskEntity createTask(Map<String, Object> body) {
        String name = (String) body.get("name");
        String type = (String) body.get("type");
        String cron = (String) body.get("cron");

        if (name == null || name.trim().isEmpty()) throw new RuntimeException("name is required");
        if (type == null || type.trim().isEmpty()) throw new RuntimeException("type is required");
        if (cron == null || cron.trim().isEmpty()) throw new RuntimeException("cron is required");

        var task = new TaskEntity();
        task.setName(name.trim());
        task.setType(type.trim());
        task.setCron(cron.trim());
        task.setEnabled(Boolean.TRUE.equals(body.getOrDefault("enabled", true)));
        task.setDescription((String) body.getOrDefault("description", ""));
        task.setConfig(toJson(body.getOrDefault("config", Map.of())));
        task.setCreatedAt(new Date());
        task.setUpdatedAt(new Date());
        taskMapper.insert(task);
        return task;
    }

    public void updateTask(Long id, Map<String, Object> body) {
        var task = taskMapper.selectById(id);
        if (task == null) throw new RuntimeException("Task not found");

        if (body.containsKey("name")) task.setName(((String) body.get("name")).trim());
        if (body.containsKey("type")) task.setType((String) body.get("type"));
        if (body.containsKey("cron")) task.setCron((String) body.get("cron"));
        if (body.containsKey("enabled")) task.setEnabled(Boolean.TRUE.equals(body.get("enabled")));
        if (body.containsKey("description")) task.setDescription((String) body.get("description"));
        if (body.containsKey("config")) task.setConfig(toJson(body.get("config")));
        task.setUpdatedAt(new Date());
        taskMapper.updateById(task);
    }

    public void deleteTask(Long id) {
        var task = taskMapper.selectById(id);
        if (task == null) throw new RuntimeException("Task not found");
        taskMapper.deleteById(id);
    }

    // ======================== Helpers ========================

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(TaskEntity task) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", task.getId());
        map.put("name", task.getName());
        map.put("type", task.getType());
        map.put("cron", task.getCron());
        map.put("enabled", task.getEnabled());
        map.put("description", task.getDescription());
        map.put("lastStatus", task.getLastStatus());
        map.put("lastRunAt", task.getLastRunAt());
        map.put("createdAt", task.getCreatedAt());
        map.put("updatedAt", task.getUpdatedAt());
        // Parse config JSON
        String configStr = task.getConfig();
        if (configStr != null && !configStr.isEmpty()) {
            try {
                map.put("config", objectMapper.readValue(configStr, Map.class));
            } catch (Exception e) {
                map.put("config", Map.of());
            }
        } else {
            map.put("config", Map.of());
        }
        return map;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}