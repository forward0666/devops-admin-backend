package com.backend.login.vo;

import com.backend.login.entity.UserEntity;
import com.backend.login.vo.UserVo;

public record LoginVo(
        String token,
        UserVo user
) {
    public static LoginVo fromEntity(String token, UserEntity userEntity) {
        return new LoginVo(token, UserVo.fromUser(userEntity));
    }
}
