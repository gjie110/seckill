package com.seckill.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seckill.common.Result;
import com.seckill.common.ResultCode;
import com.seckill.dto.OrderDetailDTO;
import com.seckill.entity.SeckillConfig;
import com.seckill.entity.SeckillOrder;
import com.seckill.entity.SeckillProduct;
import com.seckill.entity.SeckillUser;
import com.seckill.entity.TicketType;
import com.seckill.service.AuthService;
import com.seckill.service.OrderService;
import com.seckill.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "管理员接口", description = "管理员专用接口，包括演出管理、票种管理、订单管理等")
@Slf4j
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final ProductService productService;
    private final OrderService orderService;
    private final AuthService authService;

    @Operation(
        summary = "查询所有演出列表",
        description = "管理员查询系统中所有演出商品，包括已上架和已下架的演出。" +
                      "返回演出列表和总数，用于管理员控制台展示。"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "查询成功，返回演出列表")
    })
    @GetMapping("/products")
    public Result<Map<String, Object>> listProducts() {
        List<SeckillProduct> products = productService.listAllProducts();
        Map<String, Object> data = new HashMap<>();
        data.put("products", products);
        data.put("totalProducts", products.size());
        return Result.success(data);
    }

    @Operation(
        summary = "查询演出详情（管理员用）",
        description = "管理员查询演出完整信息，包括演出基本信息、所有票种列表、每个票种的秒杀活动配置。" +
                      "用于票种管理页面展示和编辑。"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "查询成功，返回演出详情"),
        @ApiResponse(responseCode = "404", description = "演出不存在")
    })
    @GetMapping("/product/{productId}")
    public Result<Map<String, Object>> productDetail(
            @Parameter(description = "演出ID", required = true, example = "1")
            @PathVariable Long productId) {
        SeckillProduct product = productService.getProduct(productId);
        List<TicketType> ticketTypes = productService.listAllTicketTypes(productId);
        Map<Long, SeckillConfig> configMap = new HashMap<>();
        for (TicketType t : ticketTypes) {
            SeckillConfig cfg = productService.getSeckillConfig(t.getId());
            if (cfg != null) {
                configMap.put(t.getId(), cfg);
            }
        }
        Map<String, Object> data = new HashMap<>();
        data.put("product", product);
        data.put("ticketTypes", ticketTypes);
        data.put("configMap", configMap);
        return Result.success(data);
    }

    @Operation(
        summary = "设置票种库存",
        description = "管理员设置指定票种的库存数量。" +
                      "同时更新数据库库存和Redis缓存库存，确保秒杀时库存数据一致。" +
                      "库存用于秒杀预扣，防止超卖。"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "库存设置成功"),
        @ApiResponse(responseCode = "404", description = "票种不存在")
    })
    @PostMapping("/ticket/{ticketTypeId}/stock")
    public Result<String> setStock(
            @Parameter(description = "票种ID", required = true, example = "1")
            @PathVariable Long ticketTypeId,
            @Parameter(description = "库存数量", required = true, example = "100")
            @RequestParam Integer stock) {
        productService.setStock(ticketTypeId, stock);
        return Result.success("库存已更新为 " + stock);
    }

    @Operation(
        summary = "发布票种",
        description = "管理员发布票种，将票种状态设置为已发布。" +
                      "发布后用户可以开始下单购买，同时将库存预热到Redis缓存，提高秒杀性能。"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "票种发布成功"),
        @ApiResponse(responseCode = "404", description = "票种不存在")
    })
    @PostMapping("/ticket/{ticketTypeId}/publish")
    public Result<String> publish(
            @Parameter(description = "票种ID", required = true, example = "1")
            @PathVariable Long ticketTypeId,
            @Parameter(description = "本次发布的库存数量，不传则发布全部", required = false)
            @RequestParam(required = false) Integer publishStock) {
        productService.publishTicket(ticketTypeId, publishStock);
        if (publishStock != null) {
            return Result.success("已发布 " + publishStock + " 张（剩余部分保留，可继续发布）");
        }
        return Result.success("已发布，用户可下单");
    }

    @Operation(
        summary = "下架票种",
        description = "管理员下架票种，将票种状态设置为已下架。" +
                      "下架后用户无法再下单购买，但不影响已生成的订单和已支付的票。"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "票种下架成功"),
        @ApiResponse(responseCode = "404", description = "票种不存在")
    })
    @PostMapping("/ticket/{ticketTypeId}/unpublish")
    public Result<String> unpublish(
            @Parameter(description = "票种ID", required = true, example = "1")
            @PathVariable Long ticketTypeId) {
        productService.unpublishTicket(ticketTypeId);
        return Result.success("已下架，用户无法下单");
    }

    @Operation(
        summary = "添加票种",
        description = "管理员为指定演出添加新的票种。" +
                      "设置票种名称、座位区域、销售渠道、价格和库存。" +
                      "渠道类型：seckill(秒杀票)、special(特价票)、regular(常规票)。"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "票种添加成功，返回新票种ID"),
        @ApiResponse(responseCode = "404", description = "演出不存在")
    })
    @PostMapping("/ticket/add")
    public Result<Long> addTicket(
            @Parameter(description = "演出ID", required = true, example = "1")
            @RequestParam Long productId,
            @Parameter(description = "票种名称", required = true, example = "A区VIP")
            @RequestParam String typeName,
            @Parameter(description = "座位区域描述", example = "内场前区")
            @RequestParam(required = false) String seatArea,
            @Parameter(description = "票价", required = true, example = "1999.00")
            @RequestParam BigDecimal price,
            @Parameter(description = "销售渠道", required = true, example = "seckill")
            @RequestParam String channel,
            @Parameter(description = "总库存", required = true, example = "100")
            @RequestParam Integer totalStock,
            @Parameter(description = "抢购数量（不能超过总库存）", required = true, example = "30")
            @RequestParam Integer seckillStock) {
        if (seckillStock > totalStock) {
            return Result.error("抢购数量不能超过总库存");
        }
        TicketType tt = productService.addTicketType(productId, typeName, seatArea, price, channel, totalStock, seckillStock);
        return Result.success(tt.getId());
    }

    @Operation(
        summary = "编辑票种",
        description = "管理员修改票种信息，包括名称、座位区域、价格、渠道。" +
                      "不能修改库存（使用库存调整接口）。"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "更新成功"),
        @ApiResponse(responseCode = "404", description = "票种不存在")
    })
    @PostMapping("/ticket/{ticketTypeId}/update")
    public Result<String> updateTicket(
            @Parameter(description = "票种ID", required = true)
            @PathVariable Long ticketTypeId,
            @Parameter(description = "票种名称")
            @RequestParam(required = false) String typeName,
            @Parameter(description = "座位区域描述")
            @RequestParam(required = false) String seatArea,
            @Parameter(description = "票价")
            @RequestParam(required = false) BigDecimal price,
            @Parameter(description = "销售渠道")
            @RequestParam(required = false) String channel) {
        productService.updateTicketType(ticketTypeId, typeName, seatArea, price, channel);
        return Result.success("更新成功");
    }

    @Operation(
        summary = "删除票种",
        description = "管理员删除票种，使用逻辑删除（软删除），将deleted字段设为1。" +
                      "如果票种已有订单（soldStock > 0），则不允许删除。"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "删除成功"),
        @ApiResponse(responseCode = "404", description = "票种不存在"),
        @ApiResponse(responseCode = "400", description = "该票种已有订单，无法删除")
    })
    @PostMapping("/ticket/{ticketTypeId}/delete")
    public Result<String> deleteTicket(
            @Parameter(description = "票种ID", required = true)
            @PathVariable Long ticketTypeId) {
        productService.deleteTicketType(ticketTypeId);
        return Result.success("删除成功");
    }

    @Operation(
        summary = "库存调整",
        description = "管理员手动调整票种库存，用于应急场景。" +
                      "支持增加库存和扣减库存操作。" +
                      "扣减库存时需要检查可用库存是否充足。"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "库存调整成功"),
        @ApiResponse(responseCode = "404", description = "票种不存在"),
        @ApiResponse(responseCode = "400", description = "库存不足，无法扣减")
    })
    @PostMapping("/ticket/{ticketTypeId}/stock/adjust")
    public Result<String> adjustStock(
            @Parameter(description = "票种ID", required = true)
            @PathVariable Long ticketTypeId,
            @Parameter(description = "调整数量（必须大于0）", required = true, example = "10")
            @RequestParam Integer adjustment,
            @Parameter(description = "操作类型：increase-增加，decrease-扣减", required = true, example = "increase")
            @RequestParam String operation) {
        productService.adjustStock(ticketTypeId, adjustment, operation);
        return Result.success("库存调整成功");
    }

    @Operation(
        summary = "配置秒杀活动",
        description = "管理员配置票种的秒杀活动时间窗口和购买限制。" +
                      "设置活动开始时间、结束时间、每用户最大购买数量。" +
                      "只有在活动时间窗口内，用户才能进行秒杀下单。" +
                      "时间格式：yyyy-MM-dd HH:mm"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "活动配置成功"),
        @ApiResponse(responseCode = "400", description = "时间格式错误"),
        @ApiResponse(responseCode = "404", description = "票种不存在")
    })
    @PostMapping("/ticket/{ticketTypeId}/config")
    public Result<String> setConfig(
            @Parameter(description = "票种ID", required = true, example = "1")
            @PathVariable Long ticketTypeId,
            @Parameter(description = "活动开始时间", required = true, example = "2026-06-20 10:00")
            @RequestParam String startTime,
            @Parameter(description = "活动结束时间", required = true, example = "2026-06-20 22:00")
            @RequestParam String endTime,
            @Parameter(description = "每用户最大购买数量", example = "3")
            @RequestParam(defaultValue = "3") Integer maxPerUser) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Date start = sdf.parse(startTime);
            Date end = sdf.parse(endTime);
            productService.setSeckillConfig(ticketTypeId, start, end, maxPerUser);
            return Result.success("活动配置已更新");
        } catch (Exception e) {
            log.warn("解析时间失败: {}", e.getMessage());
            return Result.error("时间格式错误，请使用 yyyy-MM-dd HH:mm:ss");
        }
    }

    @Operation(summary = "仅修改票种限购数量", description = "不修改时间，仅更新每人限购数量")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "修改成功"),
        @ApiResponse(responseCode = "404", description = "票种不存在或无活动配置")
    })
    @PostMapping("/ticket/{ticketTypeId}/max-per-user")
    public Result<String> setMaxPerUser(
            @Parameter(description = "票种ID", required = true)
            @PathVariable Long ticketTypeId,
            @Parameter(description = "每人限购数", required = true, example = "3")
            @RequestParam Integer maxPerUser) {
        productService.setMaxPerUser(ticketTypeId, maxPerUser);
        return Result.success("限购数量已更新");
    }

    @Operation(
        summary = "手动结束票种活动",
        description = "管理员将指定票种的活动配置强制标记为已结束（status=2），" +
                      "并把 endTime 同步设为当前时间。已结束的票种在前端详情页会显示『已结束』且不可购买。"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "已成功结束活动"),
        @ApiResponse(responseCode = "404", description = "票种或活动配置不存在")
    })
    @PostMapping("/ticket/{ticketTypeId}/end")
    public Result<String> endActivity(
            @Parameter(description = "票种ID", required = true, example = "7")
            @PathVariable Long ticketTypeId) {
        productService.forceEndActivity(ticketTypeId);
        return Result.success("活动已强制结束");
    }

    @Operation(
        summary = "修复五月天C票活动数据（内部测试用）",
        description = "将五月天C票（ticket_type_id=7）的 seckill_config 强制设为已结束。" +
                      "用于前端验证『已结束』状态显示是否正确。"
    )
    @PostMapping("/fix/wuyutian-c-ticket")
    public Result<String> fixWuyutianCTicket() {
        int rows = productService.fixWuyutianCTicketAsEnded();
        return Result.success("修复完成，影响行数=" + rows);
    }

    @Operation(
        summary = "新增演出",
        description = "管理员新增演出，填写演出基本信息（名称、描述、场馆、时间、海报）。" +
                      "新增后状态默认为草稿（未发布），需要手动发布。"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "新增成功，返回演出ID"),
        @ApiResponse(responseCode = "400", description = "参数错误")
    })
    @PostMapping("/product/add")
    public Result<Long> addProduct(
            @Parameter(description = "演出名称", required = true)
            @RequestParam String name,
            @Parameter(description = "演出描述")
            @RequestParam(required = false) String description,
            @Parameter(description = "演出场馆", required = true)
            @RequestParam String venue,
            @Parameter(description = "演出时间，格式：yyyy-MM-dd HH:mm", required = true)
            @RequestParam String showTime,
            @Parameter(description = "海报图片URL")
            @RequestParam(required = false) String posterUrl,
            @Parameter(description = "是否在轮播中：0-否，1-是")
            @RequestParam(required = false, defaultValue = "0") Integer isBanner) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Date showDate = sdf.parse(showTime);
            SeckillProduct product = productService.addProduct(name, description, venue, showDate, posterUrl, isBanner);
            return Result.success(product.getId());
        } catch (Exception e) {
            log.warn("解析时间失败: {}", e.getMessage());
            return Result.error("时间格式错误，请使用 yyyy-MM-dd HH:mm:ss");
        }
    }

    @Operation(
        summary = "编辑演出",
        description = "管理员修改演出信息，支持修改名称、描述、场馆、时间、海报等。"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "更新成功"),
        @ApiResponse(responseCode = "404", description = "演出不存在")
    })
    @PostMapping("/product/{productId}/update")
    public Result<String> updateProduct(
            @Parameter(description = "演出ID", required = true)
            @PathVariable Long productId,
            @Parameter(description = "演出名称")
            @RequestParam(required = false) String name,
            @Parameter(description = "演出描述")
            @RequestParam(required = false) String description,
            @Parameter(description = "演出场馆")
            @RequestParam(required = false) String venue,
            @Parameter(description = "演出时间，格式：yyyy-MM-dd HH:mm")
            @RequestParam(required = false) String showTime,
            @Parameter(description = "海报图片URL")
            @RequestParam(required = false) String posterUrl,
            @Parameter(description = "状态：0-草稿，1-已发布")
            @RequestParam(required = false) Integer status,
            @Parameter(description = "是否在轮播中：0-否，1-是")
            @RequestParam(required = false) Integer isBanner) {
        try {
            Date showDate = null;
            if (showTime != null && !showTime.isEmpty()) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                showDate = sdf.parse(showTime);
            }
            productService.updateProduct(productId, name, description, venue, showDate, posterUrl, status, isBanner);
            return Result.success("更新成功");
        } catch (Exception e) {
            log.warn("解析时间失败: {}", e.getMessage());
            return Result.error("时间格式错误，请使用 yyyy-MM-dd HH:mm:ss");
        }
    }

    @Operation(
        summary = "删除演出",
        description = "管理员删除演出，使用逻辑删除（软删除），将deleted字段设为1。" +
                      "删除后演出不再显示，但数据保留在数据库中。"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "删除成功"),
        @ApiResponse(responseCode = "404", description = "演出不存在")
    })
    @PostMapping("/product/{productId}/delete")
    public Result<String> deleteProduct(
            @Parameter(description = "演出ID", required = true)
            @PathVariable Long productId) {
        productService.deleteProduct(productId);
        return Result.success("删除成功");
    }

    @Operation(
        summary = "发布演出",
        description = "管理员发布演出，将演出状态设为已发布。" +
                      "发布后用户可以在前端看到该演出，同时预热票种库存到Redis。"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "发布成功"),
        @ApiResponse(responseCode = "404", description = "演出不存在")
    })
    @PostMapping("/product/{productId}/publish")
    public Result<String> publishProduct(
            @Parameter(description = "演出ID", required = true)
            @PathVariable Long productId) {
        productService.publishProduct(productId);
        return Result.success("发布成功");
    }

    @Operation(
        summary = "下架演出",
        description = "管理员下架演出，将演出状态设为草稿。" +
                      "下架后用户无法在前端看到该演出，但不影响已生成的订单和票。"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "下架成功"),
        @ApiResponse(responseCode = "404", description = "演出不存在")
    })
    @PostMapping("/product/{productId}/unpublish")
    public Result<String> unpublishProduct(
            @Parameter(description = "演出ID", required = true)
            @PathVariable Long productId) {
        productService.unpublishProduct(productId);
        return Result.success("下架成功");
    }

    @Operation(
        summary = "搜索演出",
        description = "管理员搜索演出，支持按名称关键词搜索和按状态筛选。" +
                      "状态：0-草稿，1-已发布。"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "搜索成功，返回演出列表")
    })
    @GetMapping("/product/search")
    public Result<List<SeckillProduct>> searchProducts(
            @Parameter(description = "关键词（演出名称）")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "状态：0-草稿，1-已发布")
            @RequestParam(required = false) Integer status) {
        List<SeckillProduct> products = productService.searchProducts(keyword, status);
        return Result.success(products);
    }

    @Operation(
        summary = "查询所有订单列表",
        description = "管理员查询系统中所有用户的订单。" +
                      "支持按订单号或用户ID搜索，按状态筛选。" +
                      "订单状态：0-待支付、1-已支付、2-已取消、3-已超时。"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "查询成功，返回订单列表")
    })
    @GetMapping("/orders")
    public Result<Map<String, Object>> listOrders(
            @Parameter(description = "关键词（订单号/用户ID）")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "状态：0-待支付、1-已支付、2-已取消、3-已超时")
            @RequestParam(required = false) Integer status,
            @Parameter(description = "页码（从1开始）")
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @Parameter(description = "每页大小")
            @RequestParam(required = false, defaultValue = "10") Integer size) {
        Page<SeckillOrder> pageResult = orderService.searchOrdersPage(keyword, status, page, size);
        Map<String, Object> data = new HashMap<>();
        data.put("orders", pageResult.getRecords());
        data.put("total", pageResult.getTotal());
        data.put("page", page);
        data.put("size", size);
        data.put("pages", pageResult.getPages());
        return Result.success(data);
    }

    @Operation(
        summary = "查询订单详情",
        description = "管理员查询订单详细信息，包括用户信息、演出信息、票种信息等。"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "查询成功，返回订单详情"),
        @ApiResponse(responseCode = "404", description = "订单不存在")
    })
    @GetMapping("/order/{orderNo}")
    public Result<OrderDetailDTO> orderDetail(
            @Parameter(description = "订单号", required = true)
            @PathVariable String orderNo) {
        OrderDetailDTO detail = orderService.getOrderDetail(orderNo);
        if (detail == null) {
            return Result.error("订单不存在");
        }
        return Result.success(detail);
    }

    @Operation(
        summary = "强制取消订单",
        description = "管理员强制取消订单，不受订单状态限制。" +
                      "如果订单已支付，将释放库存并通知候补用户。" +
                      "取消后订单状态变为2（已取消）。"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "取消成功"),
        @ApiResponse(responseCode = "404", description = "订单不存在"),
        @ApiResponse(responseCode = "400", description = "订单已取消或已超时")
    })
    @PostMapping("/order/{orderNo}/force-cancel")
    public Result<String> forceCancelOrder(
            @Parameter(description = "订单号", required = true)
            @PathVariable String orderNo,
            @Parameter(description = "取消原因")
            @RequestParam(required = false, defaultValue = "管理员强制取消") String reason) {
        orderService.adminForceCancelOrder(orderNo, reason);
        return Result.success("强制取消成功");
    }

    // ==================== 用户管理 ====================

    @Operation(summary = "查询所有用户列表（支持分页和搜索）")
    @GetMapping("/users")
    public Result<Map<String, Object>> listUsers(
            @Parameter(description = "搜索关键词（用户名/手机号/邮箱）")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "页码（从1开始）")
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @Parameter(description = "每页大小")
            @RequestParam(required = false, defaultValue = "10") Integer size) {
        Page<SeckillUser> pageResult = authService.listUsersPage(keyword, page, size);
        Map<String, Object> data = new HashMap<>();
        data.put("users", pageResult.getRecords());
        data.put("total", pageResult.getTotal());
        data.put("page", page);
        data.put("size", size);
        data.put("pages", pageResult.getPages());
        return Result.success(data);
    }

    @Operation(summary = "新增用户（注册）")
    @PostMapping("/user/add")
    public Result<String> addUser(
            @Parameter(description = "用户名", required = true)
            @RequestParam String username,
            @Parameter(description = "密码", required = true)
            @RequestParam String password,
            @Parameter(description = "手机号", required = false)
            @RequestParam(required = false) String phone,
            @Parameter(description = "邮箱", required = false)
            @RequestParam(required = false) String email,
            @Parameter(description = "角色：user/admin", required = false)
            @RequestParam(required = false, defaultValue = "user") String role) {
        authService.registerUser(username, password, phone, email, role);
        return Result.success("用户 [" + username + "] 注册成功");
    }

    @Operation(summary = "启用/禁用用户")
    @PostMapping("/user/{userId}/status")
    public Result<String> setUserStatus(
            @Parameter(description = "用户ID", required = true)
            @PathVariable Long userId,
            @Parameter(description = "状态：0-正常 1-禁用", required = true)
            @RequestParam Integer status) {
        authService.setUserStatus(userId, status);
        return Result.success(status == 1 ? "用户已禁用" : "用户已启用");
    }

    @Operation(summary = "修改用户角色")
    @PostMapping("/user/{userId}/role")
    public Result<String> setUserRole(
            @Parameter(description = "用户ID", required = true)
            @PathVariable Long userId,
            @Parameter(description = "角色：user/admin", required = true)
            @RequestParam String role) {
        authService.setUserRole(userId, role);
        return Result.success("用户角色已更新为：" + ("admin".equals(role) ? "管理员" : "普通用户"));
    }

    @Operation(summary = "编辑用户信息", description = "修改用户名、手机号、邮箱、密码；不传的字段保持原值不变")
    @PostMapping("/user/{userId}/update")
    public Result<String> updateUser(
            @Parameter(description = "用户ID", required = true)
            @PathVariable Long userId,
            @Parameter(description = "用户名", required = false)
            @RequestParam(required = false) String username,
            @Parameter(description = "手机号", required = false)
            @RequestParam(required = false) String phone,
            @Parameter(description = "邮箱", required = false)
            @RequestParam(required = false) String email,
            @Parameter(description = "新密码（为空则不改）", required = false)
            @RequestParam(required = false) String password) {
        try {
            authService.updateUser(userId, username, phone, email, password);
            return Result.success("用户信息已更新");
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    @Operation(summary = "删除用户")
    @PostMapping("/user/{userId}/delete")
    public Result<String> deleteUser(
            @Parameter(description = "用户ID", required = true)
            @PathVariable Long userId) {
        authService.deleteUser(userId);
        return Result.success("用户已删除");
    }
}