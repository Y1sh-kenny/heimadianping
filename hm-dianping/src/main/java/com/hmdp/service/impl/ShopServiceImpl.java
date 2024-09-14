package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.RedisData;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

	@Resource
	StringRedisTemplate stringRedisTemplate;

	private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
	private boolean tryLock(String key)
	{
		Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key,"1",10,TimeUnit.SECONDS);
		return BooleanUtil.isTrue(flag);
	}

	private void unlock(String key){
		stringRedisTemplate.delete(key);
	}
	@Override
	public Result queryById(Long id) {
		/*
		String key = RedisConstants.CACHE_SHOP_KEY + id;
		//从Redis里面查
		String shopJson = stringRedisTemplate.opsForValue().get(key);
		if(StrUtil.isNotBlank(shopJson)){
			return Result.ok(JSONUtil.toBean(shopJson,Shop.class));
		}
		//shopJson == "" ,即查询到空值时.
		if(shopJson != null){
			return Result.fail("店铺不存在");
		}
		//从数据库里查
		Shop shop = getById(id);
		if(shop == null){
			stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
			return Result.fail("店铺不存在");
		}

		//写入缓存中
		stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
		//返回
		 */
		//缓存穿透
		//Shop shop = queryWithPassThrough(id);
		//互斥锁解决缓存击穿
		Shop shop = queryWithLogicExpire(id);
		if(shop == null){
			return Result.fail("没有查询到shop");
		}
		return Result.ok(shop);
	}

	//解决缓存穿透问题...
	public Shop queryWithPassThrough(Long id)
	{
		String key = RedisConstants.CACHE_SHOP_KEY + id;
		//从Redis里面查
		String shopJson = stringRedisTemplate.opsForValue().get(key);
		if(StrUtil.isNotBlank(shopJson)){
			return JSONUtil.toBean(shopJson,Shop.class);
		}
		//shopJson == "" ,即查询到空值时.
		if(shopJson != null){
			return null;
		}
		//从数据库里查
		Shop shop = getById(id);
		if(shop == null){
			stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
			return null;
		}

		//写入缓存中
		stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
		//返回
		return shop;
	}
	//解决缓存击穿问题 -- 服务重建过程复杂,容易被击穿.
	public Shop queryWithMutex(Long id){
		String key = RedisConstants.CACHE_SHOP_KEY + id;
		//从Redis里面查
		String shopJson = stringRedisTemplate.opsForValue().get(key);
		if(StrUtil.isNotBlank(shopJson)){
			return JSONUtil.toBean(shopJson,Shop.class);
		}
		//shopJson == "" ,即查询到空值时.
		if(shopJson != null){
			return null;
		}

		//没有命中
		//拿锁
		String lockKey = "lock:shop:"+id;
		Shop shop = null;
		try {
			if (!tryLock(lockKey))
			{
				Thread.sleep(50);
				return queryWithMutex(id);

			}
			//拿到锁之后第一件事应该是再次检查redis中国年是否已经被更新了数据...
			shopJson = stringRedisTemplate.opsForValue().get(key);
			if(StrUtil.isNotBlank(shopJson)){
				return JSONUtil.toBean(shopJson,Shop.class);
			}
			//从数据库里查
			shop = getById(id);
			if(shop == null){
				stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
				return null;
			}
			//写入缓存中
			stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

			//返回

		} catch (Exception e) {
			e.printStackTrace();
		}finally {
			unlock(lockKey);
		}
		return shop;
	}

	//逻辑过期查询
	public Shop queryWithLogicExpire(Long id)
	{
		String key = RedisConstants.CACHE_SHOP_KEY + id;
		//从Redis里面查
		String shopJson = stringRedisTemplate.opsForValue().get(key);
		if (StrUtil.isBlank(shopJson)) {
			return null;
		}

		//查看是否过期
		RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
		Shop shop = JSONUtil.toBean((JSONObject)redisData.getData(),Shop.class);
		if(redisData.getExpireTime().isAfter(LocalDateTime.now()))
		{
			//没过期
			return shop;
		}
		//过期了
		//1.获取锁,启动线程更新
		String lockKey = RedisConstants.LOCK_SHOP_KEY+id;
		boolean isLock = tryLock(lockKey);
		//1.1拿到锁,首先再次从Redis冲查询数据,检查是否过期,反之上一个拿到锁的人已经更新了.
		if(isLock){
			shopJson = stringRedisTemplate.opsForValue().get(key);
			if (StrUtil.isBlank(shopJson)) {
				return null;
			}
			//查看是否过期
			redisData = JSONUtil.toBean(shopJson, RedisData.class);
			shop = JSONUtil.toBean((JSONObject)redisData.getData(),Shop.class);
			if(redisData.getExpireTime().isAfter(LocalDateTime.now()))
			{
				//没过期
				return shop;
			}
			//ok,完全过期了
			CACHE_REBUILD_EXECUTOR.submit(()->{
				//重建
				try{
					saveShop2Redis(id,20L);
				}catch (Exception e){
					throw new RuntimeException(e);
				}finally {
					unlock(lockKey);
				}

			});
		}

		//返回旧数据.
		return shop;
	}

	public void saveShop2Redis(Long id,Long expireSeconds) throws InterruptedException {
		//睡200ms再更新,
		Thread.sleep(200);
		Shop shop = getById(id);
		RedisData redisData = new RedisData();
		redisData.setData(shop);
		redisData.setExpireTime(LocalDateTime.now().plus(10, ChronoUnit.SECONDS));
		stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
	}

	@Override
	@Transactional
	public Result updateShop(Shop shop) {
		Long id = shop.getId();
		if(id == null){
			return Result.fail("店铺id不能为空");
		}
		//先更新数据库,再删除缓存
		updateById(shop);
		stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);

		return Result.ok();
	}
}
