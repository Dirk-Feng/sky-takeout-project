package com.sky.controller.user;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.result.Result;
import com.sky.service.OrderService;
import com.sky.vo.OrderSubmitVO;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/user/order")
public class UserOrderController {
    private final OrderService service;
    public UserOrderController(OrderService service) { this.service = service; }
    @PostMapping("/submit") public Result<OrderSubmitVO> submit(@RequestBody OrdersSubmitDTO dto) { return Result.success(service.submitOrder(dto)); }
}
