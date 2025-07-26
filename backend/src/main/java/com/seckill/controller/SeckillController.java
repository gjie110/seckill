package com.seckill.controller;

import com.seckill.common.Result;
import com.seckill.dto.SeckillRequest;
import com.seckill.service.SeckillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "秒杀下单", description = "高并发秒杀下单接口，使用Redis Lua脚本原子预扣库存，RocketMQ异步下单削峰填谷")
@Slf4j
@RestController
@RequestMapping("/seckill")
@RequiredArgsConstructor
public class SeckillController {

    private final SeckillService seckillService;

    @Operation(
        summary = "秒杀下单",
        description = "高并发秒杀下单核心接口。" +
                      "技术实现：使用Redis Lua脚本进行原子库存预扣，防止超卖；" +
                      "发送RocketMQ消息实现异步下单，削峰填谷；" +
                      "用户限流：滑动窗口算法限制每用户请求频率。" +
                      "返回订单号，用户需在订单超时时间内完成支付。"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "下单成功，返回订单号"),
        @ApiResponse(responseCode = "1003", description = "秒杀活动还未开始"),
        @ApiResponse(responseCode = "1004", description = "秒杀活动已结束"),
        @ApiResponse(responseCode = "2001", description = "库存不足"),
        @ApiResponse(responseCode = "2002", description = "您已购买过该商品，每人限购"),
        @ApiResponse(responseCode = "4001", description = "请求过于频繁，请稍后再试"),
        @ApiResponse(responseCode = "501", description = "服务繁忙，请稍后再试（Redis连接失败降级）")
    })
    @PostMapping("/order")
    public Result<String> seckillOrder(@Valid @org.springframework.web.bind.annotation.RequestBody SeckillRequest request) {
        log.info("收到秒杀请求, userId={}, productId={}",
                request.getUserId(), request.getProductId());
        String orderNo = seckillService.executeOrder(request);
        return Result.success("下单成功，请尽快支付", orderNo);
    }
}