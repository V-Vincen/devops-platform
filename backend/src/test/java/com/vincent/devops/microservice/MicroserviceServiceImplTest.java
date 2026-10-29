package com.vincent.devops.microservice;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisMapperBuilderAssistant;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.vincent.devops.environment.service.EnvironmentService;
import com.vincent.devops.environment.domain.EnvironmentEntity;
import com.vincent.devops.environment.mapper.EnvironmentMapper;
import com.vincent.devops.microservice.domain.MicroserviceEntity;
import com.vincent.devops.microservice.domain.ServiceEnvironmentEntity;
import com.vincent.devops.microservice.dto.CreateMicroserviceRequest;
import com.vincent.devops.microservice.mapper.MicroserviceMapper;
import com.vincent.devops.microservice.mapper.ServiceEnvironmentMapper;
import com.vincent.devops.microservice.service.impl.MicroserviceServiceImpl;
import com.vincent.devops.releaseconfig.mapper.ReleaseConfigurationMapper;
import com.vincent.devops.project.service.ProjectService;
import com.vincent.devops.project.domain.ProjectEntity;
import com.vincent.devops.project.mapper.ProjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MicroserviceServiceImplTest {

    @BeforeAll
    static void initializeLambdaMetadata() {
        MybatisMapperBuilderAssistant assistant = new MybatisMapperBuilderAssistant(new MybatisConfiguration(), "microservice-test");
        TableInfoHelper.initTableInfo(assistant, MicroserviceEntity.class);
        TableInfoHelper.initTableInfo(assistant, ServiceEnvironmentEntity.class);
        TableInfoHelper.initTableInfo(assistant, EnvironmentEntity.class);
    }

    @Mock
    private MicroserviceMapper microserviceMapper;

    @Mock
    private ServiceEnvironmentMapper serviceEnvironmentMapper;

    @Mock
    private ProjectMapper projectMapper;

    @Mock
    private EnvironmentMapper environmentMapper;

    @Mock
    private ProjectService projectService;

    @Mock
    private EnvironmentService environmentService;

    @Mock
    private ReleaseConfigurationMapper releaseConfigurationMapper;

    @InjectMocks
    private MicroserviceServiceImpl microserviceService;

    @Test
    void shouldBatchDeleteOnlyAfterAllServicesPassValidation() {
        List<Long> ids = List.of(100L, 101L);
        when(microserviceMapper.selectByIds(ids)).thenReturn(List.of(
                service(100L, "DISABLED"),
                service(101L, "DISABLED")
        ));
        microserviceService.deleteBatch(ids);

        verify(microserviceMapper).update(any(), any());
        verify(serviceEnvironmentMapper).update(any(), any());
        verify(serviceEnvironmentMapper).delete(any());
        verify(microserviceMapper).deleteByIds(ids);
    }

    @Test
    void shouldRejectBatchDeleteWhenOneServiceIsActive() {
        List<Long> ids = List.of(100L, 101L);
        when(microserviceMapper.selectByIds(ids)).thenReturn(List.of(
                service(100L, "DISABLED"),
                service(101L, "ACTIVE")
        ));

        assertThrows(IllegalArgumentException.class, () -> microserviceService.deleteBatch(ids));

        verify(microserviceMapper, never()).update(any(), any());
        verify(microserviceMapper, never()).deleteByIds(ids);
    }

    @Test
    void shouldBindMultipleActiveEnvironmentsInOneWrite() {
        when(projectService.get(1L)).thenReturn(project(1L, "ACTIVE"));
        when(microserviceMapper.selectOne(any())).thenReturn(service(100L, "ACTIVE"));
        when(environmentMapper.selectList(any())).thenReturn(List.of(
                environment(10L, 1L, "ACTIVE"),
                environment(11L, 1L, "ACTIVE")
        ));

        microserviceService.bindEnvironments(1L, 100L, List.of(10L, 11L));

        verify(serviceEnvironmentMapper).insertIgnoreExisting(org.mockito.ArgumentMatchers.argThat(bindings ->
                bindings.size() == 2
                        && bindings.stream().allMatch(binding -> binding.getProjectId().equals(1L))
                        && bindings.stream().allMatch(binding -> binding.getServiceId().equals(100L))
        ));
        verify(releaseConfigurationMapper).insertDefaultPolicies(
                org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(100L),
                org.mockito.ArgumentMatchers.eq(List.of(10L, 11L)), any());
    }

    @Test
    void shouldRejectDuplicateEnvironmentIdsBeforeWritingBinding() {
        assertThrows(IllegalArgumentException.class, () ->
                microserviceService.bindEnvironments(1L, 100L, List.of(10L, 10L))
        );

        verify(serviceEnvironmentMapper, never()).insertIgnoreExisting(any());
    }

    @Test
    void shouldCreateDefaultReleaseConfigurationForNewSimulationService() {
        when(projectService.get(1L)).thenReturn(project(1L, "ACTIVE"));
        when(microserviceMapper.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> {
            MicroserviceEntity entity = invocation.getArgument(0);
            entity.setId(200L);
            return 1;
        }).when(microserviceMapper).insert(any(MicroserviceEntity.class));
        when(microserviceMapper.selectOne(any())).thenReturn(service(200L, "ACTIVE"));
        when(environmentMapper.selectList(any())).thenReturn(List.of(environment(10L, 1L, "ACTIVE")));

        microserviceService.create(1L, new CreateMicroserviceRequest(
                "simulation-service", "模拟服务", "https://github.com/example/simulation-service.git", "main",
                "MAVEN", "nexus.local/test/simulation-service", 8080, 1,
                null, null, "仅用于验证默认发布配置", List.of(10L)
        ));

        verify(releaseConfigurationMapper).insertDefaultRepository(
                any(), org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq("simulation-service"),
                org.mockito.ArgumentMatchers.eq("https://github.com/example/simulation-service.git"),
                org.mockito.ArgumentMatchers.eq("main"), any());
        verify(releaseConfigurationMapper).insertDefaultBuildSpec(
                any(), org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(200L), any(),
                org.mockito.ArgumentMatchers.eq("MAVEN"),
                org.mockito.ArgumentMatchers.eq("nexus.local/test/simulation-service"), any());
        verify(releaseConfigurationMapper).insertDefaultPolicies(
                org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(200L),
                org.mockito.ArgumentMatchers.eq(List.of(10L)), any());
    }

    private MicroserviceEntity service(Long id, String status) {
        MicroserviceEntity entity = new MicroserviceEntity();
        entity.setId(id);
        entity.setProjectId(1L);
        entity.setServiceCode("demo-service");
        entity.setServiceName("演示服务");
        entity.setStatus(status);
        entity.setDeleted(false);
        return entity;
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
        entity.setStatus(status);
        entity.setDeleted(false);
        return entity;
    }
}
