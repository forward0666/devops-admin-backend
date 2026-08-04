package com.backend.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.backend.task.entity.TaskEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * MyBatis-Plus mapper for task table.
 */
@Mapper
public interface TaskMapper extends BaseMapper<TaskEntity> {

    @Select("SELECT * FROM task WHERE enabled = 1")
    List<TaskEntity> selectAllEnabled();

    @Select("SELECT * FROM task ORDER BY created_at DESC")
    List<TaskEntity> selectAllOrderByCreatedDesc();
}