package com.backend.task.controller;

import com.backend.task.entity.TaskEntity;
import com.backend.task.service.TaskExecutorService;
import com.backend.task.service.TaskSchedulerService;
import com.backend.task.service.TaskService;
import com.backend.utils.dto.ApiResponseDto;
import com.backend.utils.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Task CRUD controller.
 * All exceptions are handled by GlobalExceptionHandler (utils-core).
 */
@Slf4j
@RestController
@RequestMapping("/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final TaskExecutorService taskExecutorService;
    private final TaskSchedulerService taskSchedulerService;

    @GetMapping
    public ResponseEntity<ApiResponseDto<?>> listTasks() {
        var tasks = taskService.listTasks();
        return ResponseEntity.ok(ApiResponseDto.success(tasks));
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<ApiResponseDto<?>> getTask(@PathVariable Long taskId) {
        var task = taskService.getTask(taskId);
        if (task == null) {
            return ResponseEntity.ok(ApiResponseDto.error(404, "Task not found"));
        }
        return ResponseEntity.ok(ApiResponseDto.success(task));
    }

    @PostMapping
    public ResponseEntity<ApiResponseDto<?>> createTask(@RequestBody Map<String, Object> body) {
        var task = taskService.createTask(body);
        taskSchedulerService.reloadTasks();
        return ResponseEntity.ok(ApiResponseDto.success(Map.of("id", task.getId()), "Task created"));
    }

    @PutMapping("/{taskId}")
    public ResponseEntity<ApiResponseDto<?>> updateTask(@PathVariable Long taskId,
                                                        @RequestBody Map<String, Object> body) {
        taskService.updateTask(taskId, body);
        taskSchedulerService.reloadTasks();
        return ResponseEntity.ok(ApiResponseDto.success(null, "Task updated"));
    }

    @PatchMapping("/{taskId}")
    public ResponseEntity<ApiResponseDto<?>> patchTask(@PathVariable Long taskId,
                                                       @RequestBody Map<String, Object> body) {
        taskService.updateTask(taskId, body);
        taskSchedulerService.reloadTasks();
        return ResponseEntity.ok(ApiResponseDto.success(null, "Task updated"));
    }

    @DeleteMapping("/{taskId}")
    public ResponseEntity<ApiResponseDto<?>> deleteTask(@PathVariable Long taskId) {
        taskService.deleteTask(taskId);
        taskSchedulerService.reloadTasks();
        return ResponseEntity.ok(ApiResponseDto.success(null, "Task deleted"));
    }

    @PostMapping("/{taskId}/run")
    public ResponseEntity<ApiResponseDto<?>> runTask(@PathVariable Long taskId) {
        var task = taskService.getTask(taskId);
        if (task == null) {
            return ResponseEntity.ok(ApiResponseDto.error(404, "Task not found"));
        }
        var enabled = (Boolean) task.get("enabled");
        if (!Boolean.TRUE.equals(enabled)) {
            return ResponseEntity.ok(ApiResponseDto.error(400, "Task is disabled"));
        }
        // Fetch entity for execution
        var taskEntity = new TaskEntity();
        taskEntity.setId(taskId);
        taskEntity.setName((String) task.get("name"));
        taskEntity.setType((String) task.get("type"));
        taskEntity.setConfig("{}");
        // Trigger async execution
        taskExecutorService.executeTask(taskEntity);
        return ResponseEntity.ok(ApiResponseDto.success(
                Map.of("taskId", taskId), "Task '" + task.get("name") + "' triggered"));
    }

    @PostMapping("/reload")
    public ResponseEntity<ApiResponseDto<?>> reloadScheduler() {
        taskSchedulerService.reloadTasks();
        return ResponseEntity.ok(ApiResponseDto.success(null, "Scheduler reloaded"));
    }
}