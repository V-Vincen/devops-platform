package com.vincent.devops.releaseconfig.service;

import com.vincent.devops.releaseconfig.dto.ReleaseConfigurationResponse;
import com.vincent.devops.releaseconfig.dto.UpdateReleaseConfigurationRequest;

public interface ReleaseConfigurationService {
    ReleaseConfigurationResponse get(Long projectId, Long serviceId);
    ReleaseConfigurationResponse update(Long projectId, Long serviceId, UpdateReleaseConfigurationRequest request);
}
