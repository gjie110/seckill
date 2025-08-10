package com.seckill.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
public class OrderDetailDTO {
    private Long orderId;
    private String orderNo;
    private Long userId;
    private String username;
    private String phone;
    private Long productId;
    private String productName;
    private String venue;
    private Date showTime;
    private Long ticketTypeId;
    private String ticketTypeName;
    private String seatArea;
    private BigDecimal unitPrice;
    private BigDecimal totalAmount;
    private Integer quantity;
    private String channel;
    private Integer status;
    private Date payTime;
    private Date timeoutTime;
    private Date createTime;
    private Date updateTime;
    private String remark;
}
