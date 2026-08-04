package com.backend.cloudflare.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("account")
public class AccountEntity {
    @TableId
    private Long id;
    private String name;
    private String tags;
    private String apiKey;
    private String cfAccountId;
}