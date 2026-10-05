package com.vincent.devops.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vincent.devops.auth.domain.UserProjectEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户项目访问授权数据访问接口。
 */
@Mapper
public interface UserProjectMapper extends BaseMapper<UserProjectEntity> {
}
