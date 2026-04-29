package com.backend.login.vo.login;

import com.backend.login.entity.system.UserEntity;
import com.backend.login.vo.system.UserVo;

public record LoginVo(
        String token,
        UserVo user
) {
    public static LoginVo fromEntity(String token, UserEntity userEntity) {
        return new LoginVo(token, UserVo.fromUser(userEntity));
    }
}
