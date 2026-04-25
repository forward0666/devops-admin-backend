package com.backend.user.vo.login;

import com.backend.user.entity.system.UserEntity;
import com.backend.user.vo.system.UserVo;
import com.backend.user.vo.system.UserVo;

public record LoginVo(
        String token,
        UserVo user
) {
    public static LoginVo fromEntity(String token, UserEntity userEntity) {
        return new LoginVo(token, UserVo.fromUser(userEntity));
    }
}
