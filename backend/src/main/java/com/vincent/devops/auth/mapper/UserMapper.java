package com.vincent.devops.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vincent.devops.auth.domain.UserEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 平台用户数据访问接口。
 */
@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {
}
