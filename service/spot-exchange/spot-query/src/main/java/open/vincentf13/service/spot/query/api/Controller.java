package open.vincentf13.service.spot.query.api;

import open.vincentf13.service.spot.model.ActiveOrder;
import open.vincentf13.service.spot.model.Balance;
import org.springframework.web.bind.annotation.*;

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
    public List<ActiveOrder> getOrders(@RequestParam long userId) {
        return service.getOrders(userId);
    }
}
