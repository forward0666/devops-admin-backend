package com.backend.manage.vo.login;

import com.backend.manage.entity.system.UserEntity;
import com.backend.manage.vo.system.UserVo;

public record LoginVo(
        String token,
        UserVo user
) {
    public static LoginVo fromEntity(String token, UserEntity userEntity) {
        return new LoginVo(token, UserVo.fromUser(userEntity));
    }
}
