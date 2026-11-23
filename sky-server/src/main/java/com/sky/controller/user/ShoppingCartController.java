package com.sky.controller.user;
import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.ShoppingCart;
import com.sky.result.Result;
import com.sky.service.ShoppingCartService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController
@RequestMapping("/user/shoppingCart")
public class ShoppingCartController {
    private final ShoppingCartService service;
    public ShoppingCartController(ShoppingCartService service) { this.service = service; }
    @PostMapping("/add") public Result<Void> add(@RequestBody ShoppingCartDTO dto) { service.add(dto); return Result.success(); }
    @PostMapping("/sub") public Result<Void> sub(@RequestBody ShoppingCartDTO dto) { service.sub(dto); return Result.success(); }
    @GetMapping("/list") public Result<List<ShoppingCart>> list() { return Result.success(service.list()); }
    @DeleteMapping("/clean") public Result<Void> clean() { service.clean(); return Result.success(); }
}
