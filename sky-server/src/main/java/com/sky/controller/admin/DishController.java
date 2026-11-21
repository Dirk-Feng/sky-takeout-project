package com.sky.controller.admin;

import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.vo.DishVO;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.DishService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.List;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/dish")
public class DishController {
    private final DishService dishService;

    public DishController(DishService dishService) { this.dishService = dishService; }

    @PostMapping("/status/{status}")
    public Result<String> startOrStop(@PathVariable("status") Integer status, @RequestParam("id") Long id) {
        dishService.startOrStop(status, id);
        return Result.success();
    }

    @GetMapping("/{id}")
    public Result<DishVO> getById(@PathVariable("id") Long id) {
        return Result.success(dishService.getById(id));
    }

    @PutMapping
    public Result<String> update(@RequestBody DishDTO dto) {
        dishService.update(dto);
        return Result.success();
    }

    @DeleteMapping
    public Result<String> delete(@RequestParam("ids") List<Long> ids) {
        dishService.deleteBatch(ids);
        return Result.success();
    }

    @GetMapping("/page")
    public Result<PageResult<DishVO>> page(DishPageQueryDTO query) {
        return Result.success(dishService.pageQuery(query));
    }

    @PostMapping
    public Result<String> save(@RequestBody DishDTO dto) {
        dishService.save(dto);
        return Result.success();
    }
}
