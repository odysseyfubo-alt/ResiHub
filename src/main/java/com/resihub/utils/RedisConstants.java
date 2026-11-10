package com.resihub.utils;

public class RedisConstants {
    /** 登录验证码 */
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 2L;      // 分钟

    /** 登录态 */
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 36000L;  // 分钟

    /** 某个用户的全部 token 索引：login:user_tokens:{userId} Set<token>，用于改资料/审核后刷新会话 */
    public static final String LOGIN_USER_TOKENS_KEY = "login:user_tokens:";

    /** 公告已读标记 Hash：notice:read:{userId} -> field=noticeId */
    public static final String NOTICE_READ_KEY = "notice:read:";

    /** 团购：剩余名额 / 已参团用户集合 */
    public static final String GROUP_STOCK_KEY = "gb:stock:";   // gb:stock:{groupBuyId}
    public static final String GROUP_USERS_KEY = "gb:users:";   // gb:users:{groupBuyId} Set

    /** 团购订单异步流 */
    public static final String ORDER_STREAM = "stream:orders";
    public static final String ORDER_GROUP = "g1";

    /** 活动名额 */
    public static final String ACTIVITY_QUOTA_KEY = "act:quota:";
    public static final String ACTIVITY_USERS_KEY = "act:users:";

    /** 缓存 */
    public static final String CACHE_COMMUNITY_KEY = "cache:community:";
    public static final Long CACHE_COMMUNITY_TTL = 30L;        // 分钟
    public static final Long CACHE_NULL_TTL = 2L;              // 分钟
    public static final String LOCK_COMMUNITY_KEY = "lock:community:";

    /** 工单超时升级队列（ZSet，score=到点时间戳） */
    public static final String WORK_TIMEOUT_KEY = "work:timeout";
    /** 团购流团判定队列 */
    public static final String GROUP_DEADLINE_KEY = "gb:deadline";

    /** 访客/活动一次性凭证 */
    public static final String VISIT_PASS_KEY = "visit:pass:";

    /** 签到 Bitmap：sign:{userId}:{yyyyMM} */
    public static final String SIGN_KEY = "sign:";
}
