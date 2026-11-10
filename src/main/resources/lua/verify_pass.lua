-- 访客/活动码核销：一次性凭证
-- KEYS[1] visit:pass:{code}
-- 返回：0 不存在或已过期 / 1 核销成功 / 2 已使用
local v = redis.call('get', KEYS[1])
if v == false then
    return 0
end
if v == '1' then
    return 2
end
redis.call('set', KEYS[1], '1', 'KEEPTTL')
return 1
