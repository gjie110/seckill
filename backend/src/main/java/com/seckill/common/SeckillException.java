package com.seckill.common;

import lombok.Getter;

/**
 * 秒杀业务异常
 */
@Getter
public class SeckillException extends RuntimeException {

    private final int code;

    public SeckillException(ResultCode resultCode) {
        super(resultCode.getMsg());
        this.code = resultCode.getCode();
    }

    public SeckillException(int code, String message) {
        super(message);
        this.code = code;
    }

    public SeckillException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }
}
