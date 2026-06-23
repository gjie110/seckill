package com.seckill.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seckill.common.Result;
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
        description = "管理员查询系统中所有演出，含已上架和已下架的。"
    )
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
        description = "管理员查询演出完整信息，含所有票种和秒杀活动配置。"
    )
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
        description = "管理员设置票种库存，同时更新数据库和Redis。"
    )
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
        description = "管理员发布票种，用户可开始下单购买，库存预热到Redis。"
    )
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
        description = "管理员下架票种，用户无法再下单购买。"
    )
    @PostMapping("/ticket/{ticketTypeId}/unpublish")
    public Result<String> unpublish(
            @Parameter(description = "票种ID", required = true, example = "1")
            @PathVariable Long ticketTypeId) {
        productService.unpublishTicket(ticketTypeId);
        return Result.success("已下架，用户无法下单");
    }

    @Operation(
        summary = "添加票种",
        description = "管理员为演出新增票种，设置名称、区域、价格、渠道和库存。"
    )
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
        description = "管理员修改票种名称、区域、价格、渠道，库存使用调整接口。"
    )
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
        description = "管理员软删除票种，已有订单的票种不允许删除。"
    )
    @PostMapping("/ticket/{ticketTypeId}/delete")
    public Result<String> deleteTicket(
            @Parameter(description = "票种ID", required = true)
            @PathVariable Long ticketTypeId) {
        productService.deleteTicketType(ticketTypeId);
        return Result.success("删除成功");
    }

    @Operation(
        summary = "库存调整",
        description = "管理员手动调整票种库存，支持增加或扣减。"
    )
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
        description = "配置票种秒杀活动时间窗口、每人限购数量，只有活动时间内才能秒杀。"
    )
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

    @Operation(
        summary = "仅修改票种限购数量",
        description = "不修改时间，仅更新每人限购数量。"
    )
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
        description = "管理员强制结束票种秒杀活动，前端详情页显示已结束且不可购买。"
    )
    @PostMapping("/ticket/{ticketTypeId}/end")
    public Result<String> endActivity(
            @Parameter(description = "票种ID", required = true, example = "7")
            @PathVariable Long ticketTypeId) {
        productService.forceEndActivity(ticketTypeId);
        return Result.success("活动已强制结束");
    }

    @Operation(
        summary = "修复五月天C票活动数据（内部测试用）",
        description = "将票种ID=7的秒杀配置强制设为已结束状态。"
    )
    @PostMapping("/fix/wuyutian-c-ticket")
    public Result<String> fixWuyutianCTicket() {
        int rows = productService.fixWuyutianCTicketAsEnded();
        return Result.success("修复完成，影响行数=" + rows);
    }

    @Operation(
        summary = "新增演出",
        description = "管理员新增演出，填写名称、场馆、时间、海报等信息。"
    )
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
        description = "管理员软删除演出，数据保留在数据库。"
    )
    @PostMapping("/product/{productId}/delete")
    public Result<String> deleteProduct(
            @Parameter(description = "演出ID", required = true)
            @PathVariable Long productId) {
        productService.deleteProduct(productId);
        return Result.success("删除成功");
    }

    @Operation(
        summary = "发布演出",
        description = "管理员发布演出，用户可在前端看到，同时预热票种库存到Redis。"
    )
    @PostMapping("/product/{productId}/publish")
    public Result<String> publishProduct(
            @Parameter(description = "演出ID", required = true)
            @PathVariable Long productId) {
        productService.publishProduct(productId);
        return Result.success("发布成功");
    }

    @Operation(
        summary = "下架演出",
        description = "管理员下架演出，用户无法在前端看到。"
    )
    @PostMapping("/product/{productId}/unpublish")
    public Result<String> unpublishProduct(
            @Parameter(description = "演出ID", required = true)
            @PathVariable Long productId) {
        productService.unpublishProduct(productId);
        return Result.success("下架成功");
    }

    @Operation(
        summary = "搜索演出",
        description = "管理员搜索演出，按名称关键词搜索和按状态筛选。"
    )
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
        description = "管理员分页查询所有订单，支持关键词搜索和状态筛选。"
    )
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
        description = "管理员查询订单详情，含用户、演出、票种等信息。"
    )
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
        description = "管理员强制取消订单，已支付则释放库存并通知候补。"
    )
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

    @Operation(
        summary = "查询所有用户列表（支持分页和搜索）",
        description = "管理员分页查询所有注册用户，支持按用户名/手机号/邮箱搜索。"
    )
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

    @Operation(
        summary = "新增用户（注册）",
        description = "管理员手动创建新用户账号，设置用户名、密码、角色等信息。"
    )
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

    @Operation(
        summary = "启用/禁用用户",
        description = "管理员启用或禁用指定用户的登录权限。"
    )
    @PostMapping("/user/{userId}/status")
    public Result<String> setUserStatus(
            @Parameter(description = "用户ID", required = true)
            @PathVariable Long userId,
            @Parameter(description = "状态：0-正常 1-禁用", required = true)
            @RequestParam Integer status) {
        authService.setUserStatus(userId, status);
        return Result.success(status == 1 ? "用户已禁用" : "用户已启用");
    }

    @Operation(
        summary = "修改用户角色",
        description = "管理员修改用户角色，在普通用户和管理员之间切换。"
    )
    @PostMapping("/user/{userId}/role")
    public Result<String> setUserRole(
            @Parameter(description = "用户ID", required = true)
            @PathVariable Long userId,
            @Parameter(description = "角色：user/admin", required = true)
            @RequestParam String role) {
        authService.setUserRole(userId, role);
        return Result.success("用户角色已更新为：" + ("admin".equals(role) ? "管理员" : "普通用户"));
    }

    @Operation(
        summary = "编辑用户信息",
        description = "修改用户名、手机号、邮箱、密码，不传的字段保持原值不变。"
    )
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

    @Operation(
        summary = "删除用户",
        description = "管理员删除指定用户账号。"
    )
    @PostMapping("/user/{userId}/delete")
    public Result<String> deleteUser(
            @Parameter(description = "用户ID", required = true)
            @PathVariable Long userId) {
        authService.deleteUser(userId);
        return Result.success("用户已删除");
    }
}
