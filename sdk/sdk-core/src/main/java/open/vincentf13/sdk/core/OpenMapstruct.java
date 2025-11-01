package open.vincentf13.sdk.core;

import java.util.List;
import java.util.Objects;

public final class OpenMapstruct {

    private OpenMapstruct() {
    }

    /**
     * 將來源物件轉換為指定類型；來源若為 null 則回傳 null。
     */
    public static <S, T> T map(S source, Class<T> targetType) {
        if (source == null) {
            return null;
        }
        return OpenObjectMapper.convert(source, targetType);
    }

    /**
     * 將來源清單轉換為指定類型清單；若來源為 null 則回傳空集合。
     */
    public static <S, T> List<T> mapList(List<S> source, Class<T> targetType) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        return source.stream()
                .map(item -> map(item, targetType))
                .filter(Objects::nonNull)
                .toList();
    }
}
