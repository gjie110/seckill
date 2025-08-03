package com.seckill.controller;

import com.seckill.common.Result;
import com.seckill.entity.SeckillProduct;
import com.seckill.entity.TicketType;
import com.seckill.service.ProductService;
import com.seckill.service.SeckillService;
import com.seckill.service.WaitlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "演出商品", description = "演出商品和票种相关查询接口，包括演出列表、演出详情、票种信息、实时库存查询")
@RestController
@RequestMapping("/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final SeckillService seckillService;
    private final WaitlistService waitlistService;

    @Operation(
        summary = "获取演出列表",
        description = "查询所有已发布的演出商品列表（含最低价、渠道等扩展信息）。" +
                      "支持按渠道（seckill/special/regular）筛选。"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "查询成功，返回演出列表")
    })
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list(
            @Parameter(description = "渠道筛选：seckill/special/regular，不传则返回全部")
            @RequestParam(required = false) String channel) {
        return Result.success(productService.listProductsWithInfo(channel));
    }

    @Operation(
        summary = "获取轮播演出",
        description = "查询需要在首页轮播中展示的演出列表（管理员标记为轮播的演出）。" +
                      "用户端首页 Banner 使用该接口数据。"
    )
    @GetMapping("/banners")
    public Result<List<SeckillProduct>> banners() {
        return Result.success(productService.listBannerProducts());
    }

    @Operation(
        summary = "获取单张票详情",
        description = "根据票种ID获取该票的独立详情页面数据：包含票种名称、座位区域、价格、渠道、" +
                      "所属演出的基本信息，以及实时购买状态（是否可买、活动倒计时、原因说明）。" +
                      "点击首页/列表页的某张票时使用此接口，只展示这一张票而不展示其他票种。"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "查询成功，返回单张票详情"),
        @ApiResponse(responseCode = "404", description = "票种不存在或已下架")
    })
    @GetMapping("/ticket/{ticketTypeId}")
    public Result<Map<String, Object>> ticketDetail(
            @Parameter(description = "票种ID", required = true, example = "1")
            @PathVariable Long ticketTypeId) {
        return Result.success(productService.getTicketDetail(ticketTypeId));
    }

    @Operation(
        summary = "获取演出详情",
        description = "根据演出ID查询演出详细信息（含每个票种的实时购买状态）。" +
                      "返回演出基本信息、最低价、以及每个票种的渠道/库存/活动状态。"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "查询成功，返回演出详情和票种列表"),
        @ApiResponse(responseCode = "404", description = "演出不存在")
    })
    @GetMapping("/{productId}")
    public Result<Map<String, Object>> detail(
            @Parameter(description = "演出ID", required = true, example = "1")
            @PathVariable Long productId) {
        return Result.success(productService.getProductDetailWithTickets(productId));
    }

    @Operation(
        summary = "获取票种实时购买状态",
        description = "查询指定票种是否允许下单：" +
                      "常规票：只要上架+有库存即可购买；" +
                      "秒杀/特价票：需在活动时间内且有库存；" +
                      "返回 canPurchase 标识和原因说明。"
    )
    @GetMapping("/ticket/{ticketTypeId}/status")
    public Result<Map<String, Object>> ticketStatus(
            @Parameter(description = "票种ID", required = true, example = "1")
            @PathVariable Long ticketTypeId) {
        return Result.success(productService.getPurchaseStatus(ticketTypeId));
    }

    @Operation(
        summary = "获取实时库存",
        description = "从Redis缓存中查询指定票种的实时库存数量。" +
                      "高并发场景下避免直接查询数据库，提高性能。" +
                      "库存数据由CacheWarmupService预热到Redis。" +
                      "前端可轮询此接口实时刷新库存展示。"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "查询成功，返回实时库存数量"),
        @ApiResponse(responseCode = "404", description = "票种不存在")
    })
    @GetMapping("/ticket/{ticketTypeId}/stock")
    public Result<Integer> getStock(
            @Parameter(description = "票种ID", required = true, example = "1")
            @PathVariable Long ticketTypeId) {
        return Result.success(seckillService.getRedisStock(ticketTypeId));
    }

    @Operation(
        summary = "获取即将开售/进行中的秒杀配置",
        description = "查询所有已发布票种中，秒杀/特价渠道且有有效活动配置的记录。" +
                      "返回每个配置的票种信息、演出信息、抢购开始/结束时间。" +
                      "用于前端首页倒计时展示和秒杀入口。"
    )
    @GetMapping("/seckill-upcoming")
    public Result<List<Map<String, Object>>> seckillUpcoming() {
        return Result.success(productService.listUpcomingSeckillConfigs());
    }

    @Operation(
        summary = "候补登记",
        description = "当票种库存不足时，用户可登记候补。" +
                      "当该票种有库存释放（订单取消/超时）时，系统会按候补顺序通知用户。" +
                      "同一用户对同一票种不允许重复进行候补。"
    )
    @PostMapping("/ticket/waitlist")
    public Result<String> applyWaitlist(@RequestBody Map<String, Object> body) {
        Long userId = body.get("userId") != null ? Long.valueOf(String.valueOf(body.get("userId"))) : null;
        Long ticketTypeId = body.get("ticketTypeId") != null ? Long.valueOf(String.valueOf(body.get("ticketTypeId"))) : null;
        Long productId = body.get("productId") != null ? Long.valueOf(String.valueOf(body.get("productId"))) : null;
        waitlistService.joinWaitlist(userId, productId, ticketTypeId);
        return Result.success("候补登记成功，有票时将第一时间通知您");
    }
}