package com.minimouse48.grakkitpluginhelper;

import org.bukkit.Bukkit;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.io.*;
import java.net.*;
import java.util.UUID;
import java.util.logging.Level;

import com.google.gson.Gson;
//这是一个包装器，而不是api功能本身
public class HTTPServerAPI {
    private static GrakkitPluginHelper instance;
    //这里存储所有服务器的实例
    private final Map<String,HTTPServer> instances;
    public HTTPServerAPI(GrakkitPluginHelper plugin) {
        instance=plugin;
        this.instances=new HashMap<>();
    }
    //返回一个编号作为服务器的编号
    public String create(Integer port,Integer timeout){
        String serverUUID=UUID.randomUUID().toString();
//        Bukkit.getLogger().info("服务器将被创建");
        this.instances.put(serverUUID,new HTTPServer(port,timeout));
        return serverUUID;
    }
    public boolean start(String uuid){
        return this.instances.get(uuid).start();
    }
    public boolean stop(String uuid){
        boolean stopResult= this.instances.get(uuid).stop();
        //服务器停止之后，还需要将对应的实例进行清理
        this.instances.remove(uuid);
        return stopResult;
    }
    public void resolveResponse(String serverUUID,String requestUUID,String responseText){
        this.instances.get(serverUUID).resolveResponse(requestUUID,responseText);
    }
    public void setOnRequest(String serverUUID,Consumer<String> onRequest){
        this.instances.get(serverUUID).setOnRequest(onRequest);
    }
    public void setOnCilentUploaded(String serverUUID,Consumer<String> onCilentUploaded){
        this.instances.get(serverUUID).setOnClientUploaded(onCilentUploaded);
    }
    public static void test(Consumer<String> callback){
        Bukkit.getLogger().info("现在调用回调函数。");
        callback.accept("回调函数");
        //模拟Promise
        //Promise的主要机制是一个线程带有一个回调函数then
        //就是说仍然跑不出回调函数的机制
        //对于java的future，其机制就是让当前线程等待某个特定的线完成
    }
}

class HTTPServer{
    private ServerSocket server;
    private int port;
    private int timeout;
    private Map<String,Socket> clientRequests;
    private Consumer<String> onRequest;
    private Consumer<String> onClientUploaded;
    public HTTPServer(int port, int timeout){

        this.port=port;
        this.timeout=timeout;
        this.clientRequests =new ConcurrentHashMap<>();
    }

    public void setOnRequest(Consumer<String> onRequest){

        this.onRequest = onRequest;
    }
    public void setOnClientUploaded(Consumer<String> onClientUploaded){

        this.onClientUploaded=onClientUploaded;
    }
    public boolean start(){
        try{
            this.server=new ServerSocket(this.port);
//            Bukkit.getLogger().info("HTTP服务器启动，监听端口 "+this.port+"...");
            // 服务器关闭后停止循环
            try {
                //等待客户端发出请求
                CompletableFuture.runAsync(()->{
                    //http服务器使用纯原生api+thread多线程实现
                    while (!this.server.isClosed()) {
                        try {
                            //this.server.accept()代表接收到客户端请求的瞬间，但是此时请求头还未上传完毕
                            //请求头上传完毕后，调用fmp插件提供的回调
                            Socket socket = this.server.accept();
                            String requestUUID = UUID.randomUUID().toString();
                            //将这次请求产生的可以用来写入数据并发送的响应流存储并打上编号
                            //这个编号在后面还得给它传入js那边的各种存储，让js能够用这个编号找到OutputStream并发送响应
                            this.clientRequests.put(requestUUID, socket);
                            //Bukkit.getLogger().info("已将"+requestUUID+"放入");
                            //Bukkit.getLogger().info("现在this.clientRequests中实际有的：");
                            //设置本次客户端请求上传的超时时间
                            socket.setSoTimeout(timeout);
                            //请求已接收到，先等待客户端上传请求头
                            //下面开始等待客户端上传请求头
                            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                            //读取请求头中的请求行
                            String requestLine = reader.readLine();
//                            Bukkit.getLogger().info("收到请求：" + requestLine);
                            //整个请求方法在请求头之外，所以需要单独写入请求元数据中
                            String[] requestLineParts = requestLine.split(" ");
                            String method = requestLineParts[0];
                            String url = requestLineParts[1];
                            String httpVersion = requestLineParts[2];
                            if (method.equals("GET")) {
                                Bukkit.getLogger().info("这是一个 GET 请求，无需解析请求体");
                            }
                            else{
                                Bukkit.getLogger().info("不支持的请求方法："+method);
                            }

                            //下面开始读取客户端发来的请求头中的详细数据
                            // 读取请求头，寻找 `Transfer-Encoding: chunked`
                            String line;
                            //默认情况下不分块上传
                            boolean isChunked = false;
                            //连续读取请求头，直到请求头结束（头部和请求体）
                            Map<String, String> headers = new HashMap<>();
                            while (!(line = reader.readLine()).isEmpty()) {
                                //Bukkit.getLogger().info("请求头: " + line);
                                String[] headerParts = line.split(": ", 2);
                                if (headerParts.length == 2) {
                                    headers.put(headerParts[0], headerParts[1]);
                                }
                                //如果发现客户端要分块上传数据，在请求头中附上了相关信息
                                if (line.toLowerCase().contains("transfer-encoding: chunked")) {
                                    isChunked = true;
                                }
                            }
                            //isChunked在这里准备完毕，可以被读取用于判断
                            //headers是刚刚存储好的请求头
                            //现在是时候调用onRequest了
                            //用Gson将headers这个HashMap类型的请求头转换成json，将它移交给js
                            Bukkit.getLogger().info("下面将调用onRequest");
                            Bukkit.getLogger().info("这个onRequest是："+System.identityHashCode(this.onRequest));
                            this.onRequest.accept(
                                "{\"uuid\":\"" + requestUUID +
                                        "\",\"headers\":" +
                                    new Gson().toJson(headers) +
                                    ",\"method\":\""+method+"\""+
                                        ",\"url\":\"" + url + "\"" +
                                        ",\"httpVersion\":\"" + httpVersion + "\"" +
                                    "}"
                            );
                            //接下来还有一个onClientUploaded需要调用
                            //
                            //此时请求已接收到，但是客户端的请求体还未上传完毕
                            //然而如果客户端使用的是POST或者PUT这类没有请求体的方法，就要跳过下面的步骤
                            StringBuilder body=new StringBuilder();
                            if(method.equals("POST")){
                                // 处理请求体
                                body = new StringBuilder();
                                if (isChunked) {
                                    //Bukkit.getLogger().info("检测到分块传输，开始接收数据...");
                                    while (true) {
                                        // 读取块长度（十六进制）
                                        String chunkSizeHex = reader.readLine();
                                        if (chunkSizeHex == null) {
                                            Bukkit.getLogger().info("接收到无效数据，客户端可能已断开连接，放弃本次接收");
                                            break; // 连接断开，退出循环
                                        }
                                        int chunkSize = Integer.parseInt(chunkSizeHex, 16); // 转换为整数
                                        if (chunkSize == 0) break; // 结束传输

                                        // 读取块数据
                                        char[] buffer = new char[chunkSize];
                                        reader.read(buffer, 0, chunkSize);
                                        body.append(buffer);

                                        String endLine = reader.readLine();
                                        if (!endLine.isEmpty()) {
                                            Bukkit.getLogger().info("块结束时应为空行，但收到：" + endLine);
                                        }

                                        Bukkit.getLogger().info("收到数据块：" + new String(buffer));
                                    }
                                } else if(headers.containsKey("Content-Length")) {
                                    Bukkit.getLogger().info("读取完整请求体...");
                                    //这里是导致非预期的超时的关键，应该在这里使用Content-Length精确读取请求体
                                    //到时候还需要考虑一下Content-Length不正确的时候，是不是会抛出错误，或者直接导致内容异常（例如结尾充满了EOF这种符号）
                                    //客户端提供的原始的Content-Length信息
                                    String contentLengthStr = headers.get("Content-Length");

                                    int contentLengthBytes; // Content-Length 是字节数
                                    try {
                                        contentLengthBytes = Integer.parseInt(contentLengthStr);
                                    } catch (NumberFormatException e) {
                                        Bukkit.getLogger().severe("Content-Length 格式错误: " + contentLengthStr + ", 错误: " + e.getMessage());
                                        continue; // 跳过本次请求
                                    }

                                    // 注意：Content-Length 是字节数，而 reader.read 读取的是字符数。
                                    // 对于 UTF-8，英文字符是1字节1字符，但中文等是多字节1字符。
                                    // 理想情况下，应该根据 Content-Type 中的 charset 来处理。
                                    // 为了避免预读问题，我们仍然使用 reader，但需要理解这个字节到字符的转换。
                                    // 对于纯ASCII或UTF-8文本，通常按字节读取即可，但reader是按字符的。
                                    // 更准确的做法是读取字节流，但在你的问题情境下，统一reader是更优先的解决阻塞问题。
                                    // 这里的 contentLengthBytes 我们假设它是纯ASCII字符数，或者说它就是所需的字符数（虽然严格来说不正确）

                                    char[] buffer = new char[contentLengthBytes]; // 创建与Content-Length相同大小的字符缓冲区
                                    int totalCharsRead = 0;

                                    Bukkit.getLogger().info("本次将尝试读取大致 " + contentLengthBytes + " 个字符（基于Content-Length）");

                                    // 循环读取直到达到 Content-Length 指定的字节数 (近似字符数)
                                    // 或者直到 reader 读不到更多数据
                                    while (totalCharsRead < contentLengthBytes) {
                                        int charsRead = reader.read(buffer, totalCharsRead, contentLengthBytes - totalCharsRead);
                                        if (charsRead == -1) {
                                            Bukkit.getLogger().info("客户端断开连接，请求体读取不完整。");
                                            break; // 客户端断开连接
                                        }
                                        totalCharsRead += charsRead;
                                        Bukkit.getLogger().info("本次读取了 " + charsRead + " 个字符，总共已读取 " + totalCharsRead + " 个字符。");
                                    }
                                    body.append(buffer, 0, totalCharsRead); // 将读取到的字符添加到 body

                                    //chatgpt推荐的在这种情况下改用InputStream(字节流)
//                                    InputStream in = socket.getInputStream();
////                                    //将Content-Length转换成可以被程序识别的整数
////                                    int contentLength = Integer.parseInt(contentLengthStr);
////                                    //这个char类型数组用于接收实际请求体中的内容
////                                    byte[] buffer = new byte[contentLength];
////                                    //这个变量用于标记
////                                    int totalRead = 0;
////                                    //按照给定的读取长度读取指定次数
////                                    //这里之所以不使用for循环，是因为totalRead的增量是动态的
////                                    Bukkit.getLogger().info("马上就要开始读取了");
////                                    Bukkit.getLogger().info("本次将尝试读取"+contentLength+"个字节");
////
////                                    while (totalRead < contentLength) {
////                                        Bukkit.getLogger().info("读取");
////                                        //开始读取，写入buffer
////                                        int read = in.read(buffer, totalRead, contentLength - totalRead);
////                                        Bukkit.getLogger().info("本次读取了"+read+"个字节");
////                                        if (read == -1) {
////                                            Bukkit.getLogger().info("客户端断开连接");
////                                            break; // 客户端断开连接
////                                        }
////
////                                        totalRead += read;
////                                    }
////
////                                    // 假设请求体是 UTF-8 编码文本
////                                    //此处还需要判断一下请求体的编码，这个从请求头里面读取就可以
////                                    body = new StringBuilder(new String(buffer, 0, totalRead, StandardCharsets.UTF_8));



//                                body = new StringBuilder();
//                                String line1;
//                                while ((line1 = reader.readLine()) != null) {
//                                    body.append(line1).append("\n");  // 直接读取完整内容
//                                }
                                    //Bukkit.getLogger().info("请求体内容: \n" + body);
                                }
                                else{
                                    Bukkit.getLogger().info("既不是 chunked，也没有 Content-Length");
                                    Bukkit.getLogger().info("这种情况下目前还不能正确读取请求体");
                                    Bukkit.getLogger().info("在这种情况下将返回空字符串。");
                                    Bukkit.getLogger().info("需要注意的是有可能用浏览器地址栏访问这种get请求");
                                    Bukkit.getLogger().info("这种情况下请求体本来就不用读取，所以是可以跳过的");
                                }
                                Bukkit.getLogger().info("请求体内容为" + body);

                            }
                            //现在请求体处理完毕，可以向js发送请求体
                            this.onClientUploaded.accept("{\"uuid\":\"" + requestUUID + "\",\"body\":" + new Gson().toJson(body) + "}");
                            //现在客户端所有内容都已经上传完毕
                            //至于发送请求的部分，js那边随时都可以调用相关方法进行发送，无需等待客户端数据上传完毕
                            //所以里就是直接全部结束了
                        } catch (IOException e) {
                            if(e.getMessage().equals("Socket closed"))return;
                            else Bukkit.getLogger().severe("读取客户端请求时出现错误：" + e.getMessage());
                        }
                    }
                }, Executors.newSingleThreadExecutor());
            } catch (Exception e) {
                Bukkit.getLogger().warning("服务器可能已关闭，停止接收请求。错误消息："+e.getMessage());
            }
            return true;
        }
        catch(IOException e){
            Bukkit.getLogger().warning("无法启动服务器："+e.getMessage());
            return false;
        }

    }
    //响应客户端的某个请求
    //必须响应了客户端的请求，才能关闭这个连接
    public void resolveResponse(String requestUUID,String responseText){
        //Bukkit.getLogger().info("将做出响应的请求uuid："+requestUUID);
        //下面这段代码不能放在线程里，否则会出现竞态问题
        Socket socket=this.clientRequests.get(requestUUID);
        //发送的过程也是阻塞的，所以新建一个线程把它变成异步
        CompletableFuture.runAsync(()->{
            if(Objects.isNull(socket)){
                Bukkit.getLogger().warning("socket获取失败！");
                Bukkit.getLogger().warning("现在this.clientRequests中实际有的：");
                for(String key:this.clientRequests.keySet()){
                    Bukkit.getLogger().warning(key);
                }
                return;
            }
            OutputStream outputStream;
            try{
                outputStream=socket.getOutputStream();
            }
            catch (IOException e){
                Bukkit.getLogger().warning("无法取得请求流："+e.getMessage());
                //如果不能成功取得请求流，那么outputStream就没法被正确初始化，那么响应流的发送就可以只能被跳过了
                return;
            }
            try{
                //responseText还没有写入，这里用了一个默认文本做测试，等测试好了再把这个响应文本传入
                String response = responseText;
                outputStream.write(response.getBytes());
            }
            catch (IOException e){
                Bukkit.getLogger().warning("无法写入请求流："+e.getMessage());
                //要尽可能保证响应是能发送出去的，所以即使请求流写入出错，也不能耽误后面请求流的发送
            }
            try{
                outputStream.flush();
            }
            catch (IOException e){
                Bukkit.getLogger().warning("无法发送请求流："+e.getMessage());
            }
            try{
                socket.close();
            }
            catch (IOException e){
                Bukkit.getLogger().warning("结束客户端http请求会话时出错："+e.getMessage());
            }

        });
        //响应完毕后，无论如何都要清除对应的socket，防止内存泄漏
        this.clientRequests.remove(requestUUID);
    }
    public boolean stop() {
        try {
            this.server.close();
        } catch (Exception e) {
            Bukkit.getLogger().warning("无法关闭服务器：");
            e.printStackTrace();
            return false;
        }
        return true;
    }
}