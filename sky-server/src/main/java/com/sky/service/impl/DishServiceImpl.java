package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.sky.dto.DishDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.TimeUnit;
import java.util.Set;
import com.sky.dto.DishPageQueryDTO;
import com.sky.vo.DishVO;
import com.sky.result.PageResult;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.sky.entity.Dish;
import com.sky.entity.DishFlavor;
import com.sky.exception.BaseException;
import com.sky.mapper.DishFlavorMapper;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.constant.StatusConstant;
import com.sky.constant.MessageConstant;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.service.DishService;
import org.springframework.stereotype.Service;
import org.springframework.beans.BeanUtils;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Collections;

@Service
public class DishServiceImpl implements DishService {
    private static final Logger log = LoggerFactory.getLogger(DishServiceImpl.class);
    private static final String CACHE_PREFIX = "dish_";
    private static final long CACHE_TTL_MINUTES = 30;
    @Autowired
    private StringRedisTemplate redisTemplate;
    private final DishMapper dishMapper;
    private final DishFlavorMapper dishFlavorMapper;
    private final SetmealDishMapper setmealDishMapper;

    public DishServiceImpl(DishMapper dishMapper, DishFlavorMapper dishFlavorMapper, SetmealDishMapper setmealDishMapper) {
        this.dishMapper = dishMapper;
        this.dishFlavorMapper = dishFlavorMapper;
        this.setmealDishMapper = setmealDishMapper;
    }

    public DishVO getById(Long id) {
        if (id == null || id <= 0) throw new BaseException("菜品ID无效");
        Dish dish = dishMapper.getById(id);
        if (dish == null) throw new BaseException("菜品不存在");
        DishVO result = new DishVO();
        BeanUtils.copyProperties(dish, result);
        result.setFlavors(dishFlavorMapper.getByDishId(id));
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public void startOrStop(Integer status, Long id) {
        if (status == null || (!StatusConstant.ENABLE.equals(status) && !StatusConstant.DISABLE.equals(status))) {
            throw new BaseException("菜品状态无效");
        }
        if (id == null || id <= 0) throw new BaseException("菜品ID无效");
        Dish dish = dishMapper.getById(id);
        if (dish == null) throw new BaseException("菜品不存在");
        // TODO：后续实现套餐管理时，需要重新评估菜品停售与套餐状态之间的业务约束。例如，菜品被正在起售的套餐引用时，是否应该禁止停售，以及套餐起售前是否需要检查其包含的菜品状态。
        dish.setStatus(status);
        if (dishMapper.update(dish) != 1) throw new BaseException("菜品状态未更新，请检查后重试");
        evictAfterCommit(Collections.singleton(dish.getCategoryId()));
    }

    // 事务同时保护菜品更新和风味替换。
    @Transactional(rollbackFor = Exception.class)
    public void update(DishDTO dto) {
        if (dto == null || dto.getId() == null || dto.getId() <= 0) throw new BaseException("菜品ID无效");
        if (dto.getName() == null || dto.getName().trim().isEmpty() || dto.getCategoryId() == null
                || dto.getPrice() == null || dto.getImage() == null || dto.getImage().trim().isEmpty()) {
            throw new BaseException("菜品必填信息不完整");
        }
        Dish oldDish = dishMapper.getById(dto.getId());
        if (oldDish == null) throw new BaseException("菜品不存在");
        Dish dish = Dish.builder().id(dto.getId()).name(dto.getName()).categoryId(dto.getCategoryId())
                .price(dto.getPrice()).image(dto.getImage()).description(dto.getDescription()).status(dto.getStatus()).build();
        if (dishMapper.update(dish) != 1) throw new BaseException("菜品未更新，请检查后重试");
        // null 表示不变；只有传入数组时才整体替换，空数组表示清空。
        if (dto.getFlavors() != null) {
            List<DishFlavor> replacements = new ArrayList<>();
            for (DishFlavor flavor : dto.getFlavors()) {
                if (flavor == null || flavor.getName() == null || flavor.getName().trim().isEmpty()
                        || flavor.getValue() == null || flavor.getValue().trim().isEmpty()) {
                    throw new BaseException("菜品口味信息不完整");
                }
                replacements.add(DishFlavor.builder().dishId(dto.getId()).name(flavor.getName()).value(flavor.getValue()).build());
            }
            dishFlavorMapper.deleteByDishIds(Collections.singletonList(dto.getId()));
            if (!replacements.isEmpty()) dishFlavorMapper.insertBatch(replacements);
        }
        evictAfterCommit(new LinkedHashSet<>(java.util.Arrays.asList(oldDish.getCategoryId(), dto.getCategoryId())));
    }

    // 所有检查和两张表的删除处于同一事务，任何失败都撤销已执行的删除。
    @Transactional(rollbackFor = Exception.class)
    public void deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new BaseException("菜品ID无效");
        }
        List<Long> uniqueIds = new ArrayList<>(new LinkedHashSet<>(ids));
        List<Dish> dishes = dishMapper.getByIds(uniqueIds);
        if (dishes.size() != uniqueIds.size()) throw new BaseException("部分菜品不存在");
        // 起售或被套餐引用时整批拒绝，不删除套餐关系。
        if (dishes.stream().anyMatch(dish -> StatusConstant.ENABLE.equals(dish.getStatus()))) {
            throw new DeletionNotAllowedException(MessageConstant.DISH_ON_SALE);
        }
        if (!setmealDishMapper.getByDishIds(uniqueIds).isEmpty()) {
            throw new DeletionNotAllowedException(MessageConstant.DISH_BE_RELATED_BY_SETMEAL);
        }
        dishFlavorMapper.deleteByDishIds(uniqueIds);
        if (dishMapper.deleteByIds(uniqueIds) != uniqueIds.size()) {
            throw new BaseException("菜品删除不完整，请重试");
        }
        Set<Long> categories = new LinkedHashSet<>();
        for (Dish item : dishes) categories.add(item.getCategoryId());
        evictAfterCommit(categories);
    }

    public PageResult<DishVO> pageQuery(DishPageQueryDTO query) {
        if (query == null || query.getPage() < 1 || query.getPageSize() < 1 || query.getPageSize() > 10) {
            throw new BaseException("页码须大于零，每页数量须为1至10");
        }
        PageHelper.startPage(query.getPage(), query.getPageSize());
        List<DishVO> records = dishMapper.pageQuery(query);
        PageInfo<DishVO> page = new PageInfo<>(records);
        return new PageResult<>(page.getTotal(), page.getList());
    }

    // Service 事务覆盖菜品和全部口味插入，任何异常都回滚整次操作。
    @Transactional(rollbackFor = Exception.class)
    public void save(DishDTO dto) {
        if (dto == null || dto.getName() == null || dto.getName().trim().isEmpty()
                || dto.getCategoryId() == null || dto.getPrice() == null
                || dto.getImage() == null || dto.getImage().trim().isEmpty()) {
            throw new BaseException("菜品必填信息不完整");
        }
        Dish dish = Dish.builder().name(dto.getName()).categoryId(dto.getCategoryId())
                .price(dto.getPrice()).image(dto.getImage()).description(dto.getDescription())
                .status(dto.getStatus()).build();
        dishMapper.insert(dish);

        if (dish.getId() == null) throw new BaseException("未获取到新增菜品ID");

        if (dto.getFlavors() != null && !dto.getFlavors().isEmpty()) {
            List<DishFlavor> flavors = new ArrayList<>();
            for (DishFlavor flavor : dto.getFlavors()) {
                if (flavor == null || flavor.getName() == null || flavor.getName().trim().isEmpty()
                        || flavor.getValue() == null || flavor.getValue().trim().isEmpty()) {
                    throw new BaseException("菜品口味信息不完整");
                }
                flavors.add(DishFlavor.builder().dishId(dish.getId()).name(flavor.getName()).value(flavor.getValue()).build());
            }
            dishFlavorMapper.insertBatch(flavors);
        }
        evictAfterCommit(Collections.singleton(dto.getCategoryId()));
    }
    @Override
    public List<DishVO> listWithFlavor(Dish dish) {
        boolean cacheable = dish != null && dish.getCategoryId() != null
                && StatusConstant.ENABLE.equals(dish.getStatus());
        String key = cacheable ? CACHE_PREFIX + dish.getCategoryId() : null;
        if (cacheable) {
            try {
                String cached = redisTemplate.opsForValue().get(key);
                if (cached != null) return JSON.parseArray(cached, DishVO.class);
            } catch (RuntimeException ex) {
                log.warn("菜品缓存读取失败，将查询数据库");
            }
        }
        List<DishVO> result = new ArrayList<>();
        for (Dish item : dishMapper.list(dish)) {
            DishVO vo = new DishVO();
            BeanUtils.copyProperties(item, vo);
            vo.setFlavors(dishFlavorMapper.getByDishId(item.getId()));
            result.add(vo);
        }
        if (cacheable) {
            try {
                redisTemplate.opsForValue().set(key, JSON.toJSONString(result), CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            } catch (RuntimeException ex) {
                log.warn("菜品缓存写入失败");
            }
        }
        return result;
    }

    private void evictAfterCommit(Set<Long> categoryIds) {
        Set<Long> ids = new LinkedHashSet<>(categoryIds);
        ids.remove(null);
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            evict(ids);
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("菜品缓存清理需要事务同步");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                evict(ids);
            }
        });
    }
    private void evict(Set<Long> ids) {
        for (Long id : ids) {
            try {
                redisTemplate.delete(CACHE_PREFIX + id);
            } catch (RuntimeException ex) {
                log.warn("菜品缓存清理失败，将依靠过期时间恢复");
            }
        }
    }}