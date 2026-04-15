package com.huawei.opsfactory.gateway.service.channel.model;

public record ChannelConnectionConfig(
        String loginStatus,
        String sessionLabel,
        String authStateDir,
        String lastConnectedAt,
        String lastDisconnectedAt,
        String lastError,
        String selfPhone,
        String wechatId,
        String displayName
) {
}
