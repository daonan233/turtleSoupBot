package com.example.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class BotConfig {
    public static final String API_KEY = "sk-a6MdAxyRGRyPBVM60xUzAQIZMjIl8SIrkTtUmFeymViIPCp3";
    public static final String API_URL = "https://api.moonshot.cn/v1/chat/completions";
    public static final Gson GSON = new GsonBuilder().create();

    public static void loadConfig() {
        // 这里可以添加从配置文件加载的逻辑

    }
}