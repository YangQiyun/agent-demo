package com.alibaba.dubbo.performance.demo.agent.dubbo;

import com.alibaba.dubbo.performance.demo.agent.dubbo.model.JsonUtils;
import com.alibaba.dubbo.performance.demo.agent.dubbo.model.Request;
import com.alibaba.dubbo.performance.demo.agent.dubbo.model.RpcFuture;
import com.alibaba.dubbo.performance.demo.agent.dubbo.model.RpcInvocation;
import com.alibaba.dubbo.performance.demo.agent.dubbo.model.RpcRequestHolder;

import com.alibaba.dubbo.performance.demo.agent.registry.IRegistry;
import io.netty.channel.Channel;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.async.DeferredResult;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

public class RpcClient {
    private Logger logger = LoggerFactory.getLogger(RpcClient.class);

    private ConnecManager connectManager;

    public RpcClient(IRegistry registry){
        this.connectManager = new ConnecManager();
    }

    public Object invoke(String interfaceName, String method, String parameterTypesString, String parameter) throws Exception {

        Future<Channel> channelFuture = connectManager.getFuture();


        RpcInvocation invocation = new RpcInvocation();
        invocation.setMethodName(method);
        invocation.setAttachment("path", interfaceName);
        invocation.setParameterTypes(parameterTypesString);    // Dubbo内部用"Ljava/lang/String"来表示参数类型是String

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(out));
        JsonUtils.writeObject(parameter, writer);
        invocation.setArguments(out.toByteArray());

        Request request = new Request();
        request.setVersion("2.0.0");
        request.setTwoWay(true);
        request.setData(invocation);


        DeferredResult<byte[]> DefferResult = new DeferredResult<>();
        RpcFuture future = new RpcFuture();
        future.setResult(DefferResult);
        RpcRequestHolder.put(String.valueOf(request.getId()),future);

        channelFuture.addListener((FutureListener<Channel>) f1 -> {
            if (f1.isSuccess()) {
                Channel ch = f1.getNow();
                ch.writeAndFlush(request);
                // Release back to pool
                connectManager.release(ch);
            }
        });

//        Object result = null;
//        try {
//            result = future.get();
//        }catch (Exception e){
//            e.printStackTrace();
//        }

        return DefferResult;
    }
}
