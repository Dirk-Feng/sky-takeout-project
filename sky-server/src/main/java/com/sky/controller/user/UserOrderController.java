package com.sky.controller.user;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.vo.OrderPaymentVO;
import com.sky.service.WeChatPaymentService;
import com.sky.result.Result;
import com.sky.service.OrderService;
import com.sky.vo.OrderSubmitVO;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/user/order")
public class UserOrderController {
    private final OrderService service;
    private final WeChatPaymentService paymentService;
    public UserOrderController(OrderService service, WeChatPaymentService paymentService) { this.service = service; this.paymentService = paymentService; }
    @PutMapping("/payment") public Result<OrderPaymentVO> payment(@RequestBody OrdersPaymentDTO dto) { return Result.success(paymentService.payment(dto)); }
    @PostMapping("/submit") public Result<OrderSubmitVO> submit(@RequestBody OrdersSubmitDTO dto) { return Result.success(service.submitOrder(dto)); }
}
