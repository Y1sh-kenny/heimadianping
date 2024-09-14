package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.stereotype.Service;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;

@SpringBootTest
@RunWith(SpringRunner.class)

public class HmDianPingApplicationTests {
	@Resource
	private ShopServiceImpl shopService;

	@Test
	public void testShop(){
		try {
			shopService.saveShop2Redis(1L,10L);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}
}
