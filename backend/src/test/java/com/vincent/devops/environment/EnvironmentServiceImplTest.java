package com.vincent.devops.environment;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisMapperBuilderAssistant;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.vincent.devops.common.ResourceNotFoundException;
import com.vincent.devops.environment.domain.EnvironmentEntity;
import com.vincent.devops.environment.dto.CreateEnvironmentRequest;
import com.vincent.devops.environment.dto.EnvironmentProjectRow;
import com.vincent.devops.environment.dto.UpdateEnvironmentRequest;
import com.vincent.devops.environment.dto.UpdateEnvironmentStatusRequest;
import com.vincent.devops.environment.mapper.EnvironmentMapper;
import com.vincent.devops.microservice.mapper.ServiceEnvironmentMapper;
import com.vincent.devops.microservice.domain.ServiceEnvironmentEntity;
import com.vincent.devops.environment.service.impl.EnvironmentServiceImpl;
import com.vincent.devops.project.domain.ProjectEntity;
import com.vincent.devops.project.service.ProjectService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnvironmentServiceImplTest {

    @BeforeAll
    static void initializeLambdaMetadata() {
        MybatisMapperBuilderAssistant assistant = new MybatisMapperBuilderAssistant(new MybatisConfiguration(), "environment-test");
        TableInfoHelper.initTableInfo(assistant, EnvironmentEntity.class);
        TableInfoHelper.initTableInfo(assistant, ServiceEnvironmentEntity.class);
    }

    @Mock
    private EnvironmentMapper environmentMapper;

    @Mock
    private ServiceEnvironmentMapper serviceEnvironmentMapper;

    @Mock
    private ProjectService projectService;

    @InjectMocks
    private EnvironmentServiceImpl environmentService;

    @Test
    void shouldCreateActiveEnvironmentForActiveProject() {
        when(projectService.get(1L)).thenReturn(project(1L, "ACTIVE"));
        when(environmentMapper.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> {
            EnvironmentEntity entity = invocation.getArgument(0);
            entity.setId(10L);
            return 1;
        }).when(environmentMapper).insert(any(EnvironmentEntity.class));

        EnvironmentEntity result = environmentService.create(
                1L,
                new CreateEnvironmentRequest("test", "测试环境", "devops-test", "local", "联调环境")
        );

        assertEquals(10L, result.getId());
        assertEquals(1L, result.getProjectId());
        assertEquals("ACTIVE", result.getStatus());
    }

    @Test
    void shouldRejectEnvironmentForDisabledProject() {
        when(projectService.get(1L)).thenReturn(project(1L, "DISABLED"));

        assertThrows(IllegalArgumentException.class, () -> environmentService.create(
                1L,
                new CreateEnvironmentRequest("test", "测试环境", "devops-test", "local", null)
        ));

        verify(environmentMapper, never()).insert(any(EnvironmentEntity.class));
    }

    @Test
    void shouldRejectDuplicateEnvironmentCode() {
        when(projectService.get(1L)).thenReturn(project(1L, "ACTIVE"));
        when(environmentMapper.selectCount(any())).thenReturn(1L);

        assertThrows(DuplicateKeyException.class, () -> environmentService.create(
                1L,
                new CreateEnvironmentRequest("test", "测试环境", "devops-test", "local", null)
        ));
    }

    @Test
    void shouldUpdateEnvironmentDetails() {
        EnvironmentEntity entity = environment(10L, 1L, "ACTIVE");
        when(projectService.get(1L)).thenReturn(project(1L, "ACTIVE"));
        when(environmentMapper.selectOne(any())).thenReturn(entity);
        when(environmentMapper.updateById(any(EnvironmentEntity.class))).thenReturn(1);

        EnvironmentEntity result = environmentService.update(
                1L,
                10L,
                new UpdateEnvironmentRequest("测试环境二", "devops-test-v2", "local", "更新说明")
        );

        assertEquals("测试环境二", result.getName());
        assertEquals("devops-test-v2", result.getNamespace());
        verify(environmentMapper).updateById(entity);
    }

    @Test
    void shouldDisableEnvironmentIdempotently() {
        EnvironmentEntity entity = environment(10L, 1L, "ACTIVE");
        when(projectService.get(1L)).thenReturn(project(1L, "ACTIVE"));
        when(environmentMapper.selectOne(any())).thenReturn(entity);
        when(environmentMapper.updateById(any(EnvironmentEntity.class))).thenReturn(1);

        EnvironmentEntity result = environmentService.updateStatus(
                1L,
                10L,
                new UpdateEnvironmentStatusRequest("DISABLED")
        );

        assertEquals("DISABLED", result.getStatus());
        verify(environmentMapper).updateById(entity);

        when(projectService.get(1L)).thenReturn(project(1L, "ACTIVE"));
        when(environmentMapper.selectOne(any())).thenReturn(entity);
        environmentService.updateStatus(1L, 10L, new UpdateEnvironmentStatusRequest("DISABLED"));
        verify(environmentMapper, org.mockito.Mockito.times(1)).updateById(entity);
    }

    @Test
    void shouldRejectDeletingActiveEnvironment() {
        when(projectService.get(1L)).thenReturn(project(1L, "ACTIVE"));
        when(environmentMapper.selectOne(any())).thenReturn(environment(10L, 1L, "ACTIVE"));

        assertThrows(IllegalArgumentException.class, () -> environmentService.delete(1L, 10L));

        verify(environmentMapper, never()).deleteById(anyLong());
    }

    @Test
    void shouldLogicallyDeleteDisabledEnvironment() {
        EnvironmentEntity entity = environment(10L, 1L, "DISABLED");
        when(projectService.get(1L)).thenReturn(project(1L, "ACTIVE"));
        when(environmentMapper.selectOne(any())).thenReturn(entity);
        when(serviceEnvironmentMapper.selectCount(any())).thenReturn(0L);
        when(environmentMapper.updateById(any(EnvironmentEntity.class))).thenReturn(1);
        when(environmentMapper.deleteById(10L)).thenReturn(1);

        environmentService.delete(1L, 10L);

        verify(environmentMapper).updateById(entity);
        verify(environmentMapper).deleteById(10L);
    }

    @Test
    void shouldRejectMissingEnvironment() {
        when(projectService.get(1L)).thenReturn(project(1L, "ACTIVE"));
        when(environmentMapper.selectOne(any())).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> environmentService.get(1L, 99L));
    }

    @Test
    void shouldPassProjectFilterWhenLoadingGlobalEnvironmentPage() {
        Page<EnvironmentProjectRow> expected = new Page<>(1, 10);
        when(environmentMapper.selectProjectPage(any(), eq(List.of(1L, 2L)), eq(2L), eq("test"), eq("ACTIVE")))
                .thenReturn(expected);

        Page<EnvironmentProjectRow> result = environmentService.pageAll(
                List.of(1L, 2L), 2L, "test", "ACTIVE", 1, 10
        );

        assertSame(expected, result);
        verify(environmentMapper).selectProjectPage(any(), eq(List.of(1L, 2L)), eq(2L), eq("test"), eq("ACTIVE"));
    }

    @Test
    void shouldBatchDeleteOnlyAfterAllEnvironmentsPassValidation() {
        List<Long> ids = List.of(10L, 11L);
        when(environmentMapper.selectByIds(ids)).thenReturn(List.of(
                environment(10L, 1L, "DISABLED"),
                environment(11L, 2L, "DISABLED")
        ));
        when(serviceEnvironmentMapper.selectCount(any())).thenReturn(0L);

        environmentService.deleteBatch(ids);

        verify(environmentMapper).update(any(), any());
        verify(environmentMapper).deleteByIds(ids);
    }

    @Test
    void shouldRejectBatchDeleteWhenOneEnvironmentIsActive() {
        List<Long> ids = List.of(10L, 11L);
        when(environmentMapper.selectByIds(ids)).thenReturn(List.of(
                environment(10L, 1L, "DISABLED"),
                environment(11L, 1L, "ACTIVE")
        ));

        assertThrows(IllegalArgumentException.class, () -> environmentService.deleteBatch(ids));

        verify(environmentMapper, never()).update(any(), any());
        verify(environmentMapper, never()).deleteByIds(ids);
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

    private EnvironmentEntity environment(Long id, Long projectId, String status) {
        EnvironmentEntity entity = new EnvironmentEntity();
        entity.setId(id);
        entity.setProjectId(projectId);
        entity.setCode("test");
        entity.setName("测试环境");
        entity.setNamespace("devops-test");
        entity.setStatus(status);
        entity.setDeleted(false);
        return entity;
    }
}
