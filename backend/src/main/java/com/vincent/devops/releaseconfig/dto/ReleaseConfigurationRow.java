package com.vincent.devops.releaseconfig.dto;

/** MyBatis 联表查询的内部行对象；环境策略由独立查询填充。 */
public record ReleaseConfigurationRow(
        String serviceId, String repositoryId, String repositoryKey, String provider, String cloneUrl,
        String defaultBranch, String credentialReference, String buildSpecId, String sourceRoot,
        String changePathRules, String buildWorkdir, String buildType, String buildCommand,
        String dockerfilePath, String dockerContextPath, String imageRepository
) {
}
