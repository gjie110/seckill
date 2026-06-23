package com.seckill.controller;

import com.seckill.common.Result;
import com.seckill.common.ResultCode;
import com.seckill.common.SeckillException;
import com.seckill.dto.OrderDetailDTO;
import com.seckill.dto.SeckillRequest;
import com.seckill.entity.SeckillOrder;
import com.seckill.entity.UserTicket;
import com.seckill.entity.Waitlist;
import com.seckill.service.OrderService;
import com.seckill.service.SeckillService;
import com.seckill.service.UserTicketService;
import com.seckill.service.WaitlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "订单管理", description = "订单相关接口，包括订单创建、查询、支付、取消，以及用户票查询、候补记录查询")
@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {

    private final SeckillService seckillService;
    private final OrderService orderService;
    private final UserTicketService userTicketService;
    private final WaitlistService waitlistService;

    @Operation(
        summary = "创建订单",
        description = "创建新订单，返回订单号供后续支付。"
    )
    @PostMapping("/create")
    public Result<String> createOrder(@org.springframework.web.bind.annotation.RequestBody SeckillRequest request) {
        if (request.getUserId() == null) {
            request.setUserId(1001L);
        }
        if (request.getIdCard() == null || !request.getIdCard().matches("(^[0-9]{15}$)|(^[0-9]{18}$)|(^[0-9]{17}[0-9Xx]$)")) {
            throw new SeckillException(ResultCode.PARAM_ERROR, "身份证格式不正确（需为15位或18位）");
        }
        String orderNo = seckillService.executeOrder(request);
        return Result.success(orderNo);
    }

    @Operation(
        summary = "查询订单列表",
        description = "根据用户ID查询该用户的所有订单，含演出、票种、状态等信息。"
    )
    @GetMapping("/list")
    public Result<List<SeckillOrder>> orderList(
            @Parameter(description = "用户ID", required = true, example = "1001")
            @RequestParam Long userId) {
        if (userId == null) {
            throw new SeckillException(ResultCode.PARAM_ERROR, "用户ID不能为空");
        }
        List<SeckillOrder> orders = orderService.listOrders(userId);
        return Result.success(orders);
    }

    @Operation(
        summary = "查询订单详情",
        description = "根据订单号查询订单完整信息，含演出、票种、价格、状态等。"
    )
    @GetMapping("/{orderNo}")
    public Result<OrderDetailDTO> orderDetail(
            @Parameter(description = "订单号", required = true, example = "20260616123456789012")
            @PathVariable String orderNo) {
        OrderDetailDTO detail = orderService.getOrderDetail(orderNo);
        if (detail == null) {
            throw new SeckillException(ResultCode.ORDER_NOT_FOUND);
        }
        return Result.success(detail);
    }

    @Operation(
        summary = "支付订单",
        description = "用户支付指定订单，完成购票。"
    )
    @PostMapping("/{orderNo}/pay")
    public Result<String> payOrder(
            @Parameter(description = "订单号", required = true, example = "20260616123456789012")
            @PathVariable String orderNo,
            @Parameter(description = "用户ID", required = true, example = "1001")
            @RequestParam Long userId) {
        if (userId == null) {
            throw new SeckillException(ResultCode.PARAM_ERROR, "用户ID不能为空");
        }
        List<UserTicket> tickets = orderService.payOrder(orderNo, userId);
        return Result.success("支付成功！共" + tickets.size() + "张票");
    }

    @Operation(
        summary = "取消订单",
        description = "用户取消未支付的订单，释放Redis预扣库存，通知候补队列。"
    )
    @PostMapping("/{orderNo}/cancel")
    public Result<String> cancelOrder(
            @Parameter(description = "订单号", required = true, example = "20260616123456789012")
            @PathVariable String orderNo,
            @Parameter(description = "用户ID", required = true, example = "1001")
            @RequestParam Long userId) {
        if (userId == null) {
            throw new SeckillException(ResultCode.PARAM_ERROR, "用户ID不能为空");
        }
        orderService.cancelOrder(orderNo, userId);
        return Result.success("取消成功");
    }

    @Operation(
        summary = "查询我的票",
        description = "查询用户已支付的所有票，含票号、演出、座位、票价、状态等。"
    )
    @GetMapping("/tickets")
    public Result<List<UserTicket>> myTickets(
            @Parameter(description = "用户ID", required = true, example = "1001")
            @RequestParam Long userId) {
        if (userId == null) {
            throw new SeckillException(ResultCode.PARAM_ERROR, "用户ID不能为空");
        }
        List<UserTicket> tickets = userTicketService.listUserTickets(userId);
        return Result.success(tickets);
    }

    @Operation(
        summary = "查询候补记录",
        description = "查询用户的候补申请记录，退票释放库存时按顺序通知候补用户。"
    )
    @GetMapping("/waitlist")
    public Result<List<Waitlist>> myWaitlist(
            @Parameter(description = "用户ID", required = true, example = "1001")
            @RequestParam Long userId) {
        if (userId == null) {
            throw new SeckillException(ResultCode.PARAM_ERROR, "用户ID不能为空");
        }
        List<Waitlist> waitlists = waitlistService.listMyWaitlists(userId);
        return Result.success(waitlists);
    }

    @Operation(
        summary = "用户自助退票",
        description = "用户对未使用状态的票发起退票，释放库存并通知候补用户。"
    )
    @PostMapping("/ticket/refund")
    public Result<String> refundTicket(
            @Parameter(description = "票号", required = true, example = "TKxxxxxxxxxxxx")
            @RequestParam String ticketNo,
            @Parameter(description = "用户ID", required = true, example = "1001")
            @RequestParam Long userId) {
        if (userId == null || ticketNo == null || ticketNo.trim().isEmpty()) {
            throw new SeckillException(ResultCode.PARAM_ERROR, "参数不正确");
        }
        userTicketService.refundTicket(ticketNo.trim(), userId);
        return Result.success("退票成功，库存已释放");
    }
}
