package open.vincentf13.service.spot.query.api;

import open.vincentf13.service.spot.model.Balance;
import open.vincentf13.service.spot.model.Order;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/spot")
public class Controller {
    private final DataService service;
    
    public Controller(DataService service) {
        this.service = service;
    }
    
    @GetMapping("/balances")
    public List<Balance> getBalances(@RequestParam long userId) {
        return service.getBalances(userId);
    }
    
    @GetMapping("/orders")
    public List<Order> getOrders(@RequestParam long userId) {
        return service.getOrders(userId);
    }
}
