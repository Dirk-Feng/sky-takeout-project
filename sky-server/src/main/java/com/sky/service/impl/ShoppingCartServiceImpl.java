package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.ShoppingCart;
import com.sky.exception.BaseException;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.service.ShoppingCartService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ShoppingCartServiceImpl implements ShoppingCartService {
    private final ShoppingCartMapper cartMapper;
    private final DishMapper dishMapper;
    private final SetmealMapper setmealMapper;
    public ShoppingCartServiceImpl(ShoppingCartMapper cartMapper, DishMapper dishMapper, SetmealMapper setmealMapper) {
        this.cartMapper = cartMapper; this.dishMapper = dishMapper; this.setmealMapper = setmealMapper;
    }
    private Long userId() {
        Long id = BaseContext.getCurrentId();
        if (id == null) throw new BaseException("用户未登录");
        return id;
    }
    private ShoppingCart item(ShoppingCartDTO dto) {
        if (dto == null || (dto.getDishId() == null) == (dto.getSetmealId() == null)
                || (dto.getDishId() != null && dto.getDishId() <= 0)
                || (dto.getSetmealId() != null && dto.getSetmealId() <= 0)
                || (dto.getSetmealId() != null && StringUtils.hasText(dto.getDishFlavor()))) {
            throw new BaseException("商品信息无效");
        }
        return ShoppingCart.builder().userId(userId()).dishId(dto.getDishId()).setmealId(dto.getSetmealId())
                .dishFlavor(StringUtils.hasText(dto.getDishFlavor()) ? dto.getDishFlavor() : null).build();
    }
    @Override @Transactional(rollbackFor = Exception.class)
    public void add(ShoppingCartDTO dto) {
        ShoppingCart cart = item(dto);
        if (cartMapper.increment(cart) > 0) return;
        if (cart.getDishId() != null) {
            Dish dish = dishMapper.getById(cart.getDishId());
            if (dish == null) throw new BaseException("商品不存在");
            cart.setName(dish.getName()); cart.setImage(dish.getImage()); cart.setAmount(dish.getPrice());
        } else {
            Setmeal setmeal = setmealMapper.getCartProduct(cart.getSetmealId());
            if (setmeal == null) throw new BaseException("商品不存在");
            cart.setName(setmeal.getName()); cart.setImage(setmeal.getImage()); cart.setAmount(setmeal.getPrice());
        }
        cart.setNumber(1); cart.setCreateTime(LocalDateTime.now()); cartMapper.insert(cart);
    }
    @Override @Transactional(rollbackFor = Exception.class)
    public void sub(ShoppingCartDTO dto) {
        ShoppingCart cart = item(dto);
        if (cartMapper.decrement(cart) == 0) cartMapper.deleteOne(cart);
    }
    @Override public List<ShoppingCart> list() { return cartMapper.list(userId()); }
    @Override public void clean() { cartMapper.clean(userId()); }
}
