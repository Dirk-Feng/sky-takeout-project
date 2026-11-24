package com.sky.service.impl;

import com.sky.constant.StatusConstant;
import com.sky.context.BaseContext;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.*;
import com.sky.exception.BaseException;
import com.sky.mapper.*;
import com.sky.service.OrderService;
import com.sky.vo.OrderSubmitVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService {
    private final AddressBookMapper addresses;
    private final ShoppingCartMapper carts;
    private final DishMapper dishes;
    private final SetmealMapper setmeals;
    private final OrderMapper orders;
    private final OrderDetailMapper details;
    private final BigDecimal deliveryFee;
    private final BigDecimal packagingFee;
    public OrderServiceImpl(AddressBookMapper addresses, ShoppingCartMapper carts, DishMapper dishes, SetmealMapper setmeals,
                            OrderMapper orders, OrderDetailMapper details,
                            @Value("${order-fees.delivery}") BigDecimal deliveryFee,
                            @Value("${order-fees.packaging-per-item}") BigDecimal packagingFee) {
        this.addresses=addresses; this.carts=carts; this.dishes=dishes; this.setmeals=setmeals;
        this.orders=orders; this.details=details; this.deliveryFee=deliveryFee; this.packagingFee=packagingFee;
    }
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderSubmitVO submitOrder(OrdersSubmitDTO dto) {
        // 1. 获取登录用户，并校验提交订单所需的基本参数。
        Long userId = BaseContext.getCurrentId();
        if (userId == null || dto == null || dto.getAddressBookId() == null || dto.getAmount() == null || dto.getPackAmount() == null
                || (dto.getPayMethod() != 1 && dto.getPayMethod() != 2) || dto.getDeliveryStatus() == null
                || (dto.getDeliveryStatus() != 0 && dto.getDeliveryStatus() != 1) || dto.getTablewareStatus() == null
                || (dto.getTablewareStatus() != 0 && dto.getTablewareStatus() != 1) || dto.getTablewareNumber() == null || dto.getTablewareNumber() < 0)
            throw new BaseException("订单信息不完整");
        // 2. 按地址 ID 和当前用户 ID 查询，防止使用其他用户的地址。
        AddressBook address = addresses.getById(dto.getAddressBookId(), userId);
        if (address == null) throw new BaseException("收货地址不存在");
        // 3. 查询并锁定当前用户的购物车，空购物车不能下单。
        List<ShoppingCart> cart = carts.listForOrder(userId);
        if (cart == null || cart.isEmpty()) throw new BaseException("购物车为空");
        // 4. 校验购物车条目，并收集菜品和套餐 ID，供后续批量查询。
        Set<Long> dishIds = new LinkedHashSet<>(), setmealIds = new LinkedHashSet<>();
        for (ShoppingCart c : cart) {
            if (c.getId() == null || c.getNumber() == null || c.getNumber() <= 0 || (c.getDishId() == null) == (c.getSetmealId() == null)) throw new BaseException("购物车商品无效");
            if (c.getDishId() != null) dishIds.add(c.getDishId()); else setmealIds.add(c.getSetmealId());
        }
        // 5. 批量读取商品信息，避免在循环中逐个查询数据库。
        Map<Long,Dish> dishMap = dishIds.isEmpty() ? Collections.emptyMap() : dishes.findOrderProducts(new ArrayList<>(dishIds)).stream().collect(Collectors.toMap(Dish::getId, d -> d));
        Map<Long,Setmeal> setmealMap = setmealIds.isEmpty() ? Collections.emptyMap() : setmeals.findOrderProducts(new ArrayList<>(setmealIds)).stream().collect(Collectors.toMap(Setmeal::getId, s -> s));
        // 6. 检查商品是否在售、价格是否变化，同时准备订单明细快照。
        BigDecimal subtotal = BigDecimal.ZERO;
        int quantity = 0;
        List<OrderDetail> items = new ArrayList<>();
        List<Long> ids = new ArrayList<>();
        for (ShoppingCart c : cart) {
            BigDecimal price; String name; String image; Integer status;
            if (c.getDishId() != null) {
                // 是单个商品
                Dish d = dishMap.get(c.getDishId()); if (d == null) throw new BaseException("商品已下架，请刷新购物车");
                price=d.getPrice(); name=d.getName(); image=d.getImage(); status=d.getStatus();
            } else {
                // 是套餐类
                Setmeal s = setmealMap.get(c.getSetmealId()); if (s == null) throw new BaseException("商品已下架，请刷新购物车");
                price=s.getPrice(); name=s.getName(); image=s.getImage(); status=s.getStatus();
            }
            if (!StatusConstant.ENABLE.equals(status) || price == null || c.getAmount() == null || price.compareTo(c.getAmount()) != 0)
                throw new BaseException("商品状态或价格已变化，请刷新购物车");
            try { quantity = Math.addExact(quantity, c.getNumber()); } catch (ArithmeticException e) { throw new BaseException("商品数量无效"); }
            subtotal = subtotal.add(price.multiply(BigDecimal.valueOf(c.getNumber())));
            // orderdetail对象 后续批量插入订单明细
            items.add(OrderDetail.builder().name(name).image(image).dishId(c.getDishId()).setmealId(c.getSetmealId()).dishFlavor(c.getDishFlavor()).number(c.getNumber()).amount(price).build());
            ids.add(c.getId()); //购物车记录id 待会儿要删掉购物车信息
        }
        // 7. 服务端计算打包费和总金额，并与前端确认的金额比较。
        BigDecimal packaging = packagingFee.multiply(BigDecimal.valueOf(quantity)); //打包费
        int packAmount;
        try { packAmount = packaging.intValueExact(); } catch (ArithmeticException e) { throw new BaseException("打包费配置无效"); }
        BigDecimal total = subtotal.add(deliveryFee).add(packaging); //后端算出来的商品费+打包费
        if (packAmount != dto.getPackAmount() || total.compareTo(dto.getAmount()) != 0) throw new BaseException("订单金额已变化，请刷新订单信息");
        // 8. 保存收货地址快照，构造待付款、未支付的订单。
        String fullAddress = String.join("", Arrays.asList(Optional.ofNullable(address.getProvinceName()).orElse(""), Optional.ofNullable(address.getCityName()).orElse(""), Optional.ofNullable(address.getDistrictName()).orElse(""), Optional.ofNullable(address.getDetail()).orElse("")));
        Orders order = Orders.builder().number(UUID.randomUUID().toString().replace("-", "")).status(Orders.PENDING_PAYMENT).userId(userId)
                .addressBookId(address.getId()).orderTime(LocalDateTime.now()).payMethod(dto.getPayMethod()).payStatus(Orders.UN_PAID)
                .amount(total).remark(dto.getRemark()).phone(address.getPhone()).address(fullAddress).consignee(address.getConsignee())
                .estimatedDeliveryTime(dto.getEstimatedDeliveryTime()).deliveryStatus(dto.getDeliveryStatus()).packAmount(packAmount)
                .tablewareNumber(dto.getTablewareNumber()).tablewareStatus(dto.getTablewareStatus()).build();
        // 9. 插入订单并获取主键，再批量插入关联的订单明细。
        if (orders.insert(order) != 1 || order.getId() == null) throw new BaseException("订单创建失败");
        for (OrderDetail detail : items) detail.setOrderId(order.getId());
        if (details.insertBatch(items) != items.size()) throw new BaseException("订单明细创建失败");
        // 10. 只清理本次下单读取的购物车记录，失败时由事务回滚。
        if (carts.deleteOrderItems(userId, ids) != ids.size()) throw new BaseException("购物车已变化，请重试");
        // 11. 返回订单 ID、订单号、服务端计算的金额和下单时间。
        return OrderSubmitVO.builder().id(order.getId()).orderNumber(order.getNumber()).orderAmount(total).orderTime(order.getOrderTime()).build();
    }
}
