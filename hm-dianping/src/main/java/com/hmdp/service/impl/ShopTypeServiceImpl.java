package com.hmdp.service.impl;

import ch.qos.logback.core.joran.util.beans.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

	@Resource
	StringRedisTemplate stringRedisTemplate;

	@Override
	public Result queryTypeList() {
		String key="cache:shop_type_list";
		//用list保存
		ListOperations<String, String> ops = stringRedisTemplate.opsForList();
		List<String> stringList = ops.range(key, 0, -1);
		if(!stringList.isEmpty()){
			//遍历,将其转换成类型
			List<ShopType>shopTypeList = new ArrayList<>();
			for(String obj : stringList){
				ShopType shopType = JSONUtil.toBean(obj,ShopType.class);
				shopTypeList.add(shopType);
			}
			return Result.ok(shopTypeList);
		}

		List<ShopType> typeList = query().orderByAsc("sort").list();
		if(typeList == null ||  typeList.isEmpty()){
			return Result.fail("商铺类型为空");
		}


		//添加到Redis
		List<String>strList = new ArrayList<>();
		for(ShopType shopType : typeList){
			String s = JSONUtil.toJsonStr(shopType);
			strList.add(s);
		}
		stringRedisTemplate.opsForList().rightPushAll(key,strList);
		return Result.ok(typeList);
	}
}
