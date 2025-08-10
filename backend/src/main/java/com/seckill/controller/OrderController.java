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
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
        description = "创建新订单，调用秒杀服务执行下单逻辑。" +
                      "返回订单号供后续支付使用。" +
                      "如果未指定用户ID，默认使用测试用户1001。"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "订单创建成功，返回订单号"),
        @ApiResponse(responseCode = "2001", description = "库存不足")
    })
    @PostMapping("/create")
    public Result<String> createOrder(@org.springframework.web.bind.annotation.RequestBody SeckillRequest request) {
        if (request.getUserId() == null) {
            request.setUserId(1001L);
        }
        // 身份证格式校验：15位或18位（最后一位可以是X/x）
        if (request.getIdCard() == null || !request.getIdCard().matches("(^[0-9]{15}$)|(^[0-9]{18}$)|(^[0-9]{17}[0-9Xx]$)")) {
            throw new SeckillException(ResultCode.PARAM_ERROR, "身份证格式不正确（需为15位或18位）");
        }
        String orderNo = seckillService.executeOrder(request);
        return Result.success(orderNo);
    }

    @Operation(
        summary = "查询订单列表",
        description = "根据用户ID查询该用户的所有订单。" +
                      "返回订单列表，包括订单号、演出信息、票种信息、订单状态、创建时间等。" +
                      "订单状态：0-待支付、1-已支付、2-已取消、3-已超时。"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "查询成功，返回订单列表")
    })
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
        description = "根据订单号查询订单详细信息。" +
                      "返回订单完整信息，包括演出信息、票种信息、价格、数量、订单状态、支付时间等。"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "查询成功，返回订单详情"),
        @ApiResponse(responseCode = "3001", description = "订单不存在")
    })
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
        description = "用户支付订单，完成购票。" +
                      "验证订单状态必须是待支付(状态0)，更新订单状态为已支付(状态1)。" +
                      "支付成功后生成用户票(UserTicket)，包含座位号、票号等信息。" +
                      "返回购票成功信息和票的张数。"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "支付成功，返回票的张数"),
        @ApiResponse(responseCode = "3001", description = "订单不存在"),
        @ApiResponse(responseCode = "3002", description = "订单已超时，请重新下单"),
        @ApiResponse(responseCode = "3003", description = "订单已支付"),
        @ApiResponse(responseCode = "3004", description = "订单已取消")
    })
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
        description = "用户取消未支付的订单。" +
                      "验证订单状态必须是待支付(状态0)，更新订单状态为已取消(状态2)。" +
                      "释放Redis预扣的库存，库存回滚后通知候补队列可能有票可用。" +
                      "候补用户会收到消息通知。"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "取消成功"),
        @ApiResponse(responseCode = "3001", description = "订单不存在"),
        @ApiResponse(responseCode = "3003", description = "订单已支付，无法取消"),
        @ApiResponse(responseCode = "3004", description = "订单已取消")
    })
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
        description = "查询用户已支付的所有票。" +
                      "返回票的详细信息，包括票号、演出名称、票种名称、座位信息、票价、票状态等。" +
                      "票状态：0-未使用、1-已使用、2-已退票。"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "查询成功，返回票列表")
    })
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
        description = "查询用户的候补申请记录。" +
                      "当票种库存不足时，用户可以申请候补，等待有票时通知。" +
                      "候补状态：0-等待中、1-已通知、2-已购票、3-已失效。" +
                      "当有用户退票或取消订单释放库存时，候补用户会收到通知。"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "查询成功，返回候补记录列表")
    })
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
            description = "用户对自己未使用的票发起退票。" +
                          "只有 状态=0(未使用) 的票才能退票。" +
                          "退票成功后，库存自动释放，并可能通知候补用户。"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "退票成功"),
            @ApiResponse(responseCode = "3001", description = "票不存在或不是当前用户的"),
            @ApiResponse(responseCode = "3004", description = "该票已使用或已退票，不可再次退票")
    })
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