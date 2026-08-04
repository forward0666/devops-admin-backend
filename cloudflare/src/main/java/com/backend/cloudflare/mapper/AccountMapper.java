package com.backend.cloudflare.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.backend.cloudflare.entity.AccountEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AccountMapper extends BaseMapper<AccountEntity> {

    List<AccountEntity> findAll();

    AccountEntity findById(Long id);

    AccountEntity findByName(String name);

    String findApiKeyById(Long id);

    int insert(AccountEntity entity);

    int update(AccountEntity entity);

    int deleteById(Long id);
}