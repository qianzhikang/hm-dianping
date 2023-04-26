-- 获取锁中的线程标识
local id = redis.call('get', KEYS[1])
-- 比较线程标识是否与锁中一致
if (id == ARGV[1]) then
    return redis.call('del', KEYS[1])
end
return 0