package com.backend.manage.model;

import java.time.LocalDateTime;

/**
 * 菜单实体类
 * 用于表示系统菜单信息，支持树形结构
 */
public class Menu {
    private Long id;
    private String name;
    private String path;
    private String icon;
    private String type; // menu 或 button
    private Integer sort;
    private String status; // active 或 inactive
    private Long parentId;
    private String parentName; // 父级菜单名称，不存储在数据库
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Constructors
    public Menu() {}

    public Menu(String name, String path, String icon, String type, Integer sort, String status, Long parentId) {
        this.name = name;
        this.path = path;
        this.icon = icon;
        this.type = type;
        this.sort = sort;
        this.status = status;
        this.parentId = parentId;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Integer getSort() {
        return sort;
    }

    public void setSort(Integer sort) {
        this.sort = sort;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getParentName() {
        return parentName;
    }

    public void setParentName(String parentName) {
        this.parentName = parentName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
