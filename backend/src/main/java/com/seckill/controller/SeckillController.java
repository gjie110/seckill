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
        description = "高并发秒杀下单，Redis Lua原子预扣库存，RocketMQ异步削峰。"
    )
    @PostMapping("/order")
    public Result<String> seckillOrder(@Valid @org.springframework.web.bind.annotation.RequestBody SeckillRequest request) {
        log.info("收到秒杀请求, userId={}, productId={}",
                request.getUserId(), request.getProductId());
        String orderNo = seckillService.executeOrder(request);
        return Result.success("下单成功，请尽快支付", orderNo);
    }
}