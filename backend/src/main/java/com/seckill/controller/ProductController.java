package com.seckill.controller;

import com.seckill.common.Result;
import com.seckill.entity.SeckillProduct;
import com.seckill.service.ProductService;
import com.seckill.service.SeckillService;
import com.seckill.service.WaitlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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
        description = "查询所有已发布的演出列表，含最低价、渠道等扩展信息。"
    )
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list(
            @Parameter(description = "渠道筛选：seckill/special/regular，不传则返回全部")
            @RequestParam(required = false) String channel) {
        return Result.success(productService.listProductsWithInfo(channel));
    }

    @Operation(
        summary = "获取轮播演出",
        description = "查询首页轮播展示的演出列表。"
    )
    @GetMapping("/banners")
    public Result<List<SeckillProduct>> banners() {
        return Result.success(productService.listBannerProducts());
    }

    @Operation(
        summary = "获取单张票详情",
        description = "根据票种ID查询该票的详情，含名称、价格、渠道、购买状态、活动倒计时等。"
    )
    @GetMapping("/ticket/{ticketTypeId}")
    public Result<Map<String, Object>> ticketDetail(
            @Parameter(description = "票种ID", required = true, example = "1")
            @PathVariable Long ticketTypeId) {
        return Result.success(productService.getTicketDetail(ticketTypeId));
    }

    @Operation(
        summary = "获取演出详情",
        description = "根据演出ID查询演出详情，含基本信息、所有票种及实时购买状态。"
    )
    @GetMapping("/{productId}")
    public Result<Map<String, Object>> detail(
            @Parameter(description = "演出ID", required = true, example = "1")
            @PathVariable Long productId) {
        return Result.success(productService.getProductDetailWithTickets(productId));
    }

    @Operation(
        summary = "获取票种实时购买状态",
        description = "查询指定票种是否允许下单，返回 canPurchase 标识和原因说明。"
    )
    @GetMapping("/ticket/{ticketTypeId}/status")
    public Result<Map<String, Object>> ticketStatus(
            @Parameter(description = "票种ID", required = true, example = "1")
            @PathVariable Long ticketTypeId) {
        return Result.success(productService.getPurchaseStatus(ticketTypeId));
    }

    @Operation(
        summary = "获取实时库存",
        description = "从Redis缓存查询票种实时库存，前端轮询刷新库存展示。"
    )
    @GetMapping("/ticket/{ticketTypeId}/stock")
    public Result<Integer> getStock(
            @Parameter(description = "票种ID", required = true, example = "1")
            @PathVariable Long ticketTypeId) {
        return Result.success(seckillService.getRedisStock(ticketTypeId));
    }

    @Operation(
        summary = "获取即将开售/进行中的秒杀配置",
        description = "查询所有已发布票种中秒杀/特价渠道且有有效活动配置的记录。"
    )
    @GetMapping("/seckill-upcoming")
    public Result<List<Map<String, Object>>> seckillUpcoming() {
        return Result.success(productService.listUpcomingSeckillConfigs());
    }

    @Operation(
        summary = "候补登记",
        description = "票种库存不足时用户可登记候补，有票释放时按顺序通知。"
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
