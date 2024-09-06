package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
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
	@Override
	public Result queryById(Long id) {
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
		return Result.ok(shop);
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
