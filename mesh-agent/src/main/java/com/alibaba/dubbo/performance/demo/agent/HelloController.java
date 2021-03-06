package com.alibaba.dubbo.performance.demo.agent;

import com.alibaba.dubbo.performance.demo.agent.dubbo.RpcClient;
import com.alibaba.dubbo.performance.demo.agent.registry.Endpoint;
import com.alibaba.dubbo.performance.demo.agent.registry.EtcdRegistry;
import com.alibaba.dubbo.performance.demo.agent.registry.IRegistry;
import io.netty.util.HashedWheelTimer;
import okhttp3.*;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.asynchttpclient.AsyncHttpClient;
import static org.asynchttpclient.Dsl.*;
import org.asynchttpclient.AsyncHttpClientConfig;
import org.asynchttpclient.DefaultAsyncHttpClientConfig;
import org.asynchttpclient.ListenableFuture;
import org.asynchttpclient.netty.channel.DefaultChannelPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;


import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@RestController
public class HelloController {

    private Logger logger = LoggerFactory.getLogger(HelloController.class);
    
    private EtcdRegistry registry = new EtcdRegistry(System.getProperty("etcd.url"));

    private RpcClient rpcClient = new RpcClient(registry);
    private Random random = new Random();
    private List<Endpoint> endpoints = null;
    private Object lock = new Object();
    private AsyncHttpClient asyncHttpClient = null;

    public HelloController(){
        // simple configure
        HashedWheelTimer timer = new HashedWheelTimer();
        timer.start();
        asyncHttpClient = asyncHttpClient(config()
                .setMaxConnections(2000)
                .setMaxConnectionsPerHost(200)
                .setNettyTimer(timer)
                .setChannelPool(new DefaultChannelPool(60000,-1,DefaultChannelPool.PoolLeaseStrategy.LIFO,timer,1000))
        );
    }

    @RequestMapping(value = "")
    public Object invoke(@RequestParam("interface") String interfaceName,
                         @RequestParam("method") String method,
                         @RequestParam("parameterTypesString") String parameterTypesString,
                         @RequestParam("parameter") String parameter) throws Exception {
        String type = System.getProperty("type");   // 获取type参数
        if ("consumer".equals(type)){
            return consumer(interfaceName,method,parameterTypesString,parameter);
        }
        else if ("provider".equals(type)){
            return provider(interfaceName,method,parameterTypesString,parameter);
        }else {
            return "Environment variable type is needed to set to provider or consumer.";
        }
    }

    public Object provider(String interfaceName,String method,String parameterTypesString,String parameter) throws Exception {

        Object result = rpcClient.invoke(interfaceName,method,parameterTypesString,parameter);
        return  result;
    }

    public DeferredResult<Integer> consumer(String interfaceName,String method,String parameterTypesString,String parameter) throws Exception {

        if (null == endpoints){
            synchronized (lock){
                if (null == endpoints){
                    endpoints =registry.findAll();
                }
            }
        }

        // 随机轮询负载均衡，取一个
        Endpoint endpoint = endpoints.get(random.nextInt(6));
        String url =  "http://" + endpoint.getHost() + ":" + endpoint.getPort();


        // 使用异步http替换同步http
        DeferredResult<Integer> result = new DeferredResult<>();
        org.asynchttpclient.Request request = org.asynchttpclient.Dsl.post(url)
                .addFormParam("interface", interfaceName)
                .addFormParam("method", method)
                .addFormParam("parameterTypesString", parameterTypesString)
                .addFormParam("parameter", parameter)
                .build();
        ListenableFuture<org.asynchttpclient.Response> responseFuture = asyncHttpClient.executeRequest(request);
        Runnable callback = () -> {
            try {
                byte[] responseBody = null;
                responseBody = responseFuture.get().getResponseBody().getBytes();
                result.setResult(Integer.valueOf(new String(responseBody)));
            } catch (Exception e) {
                e.printStackTrace();
            }
        };
        responseFuture.addListener(callback, null);
        return result;
    }

}
