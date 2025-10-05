//package open.vincentf13.common.core.mapstruct;
//
//import org.mapstruct.Mapper;
//import org.mapstruct.TargetType;
//import org.springframework.stereotype.Service;
//
//import java.util.List;
//
//@Service
//@Mapper(componentModel = "spring")
//public interface CommonMapper {
//
//    /*
//     * Usage example:
//     * @Autowired CommonMapper mapper;
//     *
//     * UserDto dto = mapper.map(user, UserDto.class);
//     * List<UserDto> list = mapper.mapList(users, UserDto.class);
//     */
//    <S, T> T map(S source, @TargetType Class<T> targetType);
//
//    <S, T> List<T> mapList(List<S> source, @TargetType Class<T> targetType);
//}
