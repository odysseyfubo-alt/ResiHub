-- 参团脚本：原子完成「名额校验 -> 一人一单 -> 扣名额 -> 记录用户 -> 投递异步下单」
-- KEYS[1] 名额 key   gb:stock:{groupId}
-- KEYS[2] 占位集合   gb:users:{groupId}
-- ARGV[1] userId  ARGV[2] orderNo  ARGV[3] productId  ARGV[4] groupId  ARGV[5] streamKey
-- 返回：0 成功 / 1 名额不足 / 2 重复参团 / 3 名额未初始化
local stock = redis.call('get', KEYS[1])
if stock == false then
    return 3
end
if tonumber(stock) <= 0 then
    return 1
end
if redis.call('sismember', KEYS[2], ARGV[1]) == 1 then
    return 2
end
redis.call('decr', KEYS[1])
redis.call('sadd', KEYS[2], ARGV[1])
redis.call('xadd', ARGV[5], '*', 'orderNo', ARGV[2], 'userId', ARGV[1], 'productId', ARGV[3], 'groupId', ARGV[4])
return 0
