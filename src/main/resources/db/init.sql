-- ============================================================
-- ResiHub 小区居民智能生活服务平台 —— 全量初始化脚本
-- 用法：mysql -uroot -p < init.sql
-- ============================================================
CREATE DATABASE IF NOT EXISTS `resihub` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `resihub`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ------------------------------------------------------------
-- 小区（多租户的租户）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `tb_community`;
CREATE TABLE `tb_community` (
  `id`           bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `name`         varchar(64)  NOT NULL COMMENT '小区名称',
  `address`      varchar(255) NOT NULL DEFAULT '' COMMENT '地址',
  `lng`          double       NOT NULL DEFAULT 0 COMMENT '经度',
  `lat`          double       NOT NULL DEFAULT 0 COMMENT '纬度',
  `households`   int          NOT NULL DEFAULT 0 COMMENT '户数',
  `status`       tinyint UNSIGNED NOT NULL DEFAULT 1 COMMENT '1正常 0停用',
  `create_time`  timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`  timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='小区';

-- ------------------------------------------------------------
-- 用户：归属小区 + 楼栋房号 + 角色 + 积分
-- role: 0居民 1维修工 2管家 3管理员 | audit: 0待审核 1已通过 2已驳回
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `tb_user`;
CREATE TABLE `tb_user` (
  `id`           bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `phone`        varchar(11)  NOT NULL COMMENT '手机号',
  `nick_name`    varchar(32)  NOT NULL DEFAULT '' COMMENT '昵称',
  `icon`         varchar(255) NOT NULL DEFAULT '' COMMENT '头像',
  `community_id` bigint UNSIGNED NULL COMMENT '所属小区',
  `building`     varchar(16)  NULL COMMENT '楼栋',
  `room`         varchar(16)  NULL COMMENT '房号',
  `role`         tinyint UNSIGNED NOT NULL DEFAULT 0 COMMENT '0居民 1维修工 2管家 3管理员',
  `audit`        tinyint UNSIGNED NOT NULL DEFAULT 0 COMMENT '0待审核 1已通过 2已驳回',
  `points`       int          NOT NULL DEFAULT 0 COMMENT '积分',
  `status`       tinyint UNSIGNED NOT NULL DEFAULT 1 COMMENT '1正常 0封禁',
  `create_time`  timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`  timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_phone` (`phone`),
  KEY `idx_community_role` (`community_id`, `role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户';

-- ------------------------------------------------------------
-- 公告（已读状态存 Redis）
-- type: 0公告 1停水停电 2活动 3紧急
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `tb_notice`;
CREATE TABLE `tb_notice` (
  `id`              bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `community_id`    bigint UNSIGNED NOT NULL,
  `title`           varchar(128) NOT NULL,
  `content`         text NOT NULL,
  `type`            tinyint UNSIGNED NOT NULL DEFAULT 0,
  `top`             tinyint UNSIGNED NOT NULL DEFAULT 0 COMMENT '1置顶',
  `building_target` varchar(16) NULL COMMENT '定向楼栋，空=全小区',
  `publisher_id`    bigint UNSIGNED NOT NULL,
  `create_time`     timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`     timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_community_time` (`community_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公告';

-- ------------------------------------------------------------
-- 团购商品（金额单位：分）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `tb_product`;
CREATE TABLE `tb_product` (
  `id`           bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `community_id` bigint UNSIGNED NOT NULL,
  `name`         varchar(128) NOT NULL,
  `sub_title`    varchar(255) NOT NULL DEFAULT '',
  `images`       varchar(1024) NOT NULL DEFAULT '' COMMENT '逗号分隔',
  `price`        int NOT NULL COMMENT '团购价（分）',
  `origin_price` int NOT NULL DEFAULT 0 COMMENT '原价（分）',
  `unit`         varchar(16) NOT NULL DEFAULT '份',
  `stock`        int NOT NULL DEFAULT 0 COMMENT '剩余库存',
  `total_stock`  int NOT NULL DEFAULT 0,
  `status`       tinyint UNSIGNED NOT NULL DEFAULT 1 COMMENT '1上架 0下架',
  `create_time`  timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`  timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_community_status` (`community_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='团购商品';

-- ------------------------------------------------------------
-- 团购活动（一个商品一轮，团长开团）
-- status: 0进行中 1已成团 2已流团 3退款完成
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `tb_group_buy`;
CREATE TABLE `tb_group_buy` (
  `id`             bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `product_id`     bigint UNSIGNED NOT NULL,
  `community_id`   bigint UNSIGNED NOT NULL,
  `leader_user_id` bigint UNSIGNED NOT NULL COMMENT '团长',
  `min_users`      int NOT NULL DEFAULT 1 COMMENT '成团人数',
  `current_users`  int NOT NULL DEFAULT 0,
  `pickup_point`   varchar(128) NOT NULL DEFAULT '',
  `deadline`       datetime NOT NULL COMMENT '截团时间',
  `status`         tinyint UNSIGNED NOT NULL DEFAULT 0,
  `create_time`    timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`    timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_product` (`product_id`),
  KEY `idx_community_status` (`community_id`, `status`),
  KEY `idx_deadline` (`status`, `deadline`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='团购活动';

-- ------------------------------------------------------------
-- 团购订单
-- status: 0待成团 1已成团待自提 2已完成 3已退款 4已取消
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `tb_order`;
CREATE TABLE `tb_order` (
  `id`           bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_no`     varchar(32) NOT NULL,
  `user_id`      bigint UNSIGNED NOT NULL,
  `product_id`   bigint UNSIGNED NOT NULL,
  `group_buy_id` bigint UNSIGNED NOT NULL,
  `quantity`     int NOT NULL DEFAULT 1,
  `amount`       int NOT NULL COMMENT '总价（分）',
  `status`       tinyint UNSIGNED NOT NULL DEFAULT 0,
  `pickup_code`  varchar(12) NOT NULL DEFAULT '' COMMENT '自提核销码',
  `verify_time`  datetime NULL,
  `refund_time`  datetime NULL,
  `create_time`  timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`  timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  UNIQUE KEY `uk_user_group` (`user_id`, `group_buy_id`),
  KEY `idx_group_status` (`group_buy_id`, `status`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='团购订单';

-- ------------------------------------------------------------
-- 报修工单
-- status: 0待接单 1处理中 2待居民确认 3已完成 4已超时升级 5已取消
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `tb_work_order`;
CREATE TABLE `tb_work_order` (
  `id`           bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `community_id` bigint UNSIGNED NOT NULL,
  `user_id`      bigint UNSIGNED NOT NULL COMMENT '报修人',
  `type`         varchar(32) NOT NULL,
  `description`  varchar(512) NOT NULL DEFAULT '',
  `images`       varchar(2048) NOT NULL DEFAULT '',
  `status`       tinyint UNSIGNED NOT NULL DEFAULT 0,
  `assignee_id`  bigint UNSIGNED NULL COMMENT '维修工',
  `accept_time`  datetime NULL,
  `finish_time`  datetime NULL,
  `timeout_at`   datetime NOT NULL COMMENT '超时升级时间点',
  `rating`       tinyint UNSIGNED NULL,
  `comment`      varchar(255) NULL,
  `create_time`  timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`  timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_community_status` (`community_id`, `status`),
  KEY `idx_assignee` (`assignee_id`, `status`),
  KEY `idx_timeout` (`status`, `timeout_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报修工单';

-- ------------------------------------------------------------
-- 访客通行码
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `tb_visit_pass`;
CREATE TABLE `tb_visit_pass` (
  `id`            bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `community_id`  bigint UNSIGNED NOT NULL,
  `user_id`       bigint UNSIGNED NOT NULL COMMENT '业主',
  `visitor_name`  varchar(32) NOT NULL,
  `visitor_phone` varchar(11) NOT NULL,
  `code`          varchar(12) NOT NULL COMMENT '通行码',
  `expire_time`   datetime NOT NULL,
  `used`          tinyint UNSIGNED NOT NULL DEFAULT 0,
  `use_time`      datetime NULL,
  `create_time`   timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='访客通行码';

-- ------------------------------------------------------------
-- 邻里圈帖子
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `tb_post`;
CREATE TABLE `tb_post` (
  `id`           bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `community_id` bigint UNSIGNED NOT NULL,
  `user_id`      bigint UNSIGNED NOT NULL,
  `category`     varchar(16) NOT NULL DEFAULT '分享' COMMENT '闲置/求助/分享/拼车',
  `title`        varchar(128) NOT NULL,
  `content`      varchar(2048) NOT NULL DEFAULT '',
  `images`       varchar(2048) NOT NULL DEFAULT '',
  `liked`        int NOT NULL DEFAULT 0,
  `comments`     int NOT NULL DEFAULT 0,
  `status`       tinyint UNSIGNED NOT NULL DEFAULT 1 COMMENT '1正常 0已下架',
  `create_time`  timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`  timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_community_time` (`community_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='邻里圈帖子';

DROP TABLE IF EXISTS `tb_post_comment`;
CREATE TABLE `tb_post_comment` (
  `id`          bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `post_id`     bigint UNSIGNED NOT NULL,
  `user_id`     bigint UNSIGNED NOT NULL,
  `content`     varchar(512) NOT NULL,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_post` (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子评论';

-- ------------------------------------------------------------
-- 社区活动 + 报名
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `tb_activity`;
CREATE TABLE `tb_activity` (
  `id`           bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `community_id` bigint UNSIGNED NOT NULL,
  `title`        varchar(128) NOT NULL,
  `content`      varchar(2048) NOT NULL DEFAULT '',
  `images`       varchar(1024) NOT NULL DEFAULT '',
  `quota`        int NOT NULL DEFAULT 0 COMMENT '名额',
  `signed`       int NOT NULL DEFAULT 0 COMMENT '已报名',
  `start_time`   datetime NOT NULL,
  `end_time`     datetime NOT NULL,
  `status`       tinyint UNSIGNED NOT NULL DEFAULT 1 COMMENT '1报名中 0已结束',
  `create_time`  timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`  timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_community` (`community_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='社区活动';

DROP TABLE IF EXISTS `tb_activity_signup`;
CREATE TABLE `tb_activity_signup` (
  `id`          bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `activity_id` bigint UNSIGNED NOT NULL,
  `user_id`     bigint UNSIGNED NOT NULL,
  `code`        varchar(12) NOT NULL COMMENT '现场核销码',
  `used`        tinyint UNSIGNED NOT NULL DEFAULT 0,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_activity_user` (`activity_id`, `user_id`),
  KEY `idx_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动报名';

-- ------------------------------------------------------------
-- 积分流水 / 站内消息
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `tb_points_log`;
CREATE TABLE `tb_points_log` (
  `id`          bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`     bigint UNSIGNED NOT NULL,
  `points`      int NOT NULL COMMENT '正数增加，负数扣减',
  `type`        varchar(16) NOT NULL COMMENT 'sign/post/repair/activity',
  `remark`      varchar(128) NOT NULL DEFAULT '',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分流水';

DROP TABLE IF EXISTS `tb_notification`;
CREATE TABLE `tb_notification` (
  `id`           bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`      bigint UNSIGNED NOT NULL,
  `community_id` bigint UNSIGNED NOT NULL DEFAULT 0,
  `title`        varchar(128) NOT NULL,
  `content`      varchar(512) NOT NULL DEFAULT '',
  `type`         varchar(16) NOT NULL COMMENT 'order/work/notice/activity',
  `biz_id`       bigint UNSIGNED NULL,
  `read_flag`    tinyint UNSIGNED NOT NULL DEFAULT 0,
  `create_time`  timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_read` (`user_id`, `read_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内消息';

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- 种子数据
-- ============================================================
INSERT INTO `tb_community` (`id`, `name`, `address`, `lng`, `lat`, `households`) VALUES
  (1, '阳光花园', '杭州市西湖区文三路 100 号', 120.128170, 30.279290, 1200),
  (2, '翠苑新村', '杭州市西湖区翠苑路 22 号',  120.125800, 30.283000, 800),
  (3, '西溪雅苑', '杭州市西湖区西溪路 8 号',   120.090000, 30.270000, 600);

-- 账号：1390000000x 角色分别为 管理员/管家/维修工/居民
INSERT INTO `tb_user` (`id`, `phone`, `nick_name`, `icon`, `community_id`, `building`, `room`, `role`, `audit`, `points`) VALUES
  (1, '13900000001', '系统管理员', '', 1, '物业楼', '101', 3, 1, 0),
  (2, '13900000002', '管家王姐',   '', 1, '物业楼', '102', 2, 1, 0),
  (3, '13900000003', '维修工老李', '', 1, '物业楼', '103', 1, 1, 0),
  (4, '13900000004', '业主张三',   '', 1, '3幢',    '1801', 0, 1, 120),
  (5, '13900000005', '业主李四',   '', 1, '5幢',    '0902', 0, 1, 60),
  (6, '13900000006', '业主赵六',   '', 2, '2幢',    '0703', 0, 1, 30);

INSERT INTO `tb_notice` (`community_id`, `title`, `content`, `type`, `top`, `building_target`, `publisher_id`) VALUES
  (1, '3 幢 11 月 12 日计划停水通知', '因管道检修，3 幢 11 月 12 日 09:00-17:00 停水，请提前储水。', 1, 1, '3幢', 2),
  (1, '阳光花园业主大会通知', '本周六上午 10:00 在物业中心召开业主大会，讨论加装电梯事项。', 0, 1, NULL, 2),
  (1, '小区周末义诊活动', '社区医院本周日 9:00-12:00 在中心广场提供免费血压血糖检测。', 2, 0, NULL, 2),
  (1, '车库入口临时封闭提醒', '地库 B1 入口今日起维修 3 天，请车辆从东门进入。', 3, 0, NULL, 1),
  (2, '翠苑新村垃圾分类调整', '自下月起垃圾桶投放时间调整为 6:30-9:00、17:00-20:00。', 0, 0, NULL, 2);

INSERT INTO `tb_product` (`id`, `community_id`, `name`, `sub_title`, `images`, `price`, `origin_price`, `unit`, `stock`, `total_stock`, `status`) VALUES
  (1, 1, '本地散养土鸡蛋', '30 枚装，当日产后直送', '/imgs/products/egg.jpg', 2580, 3200, '盒', 100, 100, 1),
  (2, 1, '赣南脐橙',       '10 斤装，酸甜多汁',      '/imgs/products/orange.jpg', 2990, 3980, '箱', 80, 80, 1),
  (3, 1, '有机绿叶蔬菜套餐','含菠菜/油麦菜/生菜各一把','/imgs/products/veg.jpg', 1580, 2000, '份', 60, 60, 1),
  (4, 1, '现磨豆浆粉',     '500g 无添加，早餐冲饮',   '/imgs/products/soy.jpg', 1980, 2600, '袋', 120, 120, 1),
  (5, 1, '手工鲜肉水饺',   '30 只装，现包现冻（可开团）', '/imgs/products/dumpling.jpg', 1880, 2400, '袋', 50, 50, 1);

INSERT INTO `tb_group_buy` (`product_id`, `community_id`, `leader_user_id`, `min_users`, `current_users`, `pickup_point`, `deadline`, `status`) VALUES
  (1, 1, 4, 5, 0, '3 幢架空层自提点', DATE_ADD(NOW(), INTERVAL 2 DAY), 0),
  (2, 1, 4, 10, 0, '5 幢快递柜旁',      DATE_ADD(NOW(), INTERVAL 3 DAY), 0),
  (3, 1, 5, 3, 0, '东门门卫室',          DATE_ADD(NOW(), INTERVAL 1 DAY), 0),
  (4, 1, 5, 8, 0, '3 幢架空层自提点',   DATE_ADD(NOW(), INTERVAL 5 DAY), 0);

INSERT INTO `tb_post` (`community_id`, `user_id`, `category`, `title`, `content`, `images`, `liked`, `comments`) VALUES
  (1, 4, '闲置', '九成新儿童滑板车转让', '孩子长大了骑不了，90 元出，3 幢自取。', '/imgs/posts/scooter.jpg', 5, 2),
  (1, 5, '求助', '求借一把十字螺丝刀', '装个书架缺工具，用完马上还，5 幢。', '', 1, 3),
  (1, 4, '分享', '小区东门的煎饼摊真的好吃', '早上 7 点出摊，加两个蛋 8 块。', '/imgs/posts/pancake.jpg', 12, 4),
  (1, 5, '拼车', '周六上午去萧山机场拼车', '早 8 点出发，还差 2 人，均摊。', '', 2, 1);

INSERT INTO `tb_activity` (`community_id`, `title`, `content`, `images`, `quota`, `signed`, `start_time`, `end_time`, `status`) VALUES
  (1, '周末社区义诊',     '社区医院提供免费血压血糖检测与健康咨询。', '', 50, 0, DATE_ADD(NOW(), INTERVAL 3 DAY), DATE_ADD(NOW(), INTERVAL 3 DAY) + INTERVAL 3 HOUR, 1),
  (1, '亲子旧物交换市集', '带上孩子闲置玩具图书来交换，现场提供摊位。', '', 30, 0, DATE_ADD(NOW(), INTERVAL 7 DAY), DATE_ADD(NOW(), INTERVAL 7 DAY) + INTERVAL 4 HOUR, 1);

INSERT INTO `tb_work_order` (`community_id`, `user_id`, `type`, `description`, `images`, `status`, `timeout_at`) VALUES
  (1, 4, '水电', '厨房水龙头漏水，滴个不停', '', 0, DATE_ADD(NOW(), INTERVAL 30 MINUTE)),
  (1, 5, '门窗', '阳台推拉门滑轨卡住拉不动', '', 0, DATE_ADD(NOW(), INTERVAL 30 MINUTE));
