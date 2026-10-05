package com.vincent.devops.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vincent.devops.project.domain.ProjectEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProjectMapper extends BaseMapper<ProjectEntity> {
}
