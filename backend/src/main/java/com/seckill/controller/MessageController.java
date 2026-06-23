package com.seckill.controller;

import com.seckill.common.Result;
import com.seckill.entity.Message;
import com.seckill.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "消息通知", description = "用户消息通知相关接口，包括消息列表查询、标记已读、未读数量统计")
@RestController
@RequestMapping("/message")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @Operation(
        summary = "查询消息列表",
        description = "查询指定用户的所有消息通知，按时间倒序返回。"
    )
    @GetMapping("/list")
    public Result<List<Message>> list(
            @Parameter(description = "用户ID", example = "1001")
            @RequestParam(defaultValue = "1001") Long userId) {
        List<Message> messages = messageService.listMessages(userId);
        return Result.success(messages);
    }

    @Operation(
        summary = "标记消息已读",
        description = "将指定用户的所有未读消息标记为已读。"
    )
    @PostMapping("/read")
    public Result<String> read(
            @Parameter(description = "用户ID", example = "1001")
            @RequestParam(defaultValue = "1001") Long userId) {
        messageService.markAllRead(userId);
        return Result.success("已全部标记为已读");
    }

    @Operation(
        summary = "查询未读消息数量",
        description = "查询用户未读消息数量，用于前端导航栏红点提醒。"
    )
    @GetMapping("/unread")
    public Result<Long> unread(
            @Parameter(description = "用户ID", example = "1001")
            @RequestParam(defaultValue = "1001") Long userId) {
        return Result.success(messageService.getUnreadCount(userId));
    }

    @Operation(
        summary = "查询单条消息详情",
        description = "根据消息ID查询单条消息的详细内容。"
    )
    @GetMapping("/{id}")
    public Result<Message> detail(@PathVariable Long id) {
        Message msg = messageService.getMessageById(id);
        if (msg == null) {
            return Result.error("消息不存在");
        }
        return Result.success(msg);
    }

    @Operation(
        summary = "标记单条消息已读",
        description = "将指定消息标记为已读状态。"
    )
    @PostMapping("/{id}/read")
    public Result<String> readOne(@PathVariable Long id) {
        messageService.markRead(id);
        return Result.success("已读");
    }
}
