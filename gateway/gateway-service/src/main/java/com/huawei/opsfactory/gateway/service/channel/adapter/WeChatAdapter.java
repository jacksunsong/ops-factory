package com.huawei.opsfactory.gateway.service.channel.adapter;

import com.huawei.opsfactory.gateway.service.channel.ChannelAdapter;
import com.huawei.opsfactory.gateway.service.channel.model.ChannelConnectivityResult;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class WeChatAdapter implements ChannelAdapter {

    @Override
    public String type() {
        return "wechat";
    }

    @Override
    public Mono<String> verifyWebhook(String channelId, ServerWebExchange exchange) {
        return Mono.error(new ResponseStatusException(BAD_REQUEST, "WeChat channel does not use webhooks"));
    }

    @Override
    public Mono<Void> handleWebhook(String channelId, String rawBody, ServerWebExchange exchange) {
        return Mono.error(new ResponseStatusException(BAD_REQUEST, "WeChat channel does not use webhooks"));
    }

    @Override
    public Mono<ChannelConnectivityResult> testConnectivity(String channelId) {
        return Mono.just(new ChannelConnectivityResult(false, "WeChat channel runtime is not implemented yet"));
    }
}
