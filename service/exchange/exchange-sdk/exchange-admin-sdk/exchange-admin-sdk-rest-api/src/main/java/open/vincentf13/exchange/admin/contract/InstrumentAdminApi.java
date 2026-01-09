package open.vincentf13.exchange.admin.contract;

import jakarta.validation.constraints.NotNull;
import open.vincentf13.exchange.admin.contract.dto.InstrumentDetailResponse;
import open.vincentf13.exchange.admin.contract.dto.InstrumentSummaryResponse;
import open.vincentf13.exchange.admin.contract.enums.InstrumentStatus;
import open.vincentf13.exchange.admin.contract.enums.InstrumentType;
import open.vincentf13.sdk.auth.auth.PublicAPI;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Validated
public interface InstrumentAdminApi {
    
    @GetMapping
    @PublicAPI
    OpenApiResponse<List<InstrumentSummaryResponse>> list(
            @RequestParam(value = "status", required = false) InstrumentStatus status,
            @RequestParam(value = "instrumentType", required = false) InstrumentType instrumentType
                                                         );
    
    @GetMapping("/{instrumentId}")
    @PublicAPI
    OpenApiResponse<InstrumentDetailResponse> get(
            @PathVariable("instrumentId") @NotNull Long instrumentId
                                                 );
}
