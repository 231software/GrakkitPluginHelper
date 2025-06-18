package com.minimouse48.grakkitpluginhelper;

import com.google.gson.Gson;
import org.bukkit.Bukkit;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class HTTPClientAPI {
    private static GrakkitPluginHelper instance;
    //这里存储所有客户端的实例
    private final Map<String,HTTPRequest> instances;
    public HTTPClientAPI(GrakkitPluginHelper plugin) {
        instance=plugin;
        this.instances=new HashMap<>();
    }
    //返回一个编号作为请求的编号
    public String create(String urlStr,String method,String requestPropertiesJSON,Integer timeout){
        String requestUUID= UUID.randomUUID().toString();
//        Bukkit.getLogger().info("请求url:"+urlStr);
        this.instances.put(requestUUID,new HTTPRequest(urlStr,method,requestPropertiesJSON,timeout));
        return requestUUID;
    }
    public void uploadBody(String requestUUID,String body){
        this.uploadBody(requestUUID,body,"UTF-8");
    }
    public void uploadBody(String requestUUID,String body, String charset){
        try{
            this.instances.get(requestUUID).uploadBody(body,charset);
        }catch (IOException e) {
            Bukkit.getLogger().warning("请求上传失败："+e.getMessage());
        }
    }
    //send发送之后，整个请求就已经不需要再用于调用了
    //所以在这个的最后要从instances里删除这个请求腾出资源
    public void send(String requestUUID,Consumer<String> onResponse,Consumer<String> onBodyDownloaded){
        this.instances.get(requestUUID).send(onResponse,onBodyDownloaded);
        this.instances.remove(requestUUID);
    }

}

class HTTPRequest {
    private HttpURLConnection connection;
    public HTTPRequest(String urlStr,String method,String requestPropertie1sJSON,Integer timeout) {
        try {
            // 目标 URL
            URL url = new URL(urlStr);
            this.connection = (HttpURLConnection) url.openConnection();

            //解析插件传入的requestProperties JSON数据
            Map<String,String> requestProperties=new Gson().fromJson(requestPropertie1sJSON,Map.class);
            for(String key:requestProperties.keySet()){
                //将所有数据解析好并执行setRequestProperty();
                this.connection.setRequestProperty(key,requestProperties.get(key));
            }

            // 设置请求方法
            this.connection.setRequestMethod(method);
            // 不启用这个输出流的话会导致cannot write to a URLConnection if doOutput=false - call setDoOutput(true)
            if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method))this.connection.setDoOutput(true);
            this.connection.setConnectTimeout(timeout);
            this.connection.setReadTimeout(timeout);




        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void uploadBody(String body,String charset)throws IOException {

        // 发送请求体数据
        String requestBody = body;
        //这是一个try-with-resources语法
        //括号里的资源会在try执行完之后自动释放
        try (OutputStream os = this.connection.getOutputStream()) {
            byte[] input = requestBody.getBytes(charset);
            os.write(input, 0, input.length);
        }
    }
    public void send(Consumer<String> onResponse,Consumer<String> onBodyDownloaded){
        CompletableFuture.runAsync(()->{
            try{

                // 获取响应状态码
                //请求也是需要单独执行一下发送方法的
                //所以创建完了不马上发送
                //那么创建步骤到这里就结束了
                //阻塞方法
                int statusCode = this.connection.getResponseCode();
//                Bukkit.getLogger().info("状态码: " + statusCode);

                // 获取响应头
//                Bukkit.getLogger().info("响应头:");
                Map<String, java.util.List<String>> headers=this.connection.getHeaderFields();
                headers.forEach((key, value) -> Bukkit.getLogger().info(key + ": " + value));
                String headersStr=new Gson().toJson(headers,Map.class);
                //响应头获取完毕后，执行onResponse
                onResponse.accept("{\"statusCode\":"+statusCode+",\"headers\":"+headersStr+"}");

                // 获取响应体
                //getInputStream阻塞.
                InputStream inputStream = (statusCode >= 200 && statusCode < 300) ?
                                              this.connection.getInputStream() :
                                              this.connection.getErrorStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                String line;
                StringBuilder responseBody = new StringBuilder();
                //readLine阻塞
                while ((line = reader.readLine()) != null) {
                    responseBody.append(line).append("\n");
                }
                reader.close();
                //此时响应体完全获取完毕
                //调用onBodyDownloaded
                onBodyDownloaded.accept(responseBody.toString());


//                Bukkit.getLogger().info("响应体:");
//                Bukkit.getLogger().info(responseBody.toString());

                // 关闭连接
                this.connection.disconnect();
            }catch(IOException e){
                Bukkit.getLogger().warning("发送请求时出错：" + e.toString()); // 使用 toString() 获取更多错误信息
                e.printStackTrace(); // 打印完整的异常栈信息
            }
        });
    }
}
