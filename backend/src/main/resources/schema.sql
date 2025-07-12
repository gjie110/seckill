-- ========================================================================
-- 门票秒杀系统 - 数据库初始化脚本
-- ========================================================================

DROP TABLE IF EXISTS user_ticket;
DROP TABLE IF EXISTS waitlist;
DROP TABLE IF EXISTS message;
DROP TABLE IF EXISTS seckill_order;
DROP TABLE IF EXISTS seckill_stock;
DROP TABLE IF EXISTS seckill_config;
DROP TABLE IF EXISTS ticket_type;
DROP TABLE IF EXISTS seckill_product;
DROP TABLE IF EXISTS seckill_user;

-- 用户表（支持普通用户和管理员角色）
CREATE TABLE seckill_user (
    user_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    password VARCHAR(100) NOT NULL COMMENT '密码',
    phone VARCHAR(20) COMMENT '手机号',
    email VARCHAR(100) COMMENT '邮箱',
    role VARCHAR(20) DEFAULT 'user' COMMENT '角色：user-普通用户，admin-管理员',
    status TINYINT DEFAULT 0 COMMENT '账户状态：0-正常，1-禁用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    last_login_time DATETIME COMMENT '最后登录时间',
    INDEX idx_username (username),
    INDEX idx_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 演出表（演唱会信息）
CREATE TABLE seckill_product (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '演出ID',
    name VARCHAR(200) NOT NULL COMMENT '演出名称',
    description TEXT COMMENT '演出描述',
    venue VARCHAR(200) COMMENT '演出场馆',
    show_time DATETIME NOT NULL COMMENT '演出时间',
    poster_url VARCHAR(500) COMMENT '海报图',
    is_banner TINYINT DEFAULT 0 COMMENT '是否在轮播中：0-否 1-是',
    status TINYINT DEFAULT 1 COMMENT '状态：0下架 1上架',
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_show_time (show_time),
    INDEX idx_banner (is_banner)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='演出表';

-- 票种表（按距离演唱台分）
CREATE TABLE ticket_type (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '票种ID',
    product_id BIGINT NOT NULL COMMENT '演出ID',
    type_name VARCHAR(50) NOT NULL COMMENT '票种名称：A区/B区/C区/学生票',
    seat_area VARCHAR(100) COMMENT '座位区域描述',
    price DECIMAL(10,2) NOT NULL COMMENT '原价',
    channel VARCHAR(20) NOT NULL COMMENT '渠道：seckill-秒杀 special-特价 regular-常规',
    total_stock INT NOT NULL COMMENT '总库存',
    available_stock INT NOT NULL COMMENT '可用库存',
    sold_stock INT DEFAULT 0 COMMENT '已售',
    seat_count INT NOT NULL DEFAULT 0 COMMENT '总座位数（用于生成具体座位号）',
    status TINYINT DEFAULT 1 COMMENT '状态：0下架 1上架',
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_product (product_id),
    INDEX idx_channel (channel)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='票种表';

-- 秒杀配置表
CREATE TABLE seckill_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_id BIGINT NOT NULL COMMENT '演出ID',
    ticket_type_id BIGINT NOT NULL COMMENT '票种ID',
    start_time DATETIME NOT NULL COMMENT '秒杀开始时间',
    end_time DATETIME NOT NULL COMMENT '秒杀结束时间',
    max_per_user INT DEFAULT 3 COMMENT '每用户最大购买数量',
    order_timeout_minutes INT DEFAULT 5 COMMENT '订单超时时间(分钟)',
    status TINYINT DEFAULT 0 COMMENT '状态：0未开始 1进行中 2已结束',
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_product (product_id),
    INDEX idx_ticket_type (ticket_type_id),
    INDEX idx_time (start_time, end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀配置表';

-- 订单表
CREATE TABLE seckill_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(32) NOT NULL UNIQUE COMMENT '订单号',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    product_id BIGINT NOT NULL COMMENT '演出ID',
    ticket_type_id BIGINT NOT NULL COMMENT '票种ID',
    quantity INT DEFAULT 1 COMMENT '购买数量',
    unit_price DECIMAL(10,2) NOT NULL COMMENT '单价',
    total_amount DECIMAL(10,2) NOT NULL COMMENT '总金额',
    channel VARCHAR(20) NOT NULL COMMENT '渠道：seckill/special/regular',
    status TINYINT DEFAULT 0 COMMENT '状态：0待支付 1已支付 2已取消 3已超时',
    pay_time DATETIME COMMENT '支付时间',
    timeout_time DATETIME COMMENT '超时时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user (user_id),
    INDEX idx_status (status),
    INDEX idx_create_time (create_time),
    INDEX idx_timeout (timeout_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- 用户票表（支付成功后生成的实体票）
CREATE TABLE user_ticket (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    ticket_no VARCHAR(32) NOT NULL UNIQUE COMMENT '票号',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    order_id BIGINT NOT NULL COMMENT '订单ID',
    product_id BIGINT NOT NULL COMMENT '演出ID',
    ticket_type_id BIGINT NOT NULL COMMENT '票种ID',
    ticket_type_name VARCHAR(50) NOT NULL COMMENT '票种名称',
    seat_info VARCHAR(100) NOT NULL COMMENT '座位信息',
    price DECIMAL(10,2) NOT NULL COMMENT '票价',
    status TINYINT DEFAULT 0 COMMENT '状态：0未使用 1已使用 2已退票',
    used_time DATETIME COMMENT '使用时间',
    refund_time DATETIME COMMENT '退票时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user (user_id),
    INDEX idx_product (product_id),
    INDEX idx_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户票表';

-- 候补表
CREATE TABLE waitlist (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    product_id BIGINT NOT NULL COMMENT '演出ID',
    ticket_type_id BIGINT NOT NULL COMMENT '票种ID',
    quantity INT DEFAULT 1 COMMENT '候补数量',
    status TINYINT DEFAULT 0 COMMENT '状态：0等待中 1已通知 2已购票 3已失效',
    notify_time DATETIME COMMENT '通知时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user (user_id),
    INDEX idx_ticket_type (ticket_type_id),
    INDEX idx_status (status),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='候补表';

-- 消息表
CREATE TABLE message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '接收用户ID',
    type VARCHAR(30) NOT NULL COMMENT '消息类型：waitlist_notify order_notify system',
    title VARCHAR(200) NOT NULL COMMENT '消息标题',
    content TEXT COMMENT '消息内容',
    related_id BIGINT COMMENT '关联ID（演出ID/票种ID/订单ID）',
    status TINYINT DEFAULT 0 COMMENT '状态：0未读 1已读',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_status (user_id, status),
    INDEX idx_user (user_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息表';

-- ========================================================================
-- 测试数据
-- ========================================================================

-- 插入演出（3场不同热度）
INSERT INTO seckill_product (name, description, venue, show_time, poster_url) VALUES
('周杰伦2026嘉年华世界巡回演唱会·北京站', '周杰伦2026年大型世界巡回演唱会，全新舞台设计，经典曲目+新歌首唱', '北京国家体育场（鸟巢）', '2026-08-20 19:30:00', '/poster-1.svg'),
('五月天2026好好好想见到你·上海站', '五月天2026巡回演唱会上海站', '上海梅赛德斯奔驰中心', '2026-09-15 19:00:00', '/poster-2.svg'),
('薛之谦天外来物·深圳站', '薛之谦2026巡回演唱会深圳站', '深圳春茧体育馆', '2026-10-01 19:30:00', '/poster-3.svg');

-- 插入票种（按区域分，价格不同，渠道不同）
INSERT INTO ticket_type (product_id, type_name, seat_area, price, channel, total_stock, available_stock, seat_count) VALUES
-- 周杰伦演唱会（热度高）
(1, 'A区VIP', '内场前区 A1-A5区 第1-10排', 1999.00, 'seckill', 20, 20, 50),
(1, 'B区', '看台B区 B1-B10区 第11-30排', 999.00, 'seckill', 80, 80, 200),
(1, 'C区学生票', '看台C区 C1-C10区 第31-50排（凭学生证）', 499.00, 'special', 50, 50, 150),
(1, 'D区常规票', '看台D区 D1-D15区 第51-80排', 699.00, 'regular', 500, 500, 500),
-- 五月天演唱会
(2, 'A区VIP', '内场A区', 1599.00, 'seckill', 30, 30, 50),
(2, 'B区', '看台B区', 899.00, 'seckill', 100, 100, 200),
(2, 'C区学生票', '看台C区', 399.00, 'special', 80, 80, 150),
(2, 'D区常规票', '看台D区', 599.00, 'regular', 600, 600, 500),
-- 薛之谦演唱会（热度低）
(3, 'A区VIP', '内场A区', 1299.00, 'special', 50, 50, 50),
(3, 'B区', '看台B区', 799.00, 'regular', 150, 150, 200),
(3, 'C区', '看台C区', 399.00, 'regular', 200, 200, 300);

-- 秒杀配置（秒杀票才有）
INSERT INTO seckill_config (product_id, ticket_type_id, start_time, end_time, max_per_user, order_timeout_minutes, status) VALUES
-- 周杰伦（秒杀）
(1, 1, '2026-06-10 10:00:00', '2026-07-20 22:00:00', 3, 5, 1),
(1, 2, '2026-06-10 10:00:00', '2026-07-20 22:00:00', 3, 5, 1),
-- 周杰伦（特价票）
(1, 3, '2026-06-12 10:00:00', '2026-08-20 22:00:00', 3, 5, 1),
-- 五月天（秒杀）
(2, 5, '2026-06-15 10:00:00', '2026-09-10 22:00:00', 3, 5, 0),
(2, 6, '2026-06-15 10:00:00', '2026-09-10 22:00:00', 3, 5, 0),
-- 五月天（特价）
(2, 7, '2026-06-18 10:00:00', '2026-09-10 22:00:00', 3, 5, 0),
-- 薛之谦（特价）
(3, 9, '2026-06-20 10:00:00', '2026-09-30 22:00:00', 3, 5, 0);

-- ========================================================================
-- 初始用户数据（管理员 + 普通用户）
-- ========================================================================

-- 插入初始用户
INSERT INTO seckill_user (username, password, phone, email, role, status) VALUES
-- 管理员账号
('admin', 'admin123', '13800138000', 'admin@seckill.com', 'admin', 0),
-- 普通用户账号
('user001', 'user123', '13900139001', 'user001@seckill.com', 'user', 0),
('user002', 'user123', '13900139002', 'user002@seckill.com', 'user', 0),
('zhangsan', '123456', '13900139003', 'zhangsan@seckill.com', 'user', 0),
('lisi', '123456', '13900139004', 'lisi@seckill.com', 'user', 0);
