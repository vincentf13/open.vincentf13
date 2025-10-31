package open.vincentf13.common.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class OpenMapstruct {

    private final ObjectMapper objectMapper;

    /**
     * 將來源物件轉換為指定類型；來源若為 null 則回傳 null。
     */
    public <S, T> T map(S source, Class<T> targetType) {
        if (source == null) {
            return null;
        }
        return objectMapper.convertValue(source, targetType);
    }

    /**
     * 將來源清單轉換為指定類型清單；若來源為 null 則回傳空集合。
     */
    public <S, T> List<T> mapList(List<S> source, Class<T> targetType) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        return source.stream()
                .map(item -> map(item, targetType))
                .filter(Objects::nonNull)
                .toList();
    }
}
