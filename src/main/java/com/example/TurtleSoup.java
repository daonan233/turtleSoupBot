package com.example;

import com.example.config.BotConfig;
import com.example.Message;
import kotlinx.serialization.descriptors.SerialKind;
import net.mamoe.mirai.console.plugin.jvm.JavaPlugin;
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescriptionBuilder;
import net.mamoe.mirai.event.GlobalEventChannel;
import net.mamoe.mirai.event.events.GroupMessageEvent;
import net.mamoe.mirai.message.data.At;
import net.mamoe.mirai.message.data.MessageChain;
import net.mamoe.mirai.message.data.PlainText;
import okhttp3.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class TurtleSoup extends JavaPlugin {
    private static final Map<Long, Boolean> enabledGroups = new ConcurrentHashMap<>();
    private static final Map<Long, List<Message>> chatHistories = new ConcurrentHashMap<>();
    private static final OkHttpClient httpClient = new OkHttpClient();
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    public TurtleSoup() {
        super(new JvmPluginDescriptionBuilder("com.example", "1.0")
                .name("TurtleSoup")
                .author("daonan233")
                .build());
    }

    @Override
    public void onEnable() {
        getLogger().info("海龟汤机器人已启动");
        BotConfig.loadConfig();

        GlobalEventChannel.INSTANCE.subscribeAlways(GroupMessageEvent.class, event -> {
            try {
                handleMessage(event);
            } catch (Exception e) {
                getLogger().error("处理消息失败", e);
            }
        });
    }

    private void handleMessage(GroupMessageEvent event) {
        long groupId = event.getGroup().getId();
        MessageChain chain = event.getMessage();
        String content = chain.contentToString().trim();

        // 处理开关命令
        if (content.equals("#海龟汤")) {
            enabledGroups.put(groupId, true);
            chatHistories.putIfAbsent(groupId, new CopyOnWriteArrayList<>());

            // 初始化系统提示
            List<Message> history = chatHistories.get(groupId);
            history.add(new Message("system", "1.你是海龟汤出题者。海龟汤是是一种猜测情境型事件真相的智力游戏，其玩法是由出题者提出一个难以理解的事件，参与猜题者可以提出任何问题以试图缩小范围并找出事件背后真正的原因，但出题者仅能以“是”、“不是”或“没有关系”来回答问题。\n" +
                    "2.不能使用网上已有的题目！当你创造题目的时候，对于题面不要描述的太多，要求非常简短且没有细节描述！并且尽可能看起来离奇，令人细思极恐。而答案要情节曲折且过程复杂并且详细，不要有科幻和玄幻元素！逻辑合理，不要出现“特殊装置”、“特殊毒药”、“无色无味毒素”等这类道具，不要有超自然现象，不要有科幻和玄幻元素，也不要因为自身问题（疾病）而死亡，主要由于别人谋杀而死（答案里尽量还有其他人）。要求要能通过本格推理得到,符合现实" +
                    "并且符合人的逻辑思维思考模式，要求含有一定悬疑元素，可以包含暴力元素，不要有超自然现象，不要有玄幻科幻元素\n" +
                    "3.当有用户基本完整给出了答案梗概的时候，你就直接宣布答案,此时游戏结束。\n" +
                    "4.当游戏还没结束的时候，只有当用户说“直接把答案端上来罢”的时候你才能公布答案\n" +
                    "5.你出的题目一定要符合思维逻辑，符合自然原理，篇幅短，难度较高，有一定的悬疑因素。答案能够由用户提问后经过本格推理得出，不含有玄幻因素。"));
            event.getGroup().sendMessage(new PlainText("海龟汤模式已激活！正在生成题目..."));

            generateAndSendPuzzle(event);
            return;
        }

        if (content.equals("#shutdown")) {
            enabledGroups.remove(groupId);
            chatHistories.remove(groupId);
            event.getGroup().sendMessage(new PlainText("海龟汤模式已关闭"));
            return;
        }

        // 检查是否启用状态
        if (!enabledGroups.getOrDefault(groupId, false)) return;

        // 检查是否@机器人
        if (chain.stream().anyMatch(m ->
                m instanceof At && ((At) m).getTarget() == event.getBot().getId())) {
            // 提取纯文本内容
            String question = chain.stream()
                    .filter(m -> m instanceof PlainText)
                    .map(m -> ((PlainText) m).getContent())
                    .collect(Collectors.joining())
                    .trim();

            // 构建对话历史
            List<Message> history = chatHistories.get(groupId);
            history.add(new Message("user", question));

            try {
                String answer = getAIResponse(history);
                history.add(new Message("system", answer));

                // 发送回复并@用户
                event.getGroup().sendMessage(new At(event.getSender().getId())
                        .plus(" ")
                        .plus(answer));
            } catch (IOException e) {
                event.getGroup().sendMessage("思考中出了点问题，请再试一次");
            }
        }
    }

    private void generateAndSendPuzzle(GroupMessageEvent event) {
        long groupId = event.getGroup().getId();
        List<Message> history = chatHistories.get(groupId);

        // 添加用户提示
        history.add(new Message("user", "请帮我创建一个海龟汤题目，要求不要用网上已有的题目。题面不要描述细节，要求非常简短，并且尽可能看起来离奇。而答案要情节曲折且过程复杂，但能够通过本格推理得到。要能在我们日常生活联想到的范围里，符合现实，不要有超自然现象，不要有玄幻和科幻元素。不要出现“特殊装置”、“特殊毒药”、“无色无味毒素”等这类道具，不要突然因为自身问题死亡，主要由于别人谋杀而死（答案里尽量还有其他人）。逻辑合理，难度较大，并且符合人的逻辑思维思考模式，要求含有一定悬疑恐怖元素，可以包含暴力元素。"));

        try {
            String puzzle = getAIResponse(history);
            history.add(new Message("system", puzzle));

            // 发送题目到群聊
            event.getGroup().sendMessage(new PlainText("海龟汤题目已生成：\n" + puzzle));
        } catch (IOException e) {
            event.getGroup().sendMessage("生成题目时出错，请稍后再试");
        }
    }

    private String getAIResponse(List<Message> history) throws IOException {
        // 构造请求体
        ChatRequest requestBody = new ChatRequest("moonshot-v1-8k", history);
        String json = BotConfig.GSON.toJson(requestBody);

        Request request = new Request.Builder()
                .url(BotConfig.API_URL)
                .header("Authorization", "Bearer " + BotConfig.API_KEY)
                .post(RequestBody.create(json, JSON))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

            ChatResponse chatResponse = BotConfig.GSON.fromJson(
                    response.body().charStream(), ChatResponse.class);
            return chatResponse.choices.get(0).message.content;
        }
    }

    // 内部类用于JSON序列化
    private static class ChatRequest {
        String model;
        List<Message> messages;

        ChatRequest(String model, List<Message> messages) {
            this.model = model;
            this.messages = messages;
        }
    }

    private static class ChatResponse {
        List<Choice> choices;

        static class Choice {
            Message message;
        }
    }
}