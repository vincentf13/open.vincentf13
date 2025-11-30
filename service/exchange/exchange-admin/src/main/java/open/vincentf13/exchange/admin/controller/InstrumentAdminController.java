package open.vincentf13.exchange.admin.controller;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.admin.contract.InstrumentAdminApi;
import open.vincentf13.exchange.admin.contract.dto.InstrumentDetailResponse;
import open.vincentf13.exchange.admin.contract.dto.InstrumentSummaryResponse;
import open.vincentf13.exchange.admin.contract.enums.InstrumentStatus;
import open.vincentf13.exchange.admin.contract.enums.InstrumentType;
import open.vincentf13.exchange.admin.service.InstrumentQueryService;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/instruments")
public class InstrumentAdminController implements InstrumentAdminApi {
    
    private final InstrumentQueryService instrumentQueryService;
    
    @Override
    public OpenApiResponse<List<InstrumentSummaryResponse>> list(InstrumentStatus status,
                                                                 InstrumentType instrumentType) {
        return OpenApiResponse.success(instrumentQueryService.list(status, instrumentType));
    }
    
    @Override
    public OpenApiResponse<InstrumentDetailResponse> get(Long instrumentId) {
        return OpenApiResponse.success(instrumentQueryService.get(instrumentId));
    }
}
