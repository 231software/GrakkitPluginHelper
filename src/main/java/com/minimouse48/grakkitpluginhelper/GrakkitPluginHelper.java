package com.minimouse48.grakkitpluginhelper;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;


public final class GrakkitPluginHelper extends JavaPlugin {
    public static GrakkitPluginHelper APIInstance;
    private ArrayList<Runnable> onEnableListeners=new ArrayList<>();
    private boolean enabled=false;
    private HTTPServerAPI HTTPServerAPIInstance;
    private HTTPClientAPI HTTPClientAPIInstance;
    @Override
    public void onEnable() {
        // Plugin startup logic
        Bukkit.getLogger().info("Grakkit Plugin Helper Has Been Loaded.");
        APIInstance =this;
        //初始化httserverapi这整个api
        HTTPServerAPIInstance=new HTTPServerAPI(this);
        HTTPClientAPIInstance=new HTTPClientAPI(this);
        this.enabled=true;
        //执行onEnable完成后调用所有触发器
        this.onEnableListeners.forEach(listener->{
            listener.run();
        });
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
    public HTTPServerAPI getHTTPServerAPI(){
        return APIInstance.HTTPServerAPIInstance;
    }
    public HTTPClientAPI getHTTPClientAPI(){
        return APIInstance.HTTPClientAPIInstance;
    }
    public void regOnEnableListener(Runnable callback){
        //只有在enable之前才有监听该方法的意义
        //因为该方法在一个生命周期中正常来说是只会被调用一次的
        //如果在enable之后还要注册，那此时enable是已经发生过的状态
        //那么立即触发就可以了
        if(enabled){
            callback.run();
        }
        else{
            this.onEnableListeners.add(callback);
        }
    }
    public int getByteLength(String str)throws IOException {
        return str.getBytes("UTF-8").length;
    }

    public void test() {
        Bukkit.getLogger().info("test调用成功");
    }

}
