package com.vincent.devops.project;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisMapperBuilderAssistant;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.vincent.devops.common.ResourceNotFoundException;
import com.vincent.devops.environment.domain.EnvironmentEntity;
import com.vincent.devops.environment.mapper.EnvironmentMapper;
import com.vincent.devops.microservice.mapper.MicroserviceMapper;
import com.vincent.devops.microservice.domain.MicroserviceEntity;
import com.vincent.devops.project.domain.ProjectEntity;
import com.vincent.devops.project.dto.CreateProjectRequest;
import com.vincent.devops.project.dto.UpdateProjectRequest;
import com.vincent.devops.project.dto.UpdateProjectStatusRequest;
import com.vincent.devops.project.mapper.ProjectMapper;
import com.vincent.devops.project.service.impl.ProjectServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceImplTest {

    @BeforeAll
    static void initializeLambdaMetadata() {
        MybatisMapperBuilderAssistant assistant = new MybatisMapperBuilderAssistant(new MybatisConfiguration(), "project-test");
        TableInfoHelper.initTableInfo(assistant, ProjectEntity.class);
        TableInfoHelper.initTableInfo(assistant, EnvironmentEntity.class);
        TableInfoHelper.initTableInfo(assistant, MicroserviceEntity.class);
    }

    @Mock
    private ProjectMapper projectMapper;

    @Mock
    private EnvironmentMapper environmentMapper;

    @Mock
    private MicroserviceMapper microserviceMapper;

    @InjectMocks
    private ProjectServiceImpl projectService;

    @Test
    void shouldCreateActiveProject() {
        when(projectMapper.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> {
            ProjectEntity entity = invocation.getArgument(0);
            entity.setId(1L);
            return 1;
        }).when(projectMapper).insert(any(ProjectEntity.class));

        ProjectEntity result = projectService.create(
                new CreateProjectRequest("demo-project", "演示项目", "用于联调")
        );

        assertEquals(1L, result.getId());
        assertEquals("ACTIVE", result.getStatus());
    }

    @Test
    void shouldRejectDuplicateProjectCode() {
        when(projectMapper.selectCount(any())).thenReturn(1L);

        assertThrows(DuplicateKeyException.class, () -> projectService.create(
                new CreateProjectRequest("demo-project", "演示项目", null)
        ));
    }

    @Test
    void shouldUpdateProjectDetails() {
        ProjectEntity entity = project(1L, "ACTIVE");
        when(projectMapper.selectById(1L)).thenReturn(entity);
        when(projectMapper.updateById(any(ProjectEntity.class))).thenReturn(1);

        ProjectEntity result = projectService.update(
                1L,
                new UpdateProjectRequest("更新后的项目", "更新后的描述")
        );

        assertEquals("更新后的项目", result.getName());
        assertEquals("更新后的描述", result.getDescription());
        verify(projectMapper).updateById(entity);
    }

    @Test
    void shouldDisableProjectIdempotently() {
        ProjectEntity entity = project(1L, "ACTIVE");
        when(projectMapper.selectById(1L)).thenReturn(entity);
        when(projectMapper.updateById(any(ProjectEntity.class))).thenReturn(1);

        ProjectEntity result = projectService.updateStatus(
                1L,
                new UpdateProjectStatusRequest("DISABLED")
        );

        assertEquals("DISABLED", result.getStatus());
        verify(projectMapper).updateById(entity);

        when(projectMapper.selectById(1L)).thenReturn(entity);
        projectService.updateStatus(1L, new UpdateProjectStatusRequest("DISABLED"));
        verify(projectMapper, org.mockito.Mockito.times(1)).updateById(entity);
    }

    @Test
    void shouldRejectDeletingActiveProject() {
        when(projectMapper.selectById(1L)).thenReturn(project(1L, "ACTIVE"));

        assertThrows(IllegalArgumentException.class, () -> projectService.delete(1L));

        verify(projectMapper, never()).deleteById(anyLong());
    }

    @Test
    void shouldLogicallyDeleteDisabledProject() {
        ProjectEntity entity = project(1L, "DISABLED");
        when(projectMapper.selectById(1L)).thenReturn(entity);
        when(environmentMapper.selectCount(any())).thenReturn(0L);
        when(microserviceMapper.selectCount(any())).thenReturn(0L);
        when(projectMapper.updateById(any(ProjectEntity.class))).thenReturn(1);
        when(projectMapper.deleteById(1L)).thenReturn(1);

        projectService.delete(1L);

        verify(projectMapper).updateById(entity);
        verify(projectMapper).deleteById(1L);
    }

    @Test
    void shouldRejectMissingProject() {
        when(projectMapper.selectById(99L)).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> projectService.get(99L));
    }

    @Test
    void shouldBatchDeleteOnlyAfterAllProjectsPassValidation() {
        List<Long> ids = List.of(1L, 2L);
        when(projectMapper.selectByIds(ids)).thenReturn(List.of(project(1L, "DISABLED"), project(2L, "DISABLED")));
        when(environmentMapper.selectCount(any())).thenReturn(0L);
        when(microserviceMapper.selectCount(any())).thenReturn(0L);

        projectService.deleteBatch(ids);

        verify(projectMapper).update(any(), any());
        verify(projectMapper).deleteByIds(ids);
    }

    @Test
    void shouldRejectBatchDeleteWhenOneProjectIsActive() {
        List<Long> ids = List.of(1L, 2L);
        when(projectMapper.selectByIds(ids)).thenReturn(List.of(project(1L, "DISABLED"), project(2L, "ACTIVE")));

        assertThrows(IllegalArgumentException.class, () -> projectService.deleteBatch(ids));

        verify(projectMapper, never()).update(any(), any());
        verify(projectMapper, never()).deleteByIds(ids);
    }

    private ProjectEntity project(Long id, String status) {
        ProjectEntity entity = new ProjectEntity();
        entity.setId(id);
        entity.setCode("demo-project");
        entity.setName("演示项目");
        entity.setStatus(status);
        entity.setDeleted(false);
        return entity;
    }
}
